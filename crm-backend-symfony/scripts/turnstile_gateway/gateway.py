#!/usr/bin/env python3
"""
ПК-шлюз для клуба франшизы.

Что делает:
  1. Авторизуется в облачном CRM по `gateway_token` (Authorization: Bearer …).
  2. Принимает QR от считывателя в режиме клавиатуры (stdin) и шлёт в CRM
     POST /api/v1/gateway/access/entry. Если CRM разрешил вход и в ответе
     есть `open_device` — открывает турникет в локальном PERCo-Web.
  3. Долго опрашивает GET /api/v1/gateway/commands (long-poll, до 30 c) и
     выполняет команды от админки CRM (open_door / perco_call / ping).
  4. Шлёт heartbeat POST /api/v1/gateway/heartbeat каждые 30 секунд.

Конфигурация — `config.ini` рядом со скриптом, или переменные окружения
(см. config.example.ini).

Запуск:
  # Демон (рекомендуется как сервис):
  python3 gateway.py daemon

  # Однократно отправить QR (например, из скрипта считывателя):
  python3 gateway.py qr 'FITNESSCLUB:ENTRY:user-1:1700000000000'

  # Самопроверка авторизации в CRM и в PERCo:
  python3 gateway.py healthcheck

  # Открыть турникет один раз (тест локального PERCo, без CRM):
  python3 gateway.py open-now
"""
from __future__ import annotations

import argparse
import configparser
import json
import logging
import os
import signal
import ssl
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Optional

from perco_client import PercoApiError, PercoClient

LOG = logging.getLogger("gateway")


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

@dataclass
class GatewayConfig:
    crm_base_url: str
    gateway_token: str
    perco_base_url: Optional[str] = None
    perco_login: Optional[str] = None
    perco_password: Optional[str] = None
    perco_entry_device_id: Optional[int] = None
    perco_verify_ssl: bool = True
    crm_verify_ssl: bool = True
    heartbeat_interval: float = 30.0
    poll_timeout_seconds: float = 35.0
    long_poll_backoff_seconds: float = 5.0
    perco_events_enabled: bool = True
    perco_events_poll_interval: float = 1.0
    perco_events_page_size: int = 50
    perco_only_fitnessclub_qr: bool = True
    cmd_defaults: dict[str, int] = field(default_factory=lambda: {
        "cmd_number": 1, "cmd_type": 1, "cmd_value": 1, "cmd_param": 0,
    })
    device_id_label: str = "club-gateway"

    @classmethod
    def load(cls, path: Optional[Path] = None) -> "GatewayConfig":
        cfg = configparser.ConfigParser()
        ini = path or Path(__file__).with_name("config.ini")
        if ini.exists():
            cfg.read(ini, encoding="utf-8")

        def get(section: str, key: str, default: Optional[str] = None) -> Optional[str]:
            envname = f"{section.upper()}_{key.upper()}"
            v = os.environ.get(envname)
            if v is not None and v.strip() != "":
                return v.strip()
            if cfg.has_option(section, key):
                v = cfg.get(section, key).strip()
                if v != "":
                    return v
            return default

        crm_url = get("crm", "base_url")
        token = get("crm", "gateway_token")
        if not crm_url or not token:
            raise SystemExit(
                "Не задан CRM base_url / gateway_token. Создайте config.ini "
                "(см. config.example.ini) или задайте переменные окружения "
                "CRM_BASE_URL / CRM_GATEWAY_TOKEN."
            )

        def _bool(v: Optional[str], default: bool = True) -> bool:
            if v is None:
                return default
            return v.lower() not in ("0", "false", "no", "off", "")

        def _int(v: Optional[str]) -> Optional[int]:
            if v is None or str(v).strip() == "":
                return None
            return int(v)

        return cls(
            crm_base_url=crm_url.rstrip("/"),
            gateway_token=token,
            perco_base_url=(get("perco", "base_url") or "").rstrip("/") or None,
            perco_login=get("perco", "login"),
            perco_password=get("perco", "password"),
            perco_entry_device_id=_int(get("perco", "entry_device_id")),
            perco_verify_ssl=_bool(get("perco", "verify_ssl"), True),
            crm_verify_ssl=_bool(get("crm", "verify_ssl"), True),
            heartbeat_interval=float(get("crm", "heartbeat_interval") or "30"),
            poll_timeout_seconds=float(get("crm", "poll_timeout_seconds") or "35"),
            long_poll_backoff_seconds=float(get("crm", "long_poll_backoff_seconds") or "5"),
            perco_events_enabled=_bool(get("perco", "events_enabled"), True),
            perco_events_poll_interval=float(get("perco", "events_poll_interval") or "1"),
            perco_events_page_size=max(1, int(get("perco", "events_page_size") or "50")),
            perco_only_fitnessclub_qr=_bool(get("perco", "only_fitnessclub_qr"), True),
            cmd_defaults={
                "cmd_number": _int(get("perco", "cmd_number")) or 1,
                "cmd_type": _int(get("perco", "cmd_type")) or 1,
                "cmd_value": _int(get("perco", "cmd_value")) or 1,
                "cmd_param": _int(get("perco", "cmd_param")) or 0,
            },
            device_id_label=get("crm", "device_id") or "club-gateway",
        )

    def perco_configured(self) -> bool:
        return bool(self.perco_base_url and self.perco_login and self.perco_password)


