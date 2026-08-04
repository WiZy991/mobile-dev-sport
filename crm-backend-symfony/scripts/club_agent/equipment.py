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

    # Роль точки: fallback, если пары number/direction для вход/выход не заданы
    gate_role: str = "entry"

    # Идентификация стороны в событии C01 (card.number + card.direction).
    # 1 ИУ + 2 считывателя: обычно один number (ИУ) и разные direction (0=вход, 1=выход).
    # Пустое поле не сравнивается. Сторона настроена, если задан number и/или direction.
    entry_reader_number: Optional[int] = None
    entry_reader_direction: Optional[int] = None
    exit_reader_number: Optional[int] = None
    exit_reader_direction: Optional[int] = None

    notes: str = ""

    def _side_configured(self, side: str) -> bool:
        if side == "entry":
            return self.entry_reader_number is not None or self.entry_reader_direction is not None
        if side == "exit":
            return self.exit_reader_number is not None or self.exit_reader_direction is not None
        return False

    def readers_configured(self) -> bool:
        return self._side_configured("entry") or self._side_configured("exit")

    def _side_matches(self, side: str, event_number: int, event_direction: int) -> bool:
        if not self._side_configured(side):
            return False
        if side == "entry":
            n, d = self.entry_reader_number, self.entry_reader_direction
        else:
            n, d = self.exit_reader_number, self.exit_reader_direction
        if n is not None and int(event_number) != int(n):
            return False
        if d is not None and int(event_direction) != int(d):
            return False
        return True

    def resolve_passage(
        self,
        event_number: int,
        event_direction: int = 0,
    ) -> tuple[Optional[str], Optional[str]]:
        """
        (passage, error).
        passage: entry | exit; error — текст отказа, если считыватель неизвестен.
        """
        if not self.readers_configured():
            role = (self.gate_role or "entry").lower()
            return (role if role in ("entry", "exit") else "entry"), None

        entry_ok = self._side_matches("entry", event_number, event_direction)
        exit_ok = self._side_matches("exit", event_number, event_direction)
        if entry_ok and not exit_ok:
            return "entry", None
        if exit_ok and not entry_ok:
            return "exit", None
        if entry_ok and exit_ok:
            return None, (
                f"конфликт настроек: number={event_number} direction={event_direction} "
                "подходит и под вход, и под выход — уточните number/direction"
            )
        return None, (
            f"считыватель number={event_number} direction={event_direction} не совпадает с настроенными "
            f"вход(n={self.entry_reader_number!r},d={self.entry_reader_direction!r}) / "
            f"выход(n={self.exit_reader_number!r},d={self.exit_reader_direction!r})"
        )

    def open_number_for_side(self, side: str) -> Optional[int]:
        side = (side or "").lower()
        if side == "entry":
            return self.entry_reader_number
        if side == "exit":
            return self.exit_reader_number
        return None

    def open_direction_for_side(self, side: str) -> Optional[int]:
        side = (side or "").lower()
        if side == "entry":
            return self.entry_reader_direction
        if side == "exit":
            return self.exit_reader_direction
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
        for key in (
            "entry_reader_number",
            "entry_reader_direction",
            "exit_reader_number",
            "exit_reader_direction",
        ):
            filtered[key] = _optional_int(filtered.get(key))
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
