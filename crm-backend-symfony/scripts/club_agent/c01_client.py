"""Исходящее подключение к C01 (пока net.server не задан на контроллере)."""
from __future__ import annotations

import asyncio
import json
import logging
import threading
from typing import Any, Awaitable, Callable, Optional

import websockets

from c01_session import C01Session, CardHandler
from equipment import EquipmentItem

LOG = logging.getLogger("c01.client")


class C01Outbound:
    """Агент подключается к контроллеру (режим connect)."""

    def __init__(
        self,
        equipment: EquipmentItem,
        on_card: CardHandler,
        on_log: Optional[Callable[[str, str], None]] = None,
        on_connection_change: Optional[Callable[[str, bool], None]] = None,
    ) -> None:
        self.equipment = equipment
        self.session = C01Session(
            equipment,
            on_log=on_log,
            on_card=on_card,
            on_connected=lambda c: on_connection_change(equipment.id, c) if on_connection_change else None,
        )
        self._loop: Optional[asyncio.AbstractEventLoop] = None
        self._thread: Optional[threading.Thread] = None
        self._stop = asyncio.Event()

    async def _run(self) -> None:
        url = self.equipment.ws_connect_url()
        self.session._emit("info", f"Подключение к C01 {url}")
        while not self._stop.is_set():
            try:
                async with websockets.connect(url, ping_interval=20, ping_timeout=20) as ws:
                    await self.session.attach(ws)
                    async for raw in ws:
                        if self._stop.is_set():
                            break
                        try:
                            data = json.loads(raw)
                        except json.JSONDecodeError:
                            continue
                        reply = await self.session.handle_incoming(data)
                        if reply:
                            await ws.send(json.dumps(reply))
            except Exception as e:
                self.session._emit("warning", f"Связь потеряна: {e}")
            finally:
                await self.session.detach()
            if not self._stop.is_set():
                await asyncio.sleep(3.0)

    def start_background(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._stop = asyncio.Event()

        def runner() -> None:
            self._loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self._loop)
            try:
                self._loop.run_until_complete(self._run())
            finally:
                self._loop.close()

        self._thread = threading.Thread(target=runner, name=f"c01-out-{self.equipment.id}", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        if self._loop:
            self._loop.call_soon_threadsafe(self._stop.set)

    def open_door_sync(self) -> bool:
        from c01_protocol import exdev_open

        if not self.session.connected or not self._loop:
            return False
        msg = exdev_open(
            self.equipment.exdev_number,
            self.equipment.exdev_direction,
            open_type=self.equipment.open_type,
            open_time_ms=self.equipment.open_time_ms,
        )

        async def send() -> None:
            await self.session.send_json(msg)

        try:
            asyncio.run_coroutine_threadsafe(send(), self._loop).result(timeout=5.0)
            return True
        except Exception:
            return False

    @property
    def connected(self) -> bool:
        return self.session.connected

    def run_config_sync(
        self,
        coro_factory: Callable[[C01Session], Awaitable[Any]],
        timeout: float = 60.0,
    ) -> Any:
        """Одноразовое подключение для настройки (из GUI)."""
        async def job() -> Any:
            url = self.equipment.ws_connect_url()
            async with websockets.connect(url, ping_interval=20, ping_timeout=20) as ws:
                await self.session.attach(ws)
                try:
                    # Дождаться auth (need_auth приходит первым)
                    for _ in range(20):
                        if self.session.authenticated:
                            break
                        try:
                            raw = await asyncio.wait_for(ws.recv(), timeout=2.0)
                            data = json.loads(raw)
                            await self.session.handle_incoming(data)
                        except asyncio.TimeoutError:
                            if not self.equipment.password:
                                self.session._authenticated = True
                                break
                    if self.equipment.password and not self.session.authenticated:
                        raise RuntimeError("Не удалось авторизоваться (пароль?)")
                    return await coro_factory(self.session)
                finally:
                    await self.session.detach()

        loop = asyncio.new_event_loop()
        try:
            return loop.run_until_complete(asyncio.wait_for(job(), timeout=timeout))
        finally:
            loop.close()
