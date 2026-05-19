"""HTTP-клиент облачного CRM (/api/v1/gateway/*) с логированием обмена."""
from __future__ import annotations

import json
import ssl
import urllib.error
import urllib.request
from typing import Any, Callable, Optional

from config import AgentConfig

ExchangeLogger = Callable[[str, str, Optional[dict], int, dict], None]


class CrmClient:
    def __init__(
        self,
        cfg: AgentConfig,
        *,
        on_exchange: Optional[ExchangeLogger] = None,
    ) -> None:
        self.cfg = cfg
        self.on_exchange = on_exchange

    def _ssl_ctx(self) -> Optional[ssl.SSLContext]:
        base = self.cfg.crm_base_url.lower()
        if not base.startswith("https") or self.cfg.crm_verify_ssl:
            return None
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        return ctx

    def _request(
        self,
        method: str,
        path: str,
        *,
        json_body: Any = None,
        timeout: float = 30.0,
        log_exchange: bool = True,
    ) -> tuple[int, dict]:
        url = self.cfg.crm_base_url.rstrip("/") + path
        body_bytes = json.dumps(json_body, ensure_ascii=False).encode("utf-8") if json_body is not None else None
        headers = {
            "Accept": "application/json",
            "Authorization": f"Bearer {self.cfg.gateway_token}",
            "User-Agent": "FitnessClubAgent/1.0",
        }
        if body_bytes is not None:
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(url, data=body_bytes, headers=headers, method=method.upper())
        try:
            with urllib.request.urlopen(req, timeout=timeout, context=self._ssl_ctx()) as r:
                raw = r.read().decode("utf-8", errors="replace")
                status = r.status
                parsed: dict = json.loads(raw) if raw.strip() else {}
        except urllib.error.HTTPError as e:
            status = e.code
            raw = e.read().decode("utf-8", errors="replace")
            try:
                parsed = json.loads(raw) if raw.strip() else {}
            except json.JSONDecodeError:
                parsed = {"raw": raw}

        if log_exchange and self.on_exchange:
            self.on_exchange(method, path, json_body, status, parsed)
        return status, parsed

    def submit_qr(self, qr: str) -> tuple[int, dict]:
        return self._request(
            "POST",
            "/api/v1/gateway/access/entry",
            json_body={"qr": qr, "device_id": self.cfg.device_id},
        )

    def heartbeat(self, payload: Optional[dict] = None, *, log: bool = False) -> tuple[int, dict]:
        return self._request(
            "POST",
            "/api/v1/gateway/heartbeat",
            json_body=payload or {},
            timeout=15.0,
            log_exchange=log,
        )

    def poll_commands(self, timeout: float = 35.0) -> tuple[int, dict]:
        return self._request("GET", "/api/v1/gateway/commands", log_exchange=False, timeout=timeout)

    def ack_command(self, command_id: int, status: str, result: Optional[dict] = None) -> tuple[int, dict]:
        return self._request(
            "POST",
            f"/api/v1/gateway/commands/{int(command_id)}/ack",
            json_body={"status": status, "result": result or {}},
            timeout=15.0,
            log_exchange=False,
        )
