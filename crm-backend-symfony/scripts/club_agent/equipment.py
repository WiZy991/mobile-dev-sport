"""Оборудование клуба (контроллер PERCo-C01)."""
from __future__ import annotations

import uuid
from dataclasses import asdict, dataclass, field
from typing import Any, Literal

ConnectionMode = Literal["listen", "connect"]


@dataclass
class EquipmentItem:
    """
    PERCo-C01.

    Режимы подключения:
    - listen: агент — «сервер системы», C01 подключается на net.server:порт.
    - connect: агент подключается к IP контроллера (пока net.server не задан).
    """

    id: str = field(default_factory=lambda: uuid.uuid4().hex[:8])
    name: str = "Турникет вход"
    device_type: str = "perco_c01"
    enabled: bool = True

    # Режим WebSocket
    connection_mode: str = "listen"
    listen_host: str = "0.0.0.0"
    listen_port: int = 8765
    c01_host: str = "192.168.1.201"
    c01_ws_port: int = 80

    # Пароль: need_auth md5(salt+password), то же в net.password
    password: str = ""

    # Сеть net
    net_ip: str = ""
    net_mask: str = "255.255.255.0"
    net_gateway: str = ""
    net_server: str = ""

    # Считыватель reader
    reader_number: int = 0
    reader_type: str = "Barcode-USB"
    reader_port: int = 0

    # Исполнительное устройство exdev
    exdev_number: int = 0
    exdev_direction: int = 0
    exdev_type: str = "turnstyle"
    exdev_opt_fix: str = "card"
    wait_command_time: int = 3000

    # Режим контроля доступа
    access_mode: str = "control"

    # Команда открытия
    open_type: str = "open once"
    open_time_ms: int = 3000

    notes: str = ""

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "EquipmentItem":
        known = {f.name for f in cls.__dataclass_fields__.values()}  # type: ignore[attr-defined]
        filtered = {k: v for k, v in data.items() if k in known}
        if "id" not in filtered or not str(filtered.get("id", "")).strip():
            filtered["id"] = uuid.uuid4().hex[:8]
        if filtered.get("connection_mode") not in ("listen", "connect"):
            filtered["connection_mode"] = "listen"
        return cls(**filtered)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

    def ws_listen_url(self) -> str:
        return f"ws://{self.listen_host}:{self.listen_port}"

    def ws_connect_url(self) -> str:
        return f"ws://{self.c01_host}:{self.c01_ws_port}"

    def label(self) -> str:
        mode = "слушать" if self.connection_mode == "listen" else f"→{self.c01_host}"
        return f"{self.name} [{mode}]"