# ---------------------------------------------------------------------------
# CRM client (talks to /api/v1/gateway/*)
# ---------------------------------------------------------------------------

class CrmClient:
    def __init__(self, cfg: GatewayConfig) -> None:
        self.cfg = cfg

    def _ssl_ctx(self) -> Optional[ssl.SSLContext]:
        if not self.cfg.crm_base_url.lower().startswith("https") or self.cfg.crm_verify_ssl:
            return None
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        return ctx

    def _request(self, method: str, path: str, *, json_body: Any = None, timeout: float = 30.0) -> tuple[int, Any]:
        url = self.cfg.crm_base_url + path
        body = json.dumps(json_body, ensure_ascii=False).encode("utf-8") if json_body is not None else None
        headers = {
            "Accept": "application/json",
            "Authorization": f"Bearer {self.cfg.gateway_token}",
            "User-Agent": "fc-gateway/1.0",
        }
        if body is not None:
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(url, data=body, headers=headers, method=method.upper())
        try:
            with urllib.request.urlopen(req, timeout=timeout, context=self._ssl_ctx()) as r:
                raw = r.read().decode("utf-8", errors="replace")
                return r.status, (json.loads(raw) if raw.strip() else {})
        except urllib.error.HTTPError as e:
            raw = e.read().decode("utf-8", errors="replace")
            try:
                return e.code, (json.loads(raw) if raw.strip() else {})
            except json.JSONDecodeError:
                return e.code, {"raw": raw}

    def submit_qr(self, qr: str, device_id: Optional[str] = None) -> tuple[int, dict]:
        body = {"qr": qr, "device_id": device_id or self.cfg.device_id_label}
        return self._request("POST", "/api/v1/gateway/access/entry", json_body=body)

    def heartbeat(self, status_payload: Optional[dict] = None) -> tuple[int, dict]:
        return self._request(
            "POST",
            "/api/v1/gateway/heartbeat",
            json_body=status_payload or {},
            timeout=15.0,
        )

    def poll_commands(self) -> tuple[int, dict]:
        return self._request(
            "GET",
            "/api/v1/gateway/commands",
            timeout=self.cfg.poll_timeout_seconds,
        )

    def ack_command(self, command_id: int, status: str = "done", result: Optional[dict] = None) -> tuple[int, dict]:
        return self._request(
            "POST",
            f"/api/v1/gateway/commands/{int(command_id)}/ack",
            json_body={"status": status, "result": result or {}},
            timeout=15.0,
        )


