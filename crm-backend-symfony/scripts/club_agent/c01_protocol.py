"""Сообщения протокола PERCo-C01 (WebSocket JSON)."""
from __future__ import annotations

from typing import Any, Optional


# --- Авторизация ---

def auth_response(password_hash: str) -> dict[str, Any]:
    return {"set": "auth", "auth": {"hash": password_hash}}


# --- Конфигурация ---

def get_net() -> dict[str, Any]:
    return {"get": "net"}


def set_net(
    *,
    ip: str,
    mask: str,
    gateway: str,
    server: str,
    password: str = "",
) -> dict[str, Any]:
    return {
        "set": "net",
        "net": {
            "ip": ip,
            "mask": mask,
            "gateway": gateway,
            "server": server,
            "password": password,
        },
    }


def get_reader(number: int = 0) -> dict[str, Any]:
    return {"get": "reader", "number": number}


def set_reader(
    *,
    number: int = 0,
    type: str = "Barcode-USB",
    port: int = 0,
    exdev_number: int = 0,
    exdev_direction: int = 0,
) -> dict[str, Any]:
    return {
        "set": "reader",
        "reader": {
            "number": number,
            "type": type,
            "port": port,
            "exdev_number": exdev_number,
            "exdev_direction": exdev_direction,
        },
    }


def get_exdev(number: int = 0) -> dict[str, Any]:
    return {"get": "exdev", "number": number}


def set_exdev_config(
    *,
    number: int = 0,
    type: str = "turnstyle",
    opt_fix: str = "card",
    wait_command_time: int = 3000,
    analysis_time: int = 500,
    unblock_time: int = 5000,
    opt_mode: str = "potencial",
    opt_norm: str = "afterclosed",
    impulse_time: int = 150,
    remove_card_time: int = 150,
) -> dict[str, Any]:
    return {
        "set": "exdev",
        "exdev": {
            "number": number,
            "type": type,
            "opt_fix": opt_fix,
            "analysis_time": analysis_time,
            "unblock_time": unblock_time,
            "opt_mode": opt_mode,
            "opt_norm": opt_norm,
            "impulse_time": impulse_time,
            "remove_card_time": remove_card_time,
            "wait_command_time": wait_command_time,
        },
    }


def get_state() -> dict[str, Any]:
    return {"get": "state"}


# --- Управление ---

def control_acm(number: int, direction: int, access_mode: str) -> dict[str, Any]:
    """РКД: open | control."""
    return {
        "control": "acm",
        "acm": {"number": number, "direction": direction, "access_mode": access_mode},
    }


def exdev_open(
    number: int,
    direction: int,
    *,
    open_type: str = "open once",
    open_time_ms: int = 3000,
) -> dict[str, Any]:
    return {
        "control": "exdev",
        "exdev": {
            "number": number,
            "direction": direction,
            "action": "open",
            "open_type": open_type,
            "open_time": open_time_ms,
        },
    }


def exdev_close(number: int, direction: int) -> dict[str, Any]:
    return {
        "control": "exdev",
        "exdev": {
            "number": number,
            "direction": direction,
            "action": "close",
            "open_type": "",
            "open_time": 1000,
        },
    }


def access_deny(number: int, direction: int) -> dict[str, Any]:
    return {
        "control": "access",
        "access": {"number": number, "direction": direction},
    }


READER_TYPES = (
    "Wiegand",
    "Barcode",
    "Barcode-USB",
    "Barcode_terminator",
    "Barcode-USB_terminator",
)

EXDEV_TYPES = ("lock", "double lock", "turnstyle", "gate")
