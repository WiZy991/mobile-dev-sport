"""WebSocket-сервер «сервер системы» — C01 подключается к агенту."""
from __future__ import annotations

import asyncio
import json
import logging
import threading
from typing import Any, Callable, Optional

import websockets

from c01_session import C01Session, CardHandler
from equipment import EquipmentItem

LOG = logging.getLogger("c01.server")


class C01Server:
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

    async def _handle_connection(self, ws: Any) -> None:
        peer = ws.remote_address
        self.session._emit("info", f"C01 подключился: {peer}")
        await self.session.attach(ws)
        try:
            async for raw in ws:
                try:
                    data = json.loads(raw)
                except json.JSONDecodeError:
                    continue
                reply = await self.session.handle_incoming(data)
                if reply:
                    await ws.send(json.dumps(reply))
        except websockets.ConnectionClosed as e:
            self.session._emit("info", f"C01 отключён ({e.code})")
        finally:
            await self.session.detach()

    async def _run_server(self) -> None:
        eq = self.equipment
        self.session._emit("info", f"Сервер системы: ws://{eq.listen_host}:{eq.listen_port}")

        async def log_process_request(connection: Any, request: Any) -> None:
            """Диагностика: видно, доходит ли TCP/HTTP до порта (брандмауэр, порт)."""
            peer = connection.remote_address
            try:
                hdrs = request.headers
                up = hdrs.get("Upgrade", "")
                conn_h = hdrs.get("Connection", "")
                key = "да" if hdrs.get("Sec-WebSocket-Key") else "нет"
                self.session._emit(
                    "info",
                    f"Запрос к WS с {peer}: path={request.path!r} Upgrade={up!r} "
                    f"Connection={conn_h!r} Sec-WebSocket-Key={key}",
                )
            except Exception as e:
                self.session._emit("warning", f"Запрос к WS с {peer}: не разобрать заголовки ({e})")
            return None

        async with websockets.serve(
            self._handle_connection,
            eq.listen_host,
            eq.listen_port,
            ping_interval=20,
            ping_timeout=20,
            # Встроенные клиенты (PERCo и др.) часто ломаются на permessage-deflate.
            compression=None,
            process_request=log_process_request,
        ):
            await self._stop.wait()

    def start_background(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._stop = asyncio.Event()

        def runner() -> None:
            self._loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self._loop)
            try:
                self._loop.run_until_complete(self._run_server())
            finally:
                pending = asyncio.all_tasks(self._loop)
                for t in pending:
                    t.cancel()
                self._loop.run_until_complete(asyncio.gather(*pending, return_exceptions=True))
                self._loop.close()

        self._thread = threading.Thread(target=runner, name=f"c01-srv-{self.equipment.id}", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        if self._loop and self._stop:
            self._loop.call_soon_threadsafe(self._stop.set)

    def open_door_sync(self) -> bool:
        if not self.session.connected or not self._loop:
            return False
        pulse_ms = 0
        if getattr(self.equipment, "relay_use_cross_reference", False) or getattr(
            self.equipment, "relay_after_grant", False
        ):
            pulse_ms = max(pulse_ms, int(getattr(self.equipment, "relay_pulse_ms", 0)))

        async def send() -> None:
            await self.session.send_access_granted_actions(
                self.equipment.exdev_number,
                self.equipment.exdev_direction,
            )

        timeout = max(5.0, pulse_ms / 1000.0 + 2.0)
        try:
            asyncio.run_coroutine_threadsafe(send(), self._loop).result(timeout=timeout)
            return True
        except Exception:
            return False

    def run_on_active_connection_sync(
        self,
        coro_factory: Callable[..., Any],
        timeout: float = 30.0,
    ) -> Any:
        if not self.session.connected or not self._loop:
            raise RuntimeError("C01 не подключён — дождитесь соединения или режим «Подключиться»")

        async def job() -> Any:
            result = coro_factory(self.session)
            if asyncio.iscoroutine(result):
                return await result
            return result

        return asyncio.run_coroutine_threadsafe(
            asyncio.wait_for(job(), timeout=timeout),
            self._loop,
        ).result(timeout=timeout + 2)

    @property
    def connected(self) -> bool:
        return self.session.connected
