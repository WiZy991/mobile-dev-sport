#!/usr/bin/env python3
"""CLI: диагностика сырого Dahua attach-потока (агент должен быть остановлен)."""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from config import config_file_path  # noqa: E402
from dahua_camera import DahuaCameraListener  # noqa: E402


def _log(level: str, msg: str) -> None:
    print(f"[{level.upper():5}] {msg}")


def main() -> int:
    cfg_path = config_file_path()
    if not cfg_path.exists():
        print(f"Нет конфига: {cfg_path}")
        print("Запустите агент, заполните вкладку «Камера» и нажмите «Сохранить всё».")
        return 1
    cam = json.loads(cfg_path.read_text(encoding="utf-8"))["camera"]
    channels = [int(cam.get("channel") or 1)]
    if 0 not in channels:
        channels.append(0)
    duration = 20.0
    print("Остановите FitnessClub Agent перед диагностикой.")
    print(f"Host: {cam['host']}, codes={cam.get('event_codes', 'CrossLineDetection')}")
    print(f"Слушаем {duration:.0f} с на канал — пройдите через линию IVS.\n")
    for ch in channels:
        print(f"========== channel={ch} ==========")
        probe = DahuaCameraListener(
            host=cam["host"],
            username=cam["username"],
            password=cam["password"],
            channel=ch,
            event_codes=cam.get("event_codes") or "CrossLineDetection",
            require_human=False,
            on_log=_log,
        )
        stats = probe.probe_event_stream(duration)
        print(json.dumps({k: v for k, v in stats.items() if k != "samples"}, ensure_ascii=False, indent=2))
        for s in stats.get("samples") or []:
            print(f"  sample: {s}")
        print()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