# ---------------------------------------------------------------------------
# PERCo helper
# ---------------------------------------------------------------------------

def _build_perco(cfg: GatewayConfig) -> Optional[PercoClient]:
    if not cfg.perco_configured():
        return None
    return PercoClient(
        base_url=cfg.perco_base_url or "",
        login=cfg.perco_login or "",
        password=cfg.perco_password or "",
        verify_ssl=cfg.perco_verify_ssl,
    )


def _perco_open(cfg: GatewayConfig, perco: PercoClient, override: Optional[dict] = None) -> dict:
    payload = override or {}
    device_id = int(payload.get("device_id") or cfg.perco_entry_device_id or 0)
    if device_id <= 0:
        raise RuntimeError("Не задан perco_entry_device_id ни в config.ini, ни в команде CRM")
    cmd = {
        "cmd_number": int(payload.get("cmd_number") or cfg.cmd_defaults["cmd_number"]),
        "cmd_type": int(payload.get("cmd_type") or cfg.cmd_defaults["cmd_type"]),
        "cmd_value": int(payload.get("cmd_value") or cfg.cmd_defaults["cmd_value"]),
        "cmd_param": int(payload.get("cmd_param") or cfg.cmd_defaults["cmd_param"]),
    }
    perco.device_command(device_id, **cmd)
    LOG.info("PERCo: устройство %s открыто (cmd=%s)", device_id, cmd)
    return {"device_id": device_id, **cmd}


# ---------------------------------------------------------------------------
# Worker threads
# ---------------------------------------------------------------------------

