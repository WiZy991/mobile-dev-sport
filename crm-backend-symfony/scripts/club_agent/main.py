#!/usr/bin/env python3
"""FitnessClub — агент клуба (GUI + C01 WebSocket + CRM)."""
from __future__ import annotations

import logging
import sys
from pathlib import Path

# PyInstaller onefile: модули лежат рядом во временной папке
if getattr(sys, "frozen", False):
    sys.path.insert(0, str(Path(sys._MEIPASS)))  # type: ignore[attr-defined]
else:
    sys.path.insert(0, str(Path(__file__).resolve().parent))

from gui import run_app


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(name)s %(message)s")
    run_app()


if __name__ == "__main__":
    main()
