"""Сетевые утилиты для подсказок при настройке C01."""
from __future__ import annotations

import socket


def get_lan_ip() -> str:
    """Локальный IP для поля net.server на контроллере (best effort)."""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))
            return s.getsockname()[0]
    except OSError:
        pass
    try:
        return socket.gethostbyname(socket.gethostname())
    except OSError:
        return "127.0.0.1"