class GatewayDaemon:
    """Главный цикл шлюза: heartbeat + long-poll commands + опционально stdin reader."""

    def __init__(self, cfg: GatewayConfig, *, read_stdin: bool = True) -> None:
        self.cfg = cfg
        self.crm = CrmClient(cfg)
        self.perco = _build_perco(cfg)
        self._stop = threading.Event()
        self._read_stdin = read_stdin
        self._perco_seen_uids: set[str] = set()
        self._perco_last_numeric_id: Optional[int] = None

    def stop(self, *_a: Any) -> None:
        if not self._stop.is_set():
            LOG.info("Получен сигнал остановки — завершаемся…")
            self._stop.set()

    def run(self) -> None:
        signal.signal(signal.SIGINT, self.stop)
        signal.signal(signal.SIGTERM, self.stop)
        threads = [
            threading.Thread(target=self._loop_heartbeat, name="heartbeat", daemon=True),
            threading.Thread(target=self._loop_commands, name="commands", daemon=True),
        ]
        if self.perco is not None and self.cfg.perco_events_enabled:
            threads.append(threading.Thread(target=self._loop_perco_events, name="perco-events", daemon=True))
        if self._read_stdin and sys.stdin and sys.stdin.isatty() is False:
            # Если stdin — пайп (считыватель), запускаем поток чтения строк QR.
            threads.append(threading.Thread(target=self._loop_stdin_qr, name="stdin", daemon=True))

        for t in threads:
            t.start()
        LOG.info("Шлюз запущен. CRM=%s, PERCo=%s", self.cfg.crm_base_url, self.cfg.perco_base_url or "не настроен")

        while not self._stop.is_set():
            time.sleep(0.5)
        LOG.info("Шлюз остановлен.")

    # heartbeat ---------------------------------------------------------------

    def _loop_heartbeat(self) -> None:
        while not self._stop.is_set():
            try:
                code, body = self.crm.heartbeat({
                    "version": "1.0",
                    "perco_status": "configured" if self.cfg.perco_configured() else "missing",
                    "perco_events": "enabled" if (self.perco is not None and self.cfg.perco_events_enabled) else "disabled",
                })
                if code != 200:
                    LOG.warning("heartbeat HTTP %s: %s", code, body)
            except Exception as e:  # сеть может быть нестабильной — это ожидаемо
                LOG.warning("heartbeat error: %s", e)
            self._stop.wait(self.cfg.heartbeat_interval)

    # commands ---------------------------------------------------------------

    def _loop_commands(self) -> None:
        while not self._stop.is_set():
            try:
                code, body = self.crm.poll_commands()
            except urllib.error.URLError as e:
                LOG.warning("poll commands error: %s", e)
                self._stop.wait(self.cfg.long_poll_backoff_seconds)
                continue
            except Exception as e:
                LOG.warning("poll commands unexpected: %s", e)
                self._stop.wait(self.cfg.long_poll_backoff_seconds)
                continue

            if code == 401:
                LOG.error("CRM вернул 401 — проверьте gateway_token. Пауза 60 c.")
                self._stop.wait(60.0)
                continue
            if code != 200:
                LOG.warning("poll HTTP %s: %s", code, body)
                self._stop.wait(self.cfg.long_poll_backoff_seconds)
                continue

            cmds = (body or {}).get("commands") or []
            for cmd in cmds:
                self._execute(cmd)

            if not cmds:
                # Сервер сам выдержал паузу до 25c; короткая страховка от штормов.
                self._stop.wait(0.2)

    # perco events -----------------------------------------------------------

    def _loop_perco_events(self) -> None:
        if self.perco is None:
            return

        LOG.info(
            "PERCo events poller включен: interval=%ss, page_size=%s, only_fitnessclub_qr=%s",
            self.cfg.perco_events_poll_interval,
            self.cfg.perco_events_page_size,
            self.cfg.perco_only_fitnessclub_qr,
        )
        while not self._stop.is_set():
            try:
                params: dict[str, Any] = {"limit": self.cfg.perco_events_page_size}
                if self._perco_last_numeric_id is not None:
                    params["fromId"] = self._perco_last_numeric_id
                payload = self.perco.events_identifications(**params)
                events = self._normalize_perco_events(payload)
                processed = self._consume_perco_events(events)
                if processed == 0:
                    self._stop.wait(self.cfg.perco_events_poll_interval)
            except PercoApiError as e:
                LOG.warning("PERCo events API error: %s", e)
                self._stop.wait(max(2.0, self.cfg.perco_events_poll_interval))
            except Exception as e:
                LOG.warning("PERCo events poll unexpected: %s", e)
                self._stop.wait(max(2.0, self.cfg.perco_events_poll_interval))

    def _normalize_perco_events(self, payload: Any) -> list[dict[str, Any]]:
        if isinstance(payload, list):
            return [x for x in payload if isinstance(x, dict)]
        if not isinstance(payload, dict):
            return []

        for key in ("items", "rows", "events", "data", "result"):
            node = payload.get(key)
            if isinstance(node, list):
                return [x for x in node if isinstance(x, dict)]
        return []

    def _consume_perco_events(self, events: list[dict[str, Any]]) -> int:
        processed = 0
        for event in events:
            uid = self._event_uid(event)
            if uid in self._perco_seen_uids:
                continue

            self._perco_seen_uids.add(uid)
            if len(self._perco_seen_uids) > 5000:
                self._perco_seen_uids = set(list(self._perco_seen_uids)[-2000:])

            ev_id = self._event_numeric_id(event)
            if ev_id is not None and (self._perco_last_numeric_id is None or ev_id > self._perco_last_numeric_id):
                self._perco_last_numeric_id = ev_id

            qr = self._extract_qr_from_perco_event(event)
            if not qr:
                continue
            if self.cfg.perco_only_fitnessclub_qr and not qr.startswith("FITNESSCLUB:"):
                LOG.debug("PERCo event пропущен: идентификатор не FITNESSCLUB (%s)", qr[:48])
                continue

            LOG.info("PERCo event → QR найден: %s", qr[:96])
            self.handle_qr(qr)
            processed += 1
        return processed

    def _event_numeric_id(self, event: dict[str, Any]) -> Optional[int]:
        for key in ("id", "eventId", "event_id"):
            value = event.get(key)
            try:
                if value is not None:
                    return int(value)
            except (ValueError, TypeError):
                continue
        return None

    def _event_uid(self, event: dict[str, Any]) -> str:
        for key in ("id", "eventId", "event_id", "guid", "uuid"):
            value = event.get(key)
            if value is not None and str(value).strip() != "":
                return f"{key}:{value}"

        stamp = event.get("createdAt") or event.get("created_at") or event.get("time") or event.get("timestamp") or ""
        ident = event.get("identifier") or event.get("code") or event.get("card") or ""
        return f"fallback:{stamp}:{ident}:{hash(json.dumps(event, ensure_ascii=False, sort_keys=True))}"

    def _extract_qr_from_perco_event(self, event: dict[str, Any]) -> Optional[str]:
        candidates: list[str] = []

        def collect(value: Any) -> None:
            if value is None:
                return
            if isinstance(value, str):
                s = value.strip()
                if s:
                    candidates.append(s)
                return
            if isinstance(value, (int, float)):
                candidates.append(str(value))
                return
            if isinstance(value, dict):
                for v in value.values():
                    collect(v)
                return
            if isinstance(value, list):
                for v in value:
                    collect(v)

        for key in (
            "identifier",
            "identification",
            "identificationCode",
            "code",
            "cardCode",
            "rawData",
            "raw",
            "text",
            "data",
            "payload",
        ):
            if key in event:
                collect(event.get(key))

        for s in candidates:
            if "FITNESSCLUB:" in s:
                idx = s.find("FITNESSCLUB:")
                return s[idx:]
        return None

    def _execute(self, cmd: dict) -> None:
        cmd_id = cmd.get("id")
        kind = (cmd.get("kind") or "").lower()
        payload = cmd.get("payload") or {}
        LOG.info("→ команда #%s %s payload=%s", cmd_id, kind, payload)

        try:
            if kind == "ping":
                self._ack(cmd_id, "done", {"echo": payload})
                return
            if kind == "open_door":
                if self.perco is None:
                    raise RuntimeError("PERCo не настроен в config.ini, открыть нечем")
                result = _perco_open(self.cfg, self.perco, payload)
                self._ack(cmd_id, "done", {"perco": result})
                return
            if kind == "perco_call":
                if self.perco is None:
                    raise RuntimeError("PERCo не настроен в config.ini")
                method = (payload.get("method") or "GET").upper()
                path = payload.get("path") or "/"
                params = payload.get("params") or None
                json_body = payload.get("json")
                result = self.perco.call(method, path, json_body=json_body, params=params)
                self._ack(cmd_id, "done", {"perco": result})
                return

            raise RuntimeError(f"Неизвестный тип команды: {kind}")
        except PercoApiError as e:
            LOG.error("PERCo error: %s", e)
            self._ack(cmd_id, "failed", {"error": str(e), "status": e.status, "body": e.body})
        except Exception as e:
            LOG.error("ошибка команды #%s: %s", cmd_id, e)
            self._ack(cmd_id, "failed", {"error": str(e)})

    def _ack(self, cmd_id: Optional[int], status: str, result: dict) -> None:
        if cmd_id is None:
            return
        try:
            code, body = self.crm.ack_command(int(cmd_id), status, result)
            if code != 200:
                LOG.warning("ack #%s HTTP %s: %s", cmd_id, code, body)
        except Exception as e:
            LOG.warning("ack #%s error: %s", cmd_id, e)

    # stdin (QR reader) ------------------------------------------------------

    def _loop_stdin_qr(self) -> None:
        LOG.info("Читаем QR из stdin (по строке на сканирование)…")
        for line in sys.stdin:
            if self._stop.is_set():
                break
            qr = line.strip()
            if not qr:
                continue
            self.handle_qr(qr)

    def handle_qr(self, qr: str) -> dict:
        code, body = self.crm.submit_qr(qr)
        LOG.info("QR → CRM: HTTP %s, granted=%s, reason=%s", code, body.get("access_granted"), body.get("reason"))
        if code == 200 and body.get("access_granted"):
            open_device = body.get("open_device")
            if open_device and self.perco is not None:
                try:
                    _perco_open(self.cfg, self.perco, open_device)
                except Exception as e:
                    LOG.error("Не удалось открыть PERCo: %s", e)
        return body


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def _setup_logging(verbose: bool) -> None:
    logging.basicConfig(
        level=logging.DEBUG if verbose else logging.INFO,
        format="%(asctime)s %(levelname)-7s %(name)s: %(message)s",
    )


