"""Настройка C01: set net / reader / exdev / acm, get net / state."""
from __future__ import annotations

from typing import Callable, Optional

from c01_protocol import control_acm, get_net, get_state, set_exdev_config, set_net, set_reader
from c01_session import C01Session
from equipment import EquipmentItem
from net_util import get_lan_ip

OnLog = Optional[Callable[[str, str], None]]


async def read_net(session: C01Session) -> dict:
    return await session.request(get_net(), expect_key="net")


async def read_state(session: C01Session) -> dict:
    return await session.request(get_state(), expect_key="state")


async def write_net(session: C01Session, eq: EquipmentItem, server_ip: str) -> dict:
    ip = eq.net_ip.strip()
    if not ip:
        raise ValueError("Укажите IP контроллера (net.ip)")
    return await session.request(
        set_net(
            ip=ip,
            mask=eq.net_mask or "255.255.255.0",
            gateway=eq.net_gateway or ip.rsplit(".", 1)[0] + ".1",
            server=eq.net_server.strip() or server_ip,
            password=eq.password,
        ),
        expect_key="net",
    )


async def write_reader(session: C01Session, eq: EquipmentItem) -> dict:
    return await session.request(
        set_reader(
            number=eq.reader_number,
            type=eq.reader_type,
            port=eq.reader_port,
            exdev_number=eq.exdev_number,
            exdev_direction=eq.exdev_direction,
        ),
        expect_key="reader",
    )


async def write_exdev(session: C01Session, eq: EquipmentItem) -> dict:
    return await session.request(
        set_exdev_config(
            number=eq.exdev_number,
            type=eq.exdev_type,
            opt_fix=eq.exdev_opt_fix,
            wait_command_time=eq.wait_command_time,
        ),
        expect_key="exdev",
    )


async def write_acm(session: C01Session, eq: EquipmentItem) -> dict:
    return await session.request(
        control_acm(eq.exdev_number, eq.exdev_direction, eq.access_mode),
        expect_key="acm",
    )


async def apply_club_setup(
    session: C01Session,
    eq: EquipmentItem,
    *,
    server_ip: Optional[str] = None,
    on_log: OnLog = None,
) -> None:
    """Мастер: net → reader → exdev → РКД control (турникет + QR)."""
    lan = server_ip or get_lan_ip()

    def log(level: str, msg: str) -> None:
        if on_log:
            on_log(level, msg)

    log("info", f"Запись net (server={eq.net_server or lan})…")
    net_resp = await write_net(session, eq, lan)
    log("info", f"net OK: {net_resp.get('net', {})}")

    log("info", f"Запись reader ({eq.reader_type})…")
    await write_reader(session, eq)

    log("info", f"Запись exdev ({eq.exdev_type}, wait={eq.wait_command_time}ms)…")
    await write_exdev(session, eq)

    log("info", f"РКД → {eq.access_mode}…")
    await write_acm(session, eq)

    log("info", "Мастер настройки завершён. Переведите режим на «Слушать» и перезапустите агент.")
