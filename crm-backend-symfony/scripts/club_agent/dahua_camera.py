"""Камера Dahua (IVS): подписка на события tripwire по CGI и подсчёт пересечений линии входа человеком.

Модель DH-IPC-HDW2449TP-S-IL поддерживает IVS «Tripwire» с классификацией Human и отдаёт события
постоянным HTTP-потоком (multipart) через `cgi-bin/eventManager.cgi?action=attach`.
Здесь — отдельный поток-демон с Digest-авторизацией и автоматическим переподключением, который
хранит последние пересечения линии (с меткой времени), чтобы агент мог посчитать, сколько людей
прошло в коротком окне после открытия двери по одному QR (анти-проход вдвоём / tailgating).

Зависимостей нет — только стандартная библиотека (urllib).
"""
from __future__ import annotations

import json
import logging
import socket
import concurrent.futures
import threading
import time
import urllib.error
import urllib.request
from typing import Callable, Optional

LOG = logging.getLogger("camera")

OnLog = Optional[Callable[[str, str], None]]
OnCrossing = Optional[Callable[[dict], None]]

# Сколько секунд держать историю пересечений (для окна подсчёта хватает с запасом).
_RETENTION_SEC = 60.0
_MAX_TEXTBUF = 65536
_IVS_CODES = frozenset({"CrossLineDetection", "CrossRegionDetection"})
_READ_EXECUTOR = concurrent.futures.ThreadPoolExecutor(max_workers=4, thread_name_prefix="dahua-read")


def _is_read_timeout(exc: BaseException) -> bool:
    if isinstance(exc, TimeoutError):
        return True
    if isinstance(exc, OSError):
        msg = str(exc).lower()
        return "timed out" in msg or "timeout" in msg
    return False


def _response_socket(resp) -> Optional[socket.socket]:
    fp = getattr(resp, "fp", None)
    if fp is None:
        return None
    for obj in (fp, getattr(fp, "raw", None)):
        if obj is None:
            continue
        sock = getattr(obj, "_sock", None)
        if isinstance(sock, socket.socket):
            return sock
    return None


def _read_chunk(resp, sock: Optional[socket.socket], timeout_sec: float) -> Optional[bytes]:
    """Читать chunk; None = таймаут (данных пока нет), b'' = поток закрыт."""
    timeout_sec = max(0.1, float(timeout_sec))
    if sock is not None:
        old_timeout = sock.gettimeout()
        try:
            sock.settimeout(timeout_sec)
            data = sock.recv(8192)
        except socket.timeout:
            return None
        except OSError as e:
            if _is_read_timeout(e):
                return None
            raise
        finally:
            try:
                sock.settimeout(old_timeout)
            except OSError:
                pass
        return data if data else b""

    future = _READ_EXECUTOR.submit(resp.read, 4096)
    try:
        data = future.result(timeout=timeout_sec)
    except concurrent.futures.TimeoutError:
        return None
    except Exception as e:  # noqa: BLE001
        if _is_read_timeout(e):
            return None
        raise
    return data


def _find_json_end(text: str, start: int) -> int:
    """Индекс символа сразу за парной '}' для объекта, начинающегося в text[start] == '{'.

    Возвращает -1, если JSON ещё не дочитан (буфер неполный).
    Учитывает строки в кавычках и экранирование, чтобы '{'/'}' внутри строк не ломали баланс.
    """
    depth = 0
    in_str = False
    escaped = False
    for i in range(start, len(text)):
        ch = text[i]
        if in_str:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_str = False
            continue
        if ch == '"':
            in_str = True
        elif ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return i + 1
    return -1


def _normalize_header_fields(header: str) -> dict[str, str]:
    fields: dict[str, str] = {}
    for part in header.split(";"):
        if "=" not in part:
            continue
        k, _, v = part.partition("=")
        key = k.strip()
        if not key:
            continue
        fields[key.lower()] = v.strip()
    return fields


def _extract_object_type(data: dict) -> str:
    """ObjectType в Dahua может быть в Object.ObjectType или в корне data."""
    if not isinstance(data, dict):
        return ""
    obj = data.get("Object")
    if isinstance(obj, dict):
        for key in ("ObjectType", "ObjectType2", "Type"):
            val = obj.get(key)
            if val:
                return str(val).strip()
    for key in ("ObjectType", "objectType", "VehicleType"):
        val = data.get(key)
        if val:
            return str(val).strip()
    return ""


