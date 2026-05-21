"""Симулятор C01 для проверки агента без железа."""
from __future__ import annotations

import asyncio
import hashlib
import json
import logging
import threading
from typing import Any, Callable, Optional

import websockets

from equipment import EquipmentItem

LOG = logging.getLogger("c01.sim")


class C01Simulator:
    def __init__(
        self,
        equipment: EquipmentItem,
        *,
        connect_host: str = "127.0.0.1",
        on_log: Optional[Callable[[str, str], None]] = None,
    ) -> None:
        self.equipment = equipment
        self.connect_host = connect_host
        self.on_log = on_log
        self._ws: Any = None
        self._loop: Optional[asyncio.AbstractEventLoop] = None
        self._thread: Optional[threading.Thread] = None
        self._connected = threading.Event()
        self._stop = threading.Event()
        self._state = {
            "net": {
                "ip": equipment.net_ip or "192.168.1.201",
                "mask": equipment.net_mask,
                "gateway": equipment.net_gateway or "192.168.1.1",
                "server": equipment.net_server or "",
                "password": equipment.password,
            },
            "reader": {
                "number": equipment.reader_number,
                "type": equipment.reader_type,
                "port": equipment.reader_port,
                "exdev_number": equipment.exdev_number,
                "exdev_direction": equipment.exdev_direction,
            },
            "exdev": {
                "number": equipment.exdev_number,
                "type": equipment.exdev_type,
                "wait_command_time": equipment.wait_command_time,
            },
        }

    def _emit(self, level: str, msg: str) -> None:
        if self.on_log:
            self.on_log(level, msg)

    def _url(self) -> str:
        host = self.equipment.listen_host
        if host in ("0.0.0.0", ""):
            host = self.connect_host
        return f"ws://{host}:{self.equipment.listen_port}"

    async def _reply(self, ws: Any, data: dict[str, Any]) -> None:
        if data.get("set") == "auth" or (data.get("auth") and "hash" in (data.get("auth") or {})):
            await ws.send(
                json.dumps({"answer": {"auth": "ok"}, "auth": data.get("auth", {})})
            )
            return
        if data.get("set") == "net":
            net = data.get("net") or {}
            if net:
                self._state["net"].update(net)
            await ws.send(json.dumps({"answer": {"net": "ok"}, "net": self._state["net"]}))
            return
        if data.get("get") == "net":
            await ws.send(json.dumps({"answer": {"net": "ok"}, "net": self._state["net"]}))
            return
        if data.get("set") == "reader":
            r = data.get("reader") or {}
            if r:
                self._state["reader"].update(r)
            await ws.send(json.dumps({"answer": {"reader": "ok"}, "reader": self._state["reader"]}))
            return
        if data.get("set") == "exdev":
            x = data.get("exdev") or {}
            if x:
                self._state["exdev"].update(x)
            await ws.send(json.dumps({"answer": {"exdev": "ok"}, "exdev": self._state["exdev"]}))
            return
        if data.get("get") == "state":
            await ws.send(
                json.dumps(
                    {
                        "answer": {"state": "ok"},
                        "state": {
                            "exdev": [{"access_mode": ["control"], "unlock_state": ["lock"]}],
                            "cover_on": False,
                        },
                    }
                )
            )
            return
        if data.get("control") == "acm":
            await ws.send(json.dumps({"result": {"acm": "ok"}, "acm": data.get("acm")}))
            return
        if data.get("control") == "exdev":
            await ws.send(json.dumps({"result": {"exdev": "ok"}, "exdev": data.get("exdev")}))
            return
        if data.get("control") == "access":
            await ws.send(json.dumps({"result": {"access": "ok"}, "access": data.get("access")}))
            return
        if data.get("control") == "output":
            await ws.send(json.dumps({"result": {"output": "ok"}, "output": data.get("output")}))
            return
        if data.get("control") == "cross reference":
            await ws.send(
                json.dumps(
                    {"result": {"cross reference": "ok"}, "cross reference": data.get("cross reference")}
                )
            )
            return

    async def _session(self) -> None:
        url = self._url()
        self._emit("info", f"Симулятор → {url}")
        async with websockets.connect(url, ping_interval=20, ping_timeout=20) as ws:
            self._ws = ws
            self._connected.set()
            salt = "simsalt1"
            await ws.send(json.dumps({"event": "need_auth", "need_auth": {"salt": salt}}))
            async for raw in ws:
                if self._stop.is_set():
                    break
                try:
                    data = json.loads(raw)
                except json.JSONDecodeError:
                    continue
                self._emit("debug", f"Сим ← {json.dumps(data, ensure_ascii=False)[:300]}")
                if data.get("event") == "card":
                    continue
                await self._reply(ws, data)
        self._ws = None
        self._connected.clear()

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._stop.clear()

        def runner() -> None:
            self._loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self._loop)
            while not self._stop.is_set():
                try:
                    self._loop.run_until_complete(self._session())
                except Exception as e:
                    self._emit("error", f"Симулятор: {e}")
                if not self._stop.is_set():
                    self._stop.wait(3.0)

        self._thread = threading.Thread(target=runner, daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        self._connected.clear()

    @property
    def is_connected(self) -> bool:
        return self._connected.is_set()

    def send_card(self, card_id: str) -> bool:
        if not self.is_connected or not self._loop or not self._ws:
            return False
        payload = json.dumps(
            {
                "event": "card",
                "card": {
                    "number": self.equipment.exdev_number,
                    "direction": self.equipment.exdev_direction,
                    "id": card_id,
                },
            }
        )

        async def send() -> None:
            await self._ws.send(payload)
            self._emit("info", f"Сим → card {card_id[:64]}")

        try:
            asyncio.run_coroutine_threadsafe(send(), self._loop).result(timeout=5.0)
            return True
        except Exception as e:
            self._emit("error", str(e))
            return False
