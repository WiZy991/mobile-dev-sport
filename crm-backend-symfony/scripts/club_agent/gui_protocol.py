"""Выполнение команд протокола C01 из GUI (в фоновом потоке)."""
from __future__ import annotations

import asyncio
import json
import threading
from typing import Any, Callable, Optional

from c01_client import C01Outbound
from c01_configurator import apply_club_setup, read_net, read_state, write_net
from c01_protocol import exdev_close, exdev_open
from c01_server import C01Server
from equipment import EquipmentItem

OnLog = Callable[[str, str], None]


def run_with_session(
    eq: EquipmentItem,
    async_fn: Callable,
    *,
    agent: Any = None,
    on_log: OnLog,
) -> Any:
    """
    listen + агент + C01 подключён → существующая сессия.
    connect или нет связи → одноразовое подключение к IP контроллера.
    """
    if agent is not None:
        ep = agent.get_endpoint(eq.id)
        if isinstance(ep, C01Server) and ep.connected:
            return ep.run_on_active_connection_sync(async_fn)
        if isinstance(ep, C01Outbound) and ep.connected and ep._loop:
            async def job() -> Any:
                return await async_fn(ep.session)

            return asyncio.run_coroutine_threadsafe(
                asyncio.wait_for(job(), timeout=60),
                ep._loop,
            ).result(timeout=65)

    out = C01Outbound(eq, on_card=_noop_card, on_log=on_log)
    return out.run_config_sync(async_fn)


async def _noop_card(*_a: Any, **_k: Any) -> bool:
    return False


def run_in_thread(
    target: Callable[[], None],
    on_done: Optional[Callable[[], None]] = None,
) -> None:
    def wrapper() -> None:
        try:
            target()
        finally:
            if on_done:
                on_done()

    threading.Thread(target=wrapper, daemon=True).start()
