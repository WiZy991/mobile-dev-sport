"""Оборудование клуба (контроллер PERCo-C01)."""
from __future__ import annotations

import uuid
from dataclasses import asdict, dataclass, field
from typing import Any, Literal, Optional

ConnectionMode = Literal["listen", "connect"]


def _optional_int(value: Any) -> Optional[int]:
    """Пустая строка / None → не задано; иначе int."""
    if value is None:
        return None
    if isinstance(value, str) and not value.strip():
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


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

    # После допуска CRM: дополнительный импульс на дискретном выходе (реле и т.п., см. PDF п. 2.3, 5.9)
    relay_after_grant: bool = False
    relay_output_number: int = 0
    relay_pulse_ms: int = 300
    # Вместо control output — кратко включить внутреннюю реакцию (п. 4.4), если так настроен PERCo
    relay_use_cross_reference: bool = False
    relay_cross_number: int = 0

    # Роль точки: fallback, если entry_reader_number / exit_reader_number не заданы
    gate_role: str = "entry"

    # Номера считывателей PERCo (поле number в card/pass_personal). Пусто = не задано.
    entry_reader_number: Optional[int] = None
    exit_reader_number: Optional[int] = None

    notes: str = ""

    def readers_configured(self) -> bool:
        return self.entry_reader_number is not None or self.exit_reader_number is not None

    def resolve_passage(self, event_number: int) -> tuple[Optional[str], Optional[str]]:
        """
        (passage, error).
        passage: entry | exit; error — текст отказа, если считыватель неизвестен.
        """
        if not self.readers_configured():
            role = (self.gate_role or "entry").lower()
            return (role if role in ("entry", "exit") else "entry"), None

        if self.entry_reader_number is not None and event_number == self.entry_reader_number:
            return "entry", None
        if self.exit_reader_number is not None and event_number == self.exit_reader_number:
            return "exit", None
        return None, (
            f"считыватель number={event_number} не совпадает с настроенными "
            f"вход={self.entry_reader_number!r} / выход={self.exit_reader_number!r}"
        )

    def open_number_for_side(self, side: str) -> Optional[int]:
        side = (side or "").lower()
        if side == "entry":
            return self.entry_reader_number
        if side == "exit":
            return self.exit_reader_number
        return None

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "EquipmentItem":
        known = {f.name for f in cls.__dataclass_fields__.values()}  # type: ignore[attr-defined]
        filtered = {k: v for k, v in data.items() if k in known}
        if "id" not in filtered or not str(filtered.get("id", "")).strip():
            filtered["id"] = uuid.uuid4().hex[:8]
        if filtered.get("connection_mode") not in ("listen", "connect"):
            filtered["connection_mode"] = "listen"
        if filtered.get("gate_role") not in ("entry", "exit"):
            filtered["gate_role"] = "entry"
        filtered["entry_reader_number"] = _optional_int(filtered.get("entry_reader_number"))
        filtered["exit_reader_number"] = _optional_int(filtered.get("exit_reader_number"))
        return cls(**filtered)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

    def ws_listen_url(self) -> str:
        return f"ws://{self.listen_host}:{self.listen_port}"

    def ws_connect_url(self) -> str:
        # Эталон PERCo (ctl_websock): ws://host:port/tcp — не корень «/».
        return f"ws://{self.c01_host}:{self.c01_ws_port}/tcp"

    def label(self) -> str:
        mode = "слушать" if self.connection_mode == "listen" else f"→{self.c01_host}"
        return f"{self.name} [{mode}]"
