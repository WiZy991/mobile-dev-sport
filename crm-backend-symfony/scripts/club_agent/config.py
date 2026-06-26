"""Настройки агента клуба (JSON)."""
from __future__ import annotations

import json
import os
import sys
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any, Optional

from equipment import EquipmentItem


def normalize_crm_base_url(url: str) -> str:
    """
    База для API — только origin, без /admin и без /api/v1 (пути добавляет CrmClient).

    Частая ошибка: вставить из браузера https://домен/admin → 404 на gateway/*.
    """
    u = (url or "").strip().rstrip("/")
    if not u:
        return u
    lower = u.lower()
    while True:
        changed = False
        for suffix in ("/admin", "/api/v1", "/api"):
            if lower.endswith(suffix):
                u = u[: -len(suffix)].rstrip("/")
                lower = u.lower()
                changed = True
                break
        if not changed:
            break
    return u


def _config_dir() -> Path:
    if getattr(sys, "frozen", False):
        portable = Path(sys.executable).parent / "config"
        try:
            portable.mkdir(parents=True, exist_ok=True)
            test = portable / ".write_test"
            test.write_text("")
            test.unlink()
            return portable
        except OSError:
            pass
    appdata = Path(os.environ.get("APPDATA", Path.home())) / "FitnessClubAgent"
    appdata.mkdir(parents=True, exist_ok=True)
    return appdata


def config_file_path() -> Path:
    return _config_dir() / "agent_config.json"


@dataclass
class CameraConfig:
    """Камера Dahua (IVS tripwire) для контроля прохода вдвоём по одному QR.

    Линия (tripwire) настраивается в веб-интерфейсе камеры на линию входа, тип цели — Human,
    направление — внутрь. Агент считает пересечения в окне после открытия двери.
    """

    enabled: bool = False
    host: str = ""
    username: str = "admin"
    password: str = ""
    channel: int = 1
    https: bool = False
    verify_ssl: bool = False
    event_codes: str = "CrossLineDetection"
    inbound_direction: str = ""  # "", "LeftToRight" или "RightToLeft"
    require_human: bool = True
    tailgating_window_ms: int = 6000
    pre_roll_ms: int = 1000
    min_people: int = 2

    # Контроль входа без QR: непрерывно следим за линией входа и шлём тревогу, если за окно
    # прошло >= standalone_min_people человек, а прохода по QR не было (дверь подперта/взлом).
    standalone_enabled: bool = False
    standalone_window_ms: int = 4000
    standalone_min_people: int = 2
    standalone_cooldown_ms: int = 30000

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "CameraConfig":
        known = {f.name for f in cls.__dataclass_fields__.values()}  # type: ignore[attr-defined]
        filtered = {k: v for k, v in (data or {}).items() if k in known}
        return cls(**filtered)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

    def ready(self) -> bool:
        return bool(self.enabled and self.host.strip())


@dataclass
class AgentConfig:
    crm_base_url: str = "https://crm.worldcashfit.ru"
    gateway_token: str = ""
    device_id: str = "club-agent"
    crm_verify_ssl: bool = True

    equipment: list[EquipmentItem] = field(default_factory=list)
    camera: CameraConfig = field(default_factory=CameraConfig)

    # Устаревшие поля — синхронизируются с первым оборудованием при сохранении
    c01_listen_host: str = "0.0.0.0"
    c01_listen_port: int = 8765
    c01_password: str = ""
    c01_exdev_number: int = 0
    c01_exdev_direction: int = 0
    c01_open_type: str = "open once"
    c01_open_time_ms: int = 3000

    heartbeat_interval_sec: float = 30.0
    crm_poll_enabled: bool = True
    only_fitnessclub_qr: bool = True

    def __post_init__(self) -> None:
        self.crm_base_url = normalize_crm_base_url(self.crm_base_url)

    def enabled_equipment(self) -> list[EquipmentItem]:
        return [e for e in self.equipment if e.enabled]

    def get_equipment(self, equipment_id: str) -> Optional[EquipmentItem]:
        for e in self.equipment:
            if e.id == equipment_id:
                return e
        return None

    def ensure_default_equipment(self) -> None:
        if self.equipment:
            return
        self.equipment = [
            EquipmentItem(
                name="Турникет вход",
                listen_host=self.c01_listen_host,
                listen_port=self.c01_listen_port,
                password=self.c01_password,
                exdev_number=self.c01_exdev_number,
                exdev_direction=self.c01_exdev_direction,
                open_type=self.c01_open_type,
                open_time_ms=self.c01_open_time_ms,
                wait_command_time=3000,
                exdev_type="turnstyle",
                reader_type="Barcode-USB",
                access_mode="control",
            )
        ]

    def sync_legacy_c01_fields(self) -> None:
        """Первое оборудование → legacy-поля (обратная совместимость)."""
        if not self.equipment:
            return
        e = self.equipment[0]
        self.c01_listen_host = e.listen_host
        self.c01_listen_port = e.listen_port
        self.c01_password = e.password
        self.c01_exdev_number = e.exdev_number
        self.c01_exdev_direction = e.exdev_direction
        self.c01_open_type = e.open_type
        self.c01_open_time_ms = e.open_time_ms

    @classmethod
    def load(cls, path: Optional[Path] = None) -> "AgentConfig":
        p = path or config_file_path()
        if not p.exists():
            cfg = cls()
            cfg.ensure_default_equipment()
            cfg.save(p)
            return cfg
        data = json.loads(p.read_text(encoding="utf-8"))
        return cls.from_dict(data)

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "AgentConfig":
        known = {f.name for f in cls.__dataclass_fields__.values()}  # type: ignore[attr-defined]
        filtered = {
            k: v for k, v in data.items() if k in known and k not in ("equipment", "camera")
        }
        raw_eq = data.get("equipment")
        if isinstance(raw_eq, list) and raw_eq:
            filtered["equipment"] = [EquipmentItem.from_dict(x) for x in raw_eq if isinstance(x, dict)]
        raw_cam = data.get("camera")
        if isinstance(raw_cam, dict):
            filtered["camera"] = CameraConfig.from_dict(raw_cam)
        cfg = cls(**filtered)
        cfg.ensure_default_equipment()
        cfg.sync_legacy_c01_fields()
        return cfg

    def save(self, path: Optional[Path] = None) -> Path:
        self.sync_legacy_c01_fields()
        p = path or config_file_path()
        p.parent.mkdir(parents=True, exist_ok=True)
        payload = asdict(self)
        payload["equipment"] = [e.to_dict() for e in self.equipment]
        p.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        return p

    def crm_ready(self) -> bool:
        return bool(self.crm_base_url.strip() and self.gateway_token.strip())