def cmd_daemon(cfg: GatewayConfig, _args: argparse.Namespace) -> int:
    GatewayDaemon(cfg).run()
    return 0


def cmd_qr(cfg: GatewayConfig, args: argparse.Namespace) -> int:
    qr = (args.text or "").strip()
    if not qr:
        qr = sys.stdin.readline().strip()
    if not qr:
        print("Пустой QR", file=sys.stderr)
        return 2
    daemon = GatewayDaemon(cfg, read_stdin=False)
    body = daemon.handle_qr(qr)
    print(json.dumps(body, ensure_ascii=False, indent=2))
    return 0 if body.get("access_granted") else 1


def cmd_open_now(cfg: GatewayConfig, _args: argparse.Namespace) -> int:
    perco = _build_perco(cfg)
    if perco is None:
        print("PERCo не настроен в config.ini", file=sys.stderr)
        return 2
    res = _perco_open(cfg, perco)
    print(json.dumps(res, ensure_ascii=False, indent=2))
    return 0


def cmd_healthcheck(cfg: GatewayConfig, _args: argparse.Namespace) -> int:
    crm = CrmClient(cfg)
    try:
        code, body = crm.heartbeat({"probe": True})
    except Exception as e:
        print(f"CRM heartbeat: ошибка {e}")
        return 3
    print(f"CRM heartbeat: HTTP {code} {json.dumps(body, ensure_ascii=False)}")

    perco = _build_perco(cfg)
    if perco is None:
        print("PERCo: не настроен (это ОК, если открытие двери не требуется здесь)")
        return 0 if code == 200 else 4
    try:
        token = perco.authenticate()
        print(f"PERCo auth: OK, token len={len(token)}")
    except Exception as e:
        print(f"PERCo auth: ошибка {e}")
        return 5
    return 0 if code == 200 else 4


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="ПК-шлюз клуба для CRM + PERCo")
    parser.add_argument("--verbose", "-v", action="store_true")
    parser.add_argument("--config", help="путь к config.ini", default=None)
    sub = parser.add_subparsers(dest="cmd")

    sub.add_parser("daemon", help="Демон: long-poll команд, heartbeat, чтение QR из stdin")
    p_qr = sub.add_parser("qr", help="Однократно проверить QR в CRM (и при доступе открыть PERCo)")
    p_qr.add_argument("text", nargs="?", help="строка QR (если не задана — читается из stdin)")
    sub.add_parser("open-now", help="Открыть турникет в локальном PERCo (без CRM)")
    sub.add_parser("healthcheck", help="Проверка связи с CRM и с PERCo")

    args = parser.parse_args(argv)
    _setup_logging(args.verbose)
    cfg = GatewayConfig.load(Path(args.config) if args.config else None)

    handlers = {
        None: cmd_daemon,
        "daemon": cmd_daemon,
        "qr": cmd_qr,
        "open-now": cmd_open_now,
        "healthcheck": cmd_healthcheck,
    }
    handler = handlers.get(args.cmd, cmd_daemon)
    try:
        return handler(cfg, args)
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    sys.exit(main())
