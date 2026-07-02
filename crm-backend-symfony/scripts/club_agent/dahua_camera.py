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
        pwd_mgr.add_password(None, self._base(), self.username, self.password)
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
            while not self._stop.is_set():
                chunk = resp.read(2048)
                if not chunk:
                    break
                self._textbuf += chunk.decode("latin-1", errors="replace")
                self._consume_buffer()
        self._set_connected(False)

    def _consume_buffer(self) -> None:
        """Вырезать из буфера все полные события `Code=...;action=...;[data={...}]`."""
        while True:
            idx = self._textbuf.find("Code=")
            if idx < 0:
                # хвост без событий не копим бесконечно
                if len(self._textbuf) > 4096:
                    self._textbuf = self._textbuf[-8:]
                return
            data_pos = self._textbuf.find("data=", idx)
            next_code = self._textbuf.find("Code=", idx + 5)
            # Граница события — следующий Code= (если уже есть в буфере).
            if data_pos >= 0 and (next_code < 0 or data_pos < next_code):
                brace = self._textbuf.find("{", data_pos)
                if brace < 0:
                    # data= есть, но '{' ещё не пришёл — ждём данные, если впереди нет нового события
                    if next_code < 0:
                        return
                    header = self._textbuf[idx:next_code]
                    self._textbuf = self._textbuf[next_code:]
                    self._handle_event(header, None)
                    continue
                end = _find_json_end(self._textbuf, brace)
                if end < 0:
                    return  # JSON ещё не дочитан
                header = self._textbuf[idx:data_pos]
                payload = self._textbuf[brace:end]
                self._textbuf = self._textbuf[end:]
                self._handle_event(header, payload)
                continue
            # Событие без data=: завершаем по следующему Code= или по концу строки
            if next_code >= 0:
                header = self._textbuf[idx:next_code]
                self._textbuf = self._textbuf[next_code:]
                self._handle_event(header, None)
                continue
            nl = self._textbuf.find("\n", idx)
            if nl < 0:
                return  # строка ещё не дочитана
            header = self._textbuf[idx : nl + 1]
            self._textbuf = self._textbuf[nl + 1 :]
            self._handle_event(header, None)

    def _handle_event(self, header: str, payload: Optional[str]) -> None:
        fields = {}
        for part in header.split(";"):
            if "=" in part:
                k, _, v = part.partition("=")
                fields[k.strip()] = v.strip()
        code = fields.get("Code", "")
        action = fields.get("action", "")
        if code != "CrossLineDetection":
            return
        # У tripwire действие пересечения — Start (есть ещё Stop/Pulse, их не считаем).
        if action and action.lower() not in ("start", "pulse"):
            return

        data = {}
        if payload:
            try:
                data = json.loads(payload)
            except json.JSONDecodeError:
                data = {}

        obj = data.get("Object") if isinstance(data, dict) else None
        obj_type = ""
        if isinstance(obj, dict):
            obj_type = str(obj.get("ObjectType", "")).strip()
        if self.require_human:
            if not obj_type:
                self._emit("debug", "событие отброшено: ObjectType пустой при режиме only Human")
                return
            if obj_type.lower() != "human":
                self._emit("debug", f"событие отброшено: ObjectType={obj_type!r}, ожидается 'Human'")
                return

        direction = str(data.get("Direction", "")).strip() if isinstance(data, dict) else ""
        if self.inbound_direction and direction and direction.lower() != self.inbound_direction.lower():
            self._emit(
                "debug",
                f"событие отброшено: Direction={direction!r}, ожидается {self.inbound_direction!r}",
            )
            return

        record = {
            "mono": time.monotonic(),
            "ts_ms": int(time.time() * 1000),
            "direction": direction,
            "object": obj_type or ("human" if self.require_human else ""),
        }
        with self._lock:
            self._crossings.append(record)
            self._total_crossings += 1
            cutoff = record["mono"] - _RETENTION_SEC
            self._crossings = [c for c in self._crossings if c["mono"] >= cutoff]
        self._emit("debug", f"пересечение линии: human dir={direction or '—'}")
        if self.on_crossing:
            try:
                self.on_crossing({k: v for k, v in record.items() if k != "mono"})
            except Exception:  # noqa: BLE001 — колбэк GUI не должен ронять поток
                pass
