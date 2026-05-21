"""Сетевые утилиты для подсказок при настройке C01."""
from __future__ import annotations

import errno
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


def tcp_refused_c01_hint(exc: BaseException) -> str | None:
    """
    Если похоже на «на этом IP:порту никто не слушает» — короткая подсказка для журнала/диалога.
    Windows: WinError 1225 / 10061, errno ECONNREFUSED.
    """
    msg = str(exc)
    low = msg.lower()
    winerr = getattr(exc, "winerror", None)
    en = getattr(exc, "errno", None)
    refused = (
        en == errno.ECONNREFUSED
        or winerr in (1225, 10061)
        or "1225" in msg
        or "10061" in msg
        or "отклонил" in low
        or "connection refused" in low
        or "actively refused" in low
    )
    if not refused:
        return None
    return (
        "На этом IP:порту нет приёма TCP (соединение отклонено). "
        "Проверьте «Порт WS на C01» по документации PERCo; 8080 часто просто не тот порт. "
        "Если в веб-интерфейсе PERCo уже указан адрес сервера на этот ПК — в агенте выберите режим "
        "«Слушать», порт (например 8765), сохраните и перезапустите агент: контроллер подключится сам."
    )