def _is_human_type(obj_type: str) -> bool:
    return obj_type.strip().lower() in ("human", "person", "pedestrian", "people")


class DahuaCameraListener:
    """Постоянное соединение с камерой и подсчёт пересечений tripwire человеком."""

    def __init__(
        self,
        *,
        host: str,
        username: str,
        password: str,
        channel: int = 1,
        event_codes: str = "CrossLineDetection",
        inbound_direction: str = "",
        require_human: bool = True,
        https: bool = False,
        verify_ssl: bool = False,
        on_log: OnLog = None,
        on_crossing: OnCrossing = None,
        reconnect_sec: float = 5.0,
        open_timeout: float = 15.0,
    ) -> None:
        self.host = (host or "").strip()
        self.username = username or ""
        self.password = password or ""
        self.channel = int(channel) if channel else 1
        self.event_codes = (event_codes or "CrossLineDetection").strip()
        self.inbound_direction = (inbound_direction or "").strip()
        self.require_human = bool(require_human)
        self.https = bool(https)
        self.verify_ssl = bool(verify_ssl)
        self.on_log = on_log
        self.on_crossing = on_crossing
        self.reconnect_sec = max(1.0, float(reconnect_sec))
        self.open_timeout = max(3.0, float(open_timeout))

        self._stop = threading.Event()
        self._thread: Optional[threading.Thread] = None
        self._connected = False
        self._lock = threading.Lock()
        self._crossings: list[dict] = []
        self._textbuf = ""
        self._total_crossings = 0

    # ── публичный API ─────────────────────────────────────────────
    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._run, name="dahua-camera", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        self._connected = False

    @property
    def connected(self) -> bool:
        return self._connected

    @property
    def total_crossings(self) -> int:
        return self._total_crossings

    def crossings_between(self, start_mono: float, end_mono: float) -> list[dict]:
        """Пересечения линии с monotonic-меткой в [start_mono; end_mono] (без служебного поля mono)."""
        out: list[dict] = []
        with self._lock:
            for c in self._crossings:
                if start_mono <= c["mono"] <= end_mono:
                    out.append({k: v for k, v in c.items() if k != "mono"})
        return out

    def probe_connectivity(self) -> tuple[bool, str]:
        """Проверка камеры без запуска фонового потока: auth + доступ к attach endpoint."""
        if not self.host:
            return False, "Камера: не указан host"
        try:
            opener = self._build_opener()
            with opener.open(self._attach_url(), timeout=self.open_timeout):
                return True, "Камера: подключение и авторизация OK"
        except urllib.error.HTTPError as e:
            if e.code == 401:
                return False, "Камера: HTTP 401 (проверьте логин/пароль Digest)"
            return False, f"Камера: HTTP {e.code}"
        except Exception as e:  # noqa: BLE001 — сетевые ошибки/таймаут
            return False, f"Камера: {e}"

    def probe_event_stream(self, duration_sec: float = 25.0) -> dict:
        """Слушает сырой HTTP-поток attach и возвращает статистику (для диагностики).

        Важно: у Dahua обычно одно attach-подключение — перед вызовом остановите агент.
        """
        duration_sec = max(5.0, float(duration_sec))
        if not self.host:
            return {"error": "host пустой", "summary": "host пустой"}

        urls = self._attach_urls_for_probe()
        combined: dict = {
            "duration_sec": duration_sec,
            "bytes": 0,
            "chunks": 0,
            "heartbeats": 0,
            "code_events": 0,
            "cross_line_events": 0,
            "samples": [],
            "error": "",
            "http_code": 0,
            "urls_tried": [],
        }
        self._emit("info", f"диагностика потока: слушаем {duration_sec:.0f} с — пройдите через линию IVS")

        per_url = max(8.0, duration_sec / len(urls))
        for url in urls:
            listen_for = duration_sec if not combined["urls_tried"] else per_url
            stats = self._probe_one_url(url, listen_for)
            combined["urls_tried"].append(
                {
                    "url": url,
                    "bytes": stats.get("bytes", 0),
                    "code_events": stats.get("code_events", 0),
                    "cross_line": stats.get("cross_line_events", 0),
                }
            )
            for key in ("bytes", "chunks", "heartbeats", "code_events", "cross_line_events"):
                combined[key] = int(combined.get(key, 0)) + int(stats.get(key, 0))
            combined["samples"].extend(stats.get("samples") or [])
            if stats.get("error"):
                combined["error"] = stats["error"]
            if stats.get("http_code"):
                combined["http_code"] = stats["http_code"]
            if stats.get("cross_line_events", 0) > 0 or stats.get("code_events", 0) > 0:
                break
            if stats.get("bytes", 0) > 0:
                break
            if stats.get("http_code") == 401:
                break

        combined["samples"] = combined["samples"][:8]
        self._emit_probe_summary(combined)
        return combined

    def _attach_urls_for_probe(self) -> list[str]:
        codes = "%2C".join(p.strip() for p in self.event_codes.split(",") if p.strip())
        base = (
            f"{self._base()}/cgi-bin/eventManager.cgi"
            f"?action=attach&codes=[{codes}]&heartbeat=5"
        )
        urls: list[str] = []
        for ch in (self.channel, 0 if self.channel != 0 else 1):
            urls.append(f"{base}&channel={ch}")
        urls.append(base)
        return list(dict.fromkeys(urls))

    def _probe_one_url(self, url: str, duration_sec: float) -> dict:
        stats: dict = {
            "url": url,
            "duration_sec": duration_sec,
            "bytes": 0,
            "chunks": 0,
            "heartbeats": 0,
            "code_events": 0,
            "cross_line_events": 0,
            "samples": [],
            "error": "",
            "http_code": 0,
        }
        self._emit("info", f"диагностика потока: {url}")
        buf = ""
        deadline = time.monotonic() + duration_sec
        next_progress = time.monotonic() + 5.0
        resp = None
        try:
            opener = self._build_opener()
            resp = opener.open(url, timeout=self.open_timeout)
            ct = resp.headers.get("Content-Type", "")
            self._emit("info", f"диагностика потока: Content-Type={ct or '—'}")
            sock = _response_socket(resp)
            while time.monotonic() < deadline:
                if time.monotonic() >= next_progress:
                    self._emit(
                        "info",
                        f"диагностика потока: … {stats['bytes']} байт, heartbeat={stats['heartbeats']}, "
                        f"Code={stats['code_events']}, CrossLine={stats['cross_line_events']}",
                    )
                    next_progress += 5.0
                wait = min(5.0, max(0.2, deadline - time.monotonic()))
                chunk = _read_chunk(resp, sock, wait)
                if chunk is None:
                    continue
                if chunk == b"":
                    self._emit("warning", "диагностика потока: камера закрыла поток (пустой chunk)")
                    break
                stats["bytes"] += len(chunk)
                stats["chunks"] += 1
                text = chunk.decode("utf-8", errors="replace")
                if stats["chunks"] <= 3:
                    preview = text.replace("\r", "\\r").replace("\n", "\\n ")[:300]
                    self._emit("info", f"диагностика потока chunk#{stats['chunks']}: {preview}")
                buf += text
                buf = self._consume_probe_events(buf, stats)
                if len(buf) > 131072:
                    buf = buf[-65536:]
                hb = text.count("Heartbeat")
                if hb:
                    stats["heartbeats"] += hb
        except urllib.error.HTTPError as e:
            stats["http_code"] = int(e.code)
            if e.code == 401:
                stats["error"] = "HTTP 401 Unauthorized"
                self._emit(
                    "warning",
                    "диагностика потока: HTTP 401 — неверный логин или пароль камеры. "
                    "Проверьте поля на вкладке «Камера» и нажмите «Сохранить всё».",
                )
            else:
                stats["error"] = f"HTTP {e.code}"
                self._emit("warning", f"диагностика потока: HTTP {e.code}")
        except Exception as e:  # noqa: BLE001
            stats["error"] = str(e)
            self._emit("warning", f"диагностика потока: ошибка — {e}")
        finally:
            if resp is not None:
                try:
                    resp.close()
                except Exception:  # noqa: BLE001
                    pass
        return stats

    def _consume_probe_events(self, buf: str, stats: dict) -> str:
        while "Code=" in buf:
            idx = buf.find("Code=")
            end = buf.find("Code=", idx + 5)
            nl = buf.find("\n", idx)
            if end < 0 and nl < 0:
                break
            if end < 0:
                seg_end = nl + 1 if nl >= 0 else len(buf)
            elif nl >= 0 and nl < end:
                seg_end = nl + 1
            else:
                seg_end = end
            line = buf[idx:seg_end].strip()
            if not line:
                break
            buf = buf[seg_end:]
            stats["code_events"] += 1
            low = line.lower()
            if "crosslinedetection" in low:
                stats["cross_line_events"] += 1
            preview = line[:240]
            if len(stats["samples"]) < 8:
                stats["samples"].append(preview)
            self._emit("info", f"диагностика потока RAW: {preview}")
        return buf

    def _emit_probe_summary(self, stats: dict) -> None:
        summary = (
            f"байт={stats.get('bytes', 0)}, chunks={stats.get('chunks', 0)}, "
            f"heartbeat={stats.get('heartbeats', 0)}, Code=={stats.get('code_events', 0)}, "
            f"CrossLine={stats.get('cross_line_events', 0)}"
        )
        if stats.get("cross_line_events", 0):
            self._emit("info", f"диагностика потока: СОБЫТИЯ ПЕРЕСЕЧЕНИЯ ПРИХОДЯТ ({summary})")
        elif stats.get("code_events", 0):
            self._emit("warning", f"диагностика потока: события есть, но CrossLineDetection нет ({summary})")
        elif stats.get("heartbeats", 0) or stats.get("bytes", 0):
            self._emit(
                "warning",
                f"диагностика потока: поток живой, но Code= не пришло ({summary}). "
                "Пройдите через линию или проверьте расписание/Smart Plan IVS.",
            )
        else:
            self._emit(
                "warning",
                f"диагностика потока: данных нет ({summary}). "
                + (
                    "Ошибка авторизации — проверьте логин/пароль камеры."
                    if stats.get("http_code") == 401
                    else "Проверьте канал (0/1), остановлен ли агент, нет ли второго слушателя."
                ),
            )
        stats["summary"] = summary

    # ── внутреннее ────────────────────────────────────────────────
    def _emit(self, level: str, msg: str) -> None:
        if self.on_log:
            self.on_log(level, f"[камера] {msg}")

    def _set_connected(self, value: bool) -> None:
        self._connected = value

    def _base(self) -> str:
        scheme = "https" if self.https else "http"
        return f"{scheme}://{self.host}"

    def _attach_url(self) -> str:
        codes = "%2C".join(p.strip() for p in self.event_codes.split(",") if p.strip())
        return (
            f"{self._base()}/cgi-bin/eventManager.cgi"
            f"?action=attach&codes=[{codes}]&heartbeat=5&channel={self.channel}"
        )

    def _build_opener(self) -> urllib.request.OpenerDirector:
        pwd_mgr = urllib.request.HTTPPasswordMgrWithDefaultRealm()
        base = self._base()
        user = self.username
        pwd = self.password
        for uri in (base, f"{base}/", f"{base}/cgi-bin/", f"{base}/cgi-bin/eventManager.cgi"):
            pwd_mgr.add_password(None, uri, user, pwd)
        handlers: list = [
            urllib.request.HTTPDigestAuthHandler(pwd_mgr),
            urllib.request.HTTPBasicAuthHandler(pwd_mgr),
        ]
        if self.https and not self.verify_ssl:
            import ssl

            ctx = ssl.create_default_context()
            ctx.check_hostname = False
            ctx.verify_mode = ssl.CERT_NONE
            handlers.append(urllib.request.HTTPSHandler(context=ctx))
        return urllib.request.build_opener(*handlers)

    def _run(self) -> None:
        while not self._stop.is_set():
            try:
                self._connect_and_read()
            except urllib.error.HTTPError as e:
                self._set_connected(False)
                hint = ""
                if e.code == 401:
                    hint = " — проверьте логин/пароль камеры (Digest)"
                self._emit("warning", f"HTTP {e.code}{hint}")
            except Exception as e:  # noqa: BLE001 — сеть/парсинг, переподключаемся
                self._set_connected(False)
                self._emit("warning", f"разрыв соединения: {e}")
            if self._stop.wait(self.reconnect_sec):
                break

    def _connect_and_read(self) -> None:
        opener = self._build_opener()
        url = self._attach_url()
        self._emit("info", f"подключение {url}")
        self._textbuf = ""
        with opener.open(url, timeout=self.open_timeout) as resp:
            self._set_connected(True)
            self._emit("info", "подписка на события активна")
            sock = _response_socket(resp)
            while not self._stop.is_set():
                chunk = _read_chunk(resp, sock, 12.0)
                if chunk is None:
                    continue
                if not chunk:
                    break
                self._textbuf += chunk.decode("utf-8", errors="replace")
                self._consume_buffer()
        self._set_connected(False)

    def _has_partial_event(self) -> bool:
        idx = self._textbuf.rfind("Code=")
        if idx < 0:
            return False
        tail = self._textbuf[idx:]
        data_pos = tail.find("data=")
        if data_pos < 0:
            return True
        brace = tail.find("{", data_pos)
        if brace < 0:
            return True
        return _find_json_end(tail, brace) < 0

    def _trim_buffer_if_needed(self) -> None:
        if len(self._textbuf) <= _MAX_TEXTBUF or self._has_partial_event():
            return
        idx = self._textbuf.rfind("Code=")
        if idx > 0:
            self._textbuf = self._textbuf[idx:]
        if len(self._textbuf) > _MAX_TEXTBUF:
            self._textbuf = self._textbuf[-8192:]

    def _consume_buffer(self) -> None:
        """Вырезать из буфера все полные события `Code=...;action=...;[data={...}]`."""
        self._textbuf = self._textbuf.replace("\r\n", "\n").replace("\r", "\n")
        while True:
            idx = self._textbuf.find("Code=")
            if idx < 0:
                self._trim_buffer_if_needed()
                return
            if idx > 0:
                self._textbuf = self._textbuf[idx:]
                idx = 0
            data_pos = self._textbuf.find("data=", idx)
            next_code = self._textbuf.find("Code=", idx + 5)
            if data_pos >= 0 and (next_code < 0 or data_pos < next_code):
                brace = self._textbuf.find("{", data_pos)
                if brace < 0:
                    return
                end = _find_json_end(self._textbuf, brace)
                if end < 0:
                    return
                header = self._textbuf[idx:data_pos]
                payload = self._textbuf[brace:end]
                self._textbuf = self._textbuf[end:]
                self._handle_event(header, payload)
                continue
            if next_code >= 0:
                header = self._textbuf[idx:next_code]
                self._textbuf = self._textbuf[next_code:]
                self._handle_event(header, None)
                continue
            nl = self._textbuf.find("\n", idx)
            if nl < 0:
                return
            header = self._textbuf[idx : nl + 1]
            self._textbuf = self._textbuf[nl + 1 :]
            self._handle_event(header, None)

    def _handle_event(self, header: str, payload: Optional[str]) -> None:
        fields = _normalize_header_fields(header)
        code = fields.get("code", "")
        action = fields.get("action", "")
        if code not in _IVS_CODES:
            return
        # Tripwire: Start/Pulse — начало; Stop — на части прошивок Dahua приходит с полным JSON.
        if action and action.lower() not in ("start", "pulse", "stop"):
            return

        data: dict = {}
        if payload:
            try:
                parsed = json.loads(payload)
                if isinstance(parsed, dict):
                    data = parsed
            except json.JSONDecodeError:
                self._emit("warning", f"IVS {code}: не разобрать JSON data: {payload[:160]!r}")
                return

        obj_type = _extract_object_type(data)
        direction = str(data.get("Direction", "") or data.get("direction", "")).strip()

        self._emit(
            "info",
            f"IVS событие: {code} action={action or '—'} dir={direction or '—'} type={obj_type or '—'}",
        )

        if self.require_human:
            if not obj_type:
                self._emit(
                    "warning",
                    "пересечение не засчитано: ObjectType пустой (снимите «Только Human» или включите AI-фильтр «Человек» в камере)",
                )
                return
            if not _is_human_type(obj_type):
                self._emit("info", f"пересечение пропущено: ObjectType={obj_type!r} (не Human)")
                return

        if self.inbound_direction and direction and direction.lower() != self.inbound_direction.lower():
            self._emit(
                "info",
                f"пересечение пропущено: Direction={direction!r}, ожидается {self.inbound_direction!r}",
            )
            return

        record = {
            "mono": time.monotonic(),
            "ts_ms": int(time.time() * 1000),
            "direction": direction,
            "object": obj_type or ("human" if self.require_human else ""),
            "code": code,
        }
        with self._lock:
            self._crossings.append(record)
            self._total_crossings += 1
            cutoff = record["mono"] - _RETENTION_SEC
            self._crossings = [c for c in self._crossings if c["mono"] >= cutoff]
        self._emit("info", f"пересечение линии засчитано: {obj_type or 'object'} dir={direction or '—'}")
        if self.on_crossing:
            try:
                self.on_crossing({k: v for k, v in record.items() if k != "mono"})
            except Exception:  # noqa: BLE001 — колбэк GUI не должен ронять поток
                pass
