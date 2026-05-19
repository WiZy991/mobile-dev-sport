"""Фоновые задачи: CRM + C01 (режимы listen / connect)."""
from __future__ import annotations

import json
import logging
import threading
from typing import Any, Callable, Optional, Union

from c01_client import C01Outbound
from c01_server import C01Server
from config import AgentConfig
from crm_client import CrmClient
from equipment import EquipmentItem

LOG = logging.getLogger("agent")

Endpoint = Union[C01Server, C01Outbound]


class ClubAgent:
    def __init__(
        self,
        cfg: AgentConfig,
        on_log: Callable[[str, str], None],
        on_status: Optional[Callable[[bool, dict[str, bool]], None]] = None,
    ) -> None:
        self.cfg = cfg
        self.on_log = on_log
        self.on_status = on_status
        self._stop = threading.Event()
        self._crm_thread: Optional[threading.Thread] = None
        self._endpoints: dict[str, Endpoint] = {}
        self._c01_connected: dict[str, bool] = {}
        self._crm_ok = False
        self._selected_equipment_id: Optional[str] = None

    def _emit(self, level: str, msg: str) -> None:
        self.on_log(level, msg)

    def _notify_status(self) -> None:
        if self.on_status:
            self.on_status(self._crm_ok, dict(self._c01_connected))

    def _on_c01_connection(self, equipment_id: str, connected: bool) -> None:
        self._c01_connected[equipment_id] = connected
        self._notify_status()

    def _crm_client(self) -> CrmClient:
        return CrmClient(self.cfg, on_exchange=self._log_crm_exchange)

    def _log_crm_exchange(
        self,
        method: str,
        path: str,
        request_body: Optional[dict],
        status: int,
        response: dict,
    ) -> None:
        self._emit("info", "────── CRM запрос ──────")
        self._emit("info", f"→ {method} {path}")
        if request_body is not None:
            self._emit("info", f"  тело: {json.dumps(request_body, ensure_ascii=False)}")
        self._emit("info", f"← HTTP {status}")
        self._emit("info", f"  тело: {json.dumps(response, ensure_ascii=False)}")
        granted = response.get("access_granted")
        reason = response.get("reason", "")
        if granted is True:
            user = response.get("user") or {}
            self._emit("info", f"  итог: ДОПУСК — {user.get('name') or user.get('id', '')}")
        elif granted is False:
            self._emit("warning", f"  итог: ОТКАЗ — reason={reason}")

    def _log_c01_command(self, equipment: EquipmentItem, cmd: dict, label: str) -> None:
        self._emit("info", f"────── C01 команда ({label}) ──────")
        self._emit("info", f"→ {json.dumps(cmd, ensure_ascii=False)}")

    async def _handle_card(self, qr: str, number: int, direction: int, equipment: EquipmentItem) -> bool:
        self._emit("info", "══════ ПРОХОД: скан QR ══════")
        self._emit("info", f"← C01 event card | id={qr}")

        if self.cfg.only_fitnessclub_qr and not qr.startswith("FITNESSCLUB:"):
            self._emit("warning", "QR не FITNESSCLUB — в CRM не отправляем")
            return False
        if not self.cfg.crm_ready():
            self._emit("error", "CRM не настроен (URL / gateway_token)")
            return False

        import asyncio

        code, body = await asyncio.to_thread(lambda: self._crm_client().submit_qr(qr))
        granted = code == 200 and bool(body.get("access_granted"))

        from c01_protocol import access_deny, exdev_open

        if granted:
            cmd = exdev_open(
                number,
                direction,
                open_type=equipment.open_type,
                open_time_ms=equipment.open_time_ms,
            )
            self._log_c01_command(equipment, cmd, "открыть турникет")
        else:
            cmd = access_deny(number, direction)
            self._log_c01_command(equipment, cmd, "запрет прохода")

        return granted

    def _crm_loop(self) -> None:
        while not self._stop.is_set():
            if not self.cfg.crm_ready():
                self._crm_ok = False
                self._notify_status()
                self._stop.wait(5.0)
                continue
            try:
                code, _body = self._crm_client().heartbeat(
                    {"agent": "FitnessClubAgent", "equipment": self._c01_connected},
                    log=False,
                )
                self._crm_ok = code == 200
            except Exception as e:
                self._crm_ok = False
                self._emit("error", f"CRM heartbeat: {e}")
            self._notify_status()

            if self.cfg.crm_poll_enabled:
                try:
                    code, data = self._crm_client().poll_commands(timeout=30.0)
                    if code == 200:
                        for cmd in data.get("commands") or []:
                            self._execute_command(None, cmd)
                except Exception as e:
                    self._emit("debug", f"CRM poll: {e}")

            self._stop.wait(max(1.0, self.cfg.heartbeat_interval_sec))

    def _pick_endpoint(self) -> Optional[Endpoint]:
        if self._selected_equipment_id:
            ep = self._endpoints.get(self._selected_equipment_id)
            if ep and ep.connected:
                return ep
        for ep in self._endpoints.values():
            if ep.connected:
                return ep
        return None

    def _execute_command(self, crm: Optional[CrmClient], cmd: dict) -> None:
        if crm is None:
            crm = self._crm_client()
        cmd_id = cmd.get("id")
        kind = (cmd.get("kind") or "").lower()
        try:
            if kind == "ping":
                crm.ack_command(int(cmd_id), "done", {"echo": cmd.get("payload") or {}})
                return
            if kind == "open_door":
                ep = self._pick_endpoint()
                if ep is None or not ep.open_door_sync():
                    raise RuntimeError("C01 не подключён")
                crm.ack_command(int(cmd_id), "done", {"c01": "open"})
                return
            raise RuntimeError(f"Неизвестная команда: {kind}")
        except Exception as e:
            self._emit("error", f"Команда #{cmd_id}: {e}")
            if cmd_id is not None:
                crm.ack_command(int(cmd_id), "failed", {"error": str(e)})

    def start(self) -> None:
        self._stop.clear()
        self._endpoints.clear()
        self._c01_connected.clear()
        for eq in self.cfg.enabled_equipment():
            self._c01_connected[eq.id] = False
            if eq.connection_mode == "connect":
                ep: Endpoint = C01Outbound(
                    eq,
                    self._handle_card,
                    on_log=self.on_log,
                    on_connection_change=self._on_c01_connection,
                )
                self._emit("info", f"{eq.name}: режим «к контроллеру» {eq.ws_connect_url()}")
            else:
                ep = C01Server(
                    eq,
                    self._handle_card,
                    on_log=self.on_log,
                    on_connection_change=self._on_c01_connection,
                )
                self._emit("info", f"{eq.name}: сервер системы {eq.ws_listen_url()}")
            self._endpoints[eq.id] = ep
            ep.start_background()
        self._crm_thread = threading.Thread(target=self._crm_loop, daemon=True)
        self._crm_thread.start()
        self._emit("info", "Агент запущен")

    def stop(self) -> None:
        self._stop.set()
        for ep in self._endpoints.values():
            ep.stop()
        self._endpoints.clear()
        self._c01_connected.clear()
        self._crm_ok = False
        self._notify_status()

    def get_server(self, equipment_id: str) -> Optional[C01Server]:
        ep = self._endpoints.get(equipment_id)
        return ep if isinstance(ep, C01Server) else None

    def get_endpoint(self, equipment_id: str) -> Optional[Endpoint]:
        return self._endpoints.get(equipment_id)

    def set_selected_equipment(self, equipment_id: Optional[str]) -> None:
        self._selected_equipment_id = equipment_id

    def test_crm(self) -> tuple[bool, str]:
        if not self.cfg.crm_ready():
            return False, "Укажите URL CRM и gateway_token"
        try:
            code, body = self._crm_client().heartbeat({"probe": True}, log=True)
            return code == 200, f"HTTP {code} (см. журнал)"
        except Exception as e:
            return False, str(e)

    def submit_qr_manual(self, qr: str, equipment_id: Optional[str] = None) -> tuple[bool, str]:
        if not self.cfg.crm_ready():
            return False, "CRM не настроен"
        self._emit("info", "══════ ПРОХОД: тест QR (вручную) ══════")
        self._emit("info", f"QR: {qr.strip()}")
        code, body = self._crm_client().submit_qr(qr.strip())
        granted = code == 200 and bool(body.get("access_granted"))
        if granted:
            ep = None
            if equipment_id:
                ep = self._endpoints.get(equipment_id)
            if ep is None:
                ep = self._pick_endpoint()
            if ep and ep.connected:
                ep.open_door_sync()
                self._emit("info", "→ C01: команда open отправлена")
            else:
                self._emit("warning", "CRM разрешил, но C01 не подключён — дверь не открыта")
        reason = body.get("reason", "")
        name = (body.get("user") or {}).get("name", "")
        summary = f"HTTP {code}, granted={granted}, reason={reason}"
        if name:
            summary += f", клиент={name}"
        return granted, summary

    def open_door(self, equipment_id: Optional[str] = None) -> bool:
        if equipment_id:
            ep = self._endpoints.get(equipment_id)
            return ep.open_door_sync() if ep else False
        ep = self._pick_endpoint()
        return ep.open_door_sync() if ep else False

    @property
    def crm_online(self) -> bool:
        return self._crm_ok

    def equipment_connected(self) -> dict[str, bool]:
        return dict(self._c01_connected)

    @property
    def c01_online(self) -> bool:
        return any(self._c01_connected.values())
