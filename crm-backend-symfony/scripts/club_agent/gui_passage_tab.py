"""Вкладка «Проход» — QR → CRM → дверь."""
from __future__ import annotations

import time
import tkinter as tk
from tkinter import ttk
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from gui import AgentApp


def build_passage_tab(app: "AgentApp", parent: ttk.Frame) -> None:
    ttk.Label(
        parent,
        text="Цепочка: скан QR → проверка в CRM (клиент + абонемент) → открытие турникета.\n"
        "Все запросы и ответы — на вкладке «Журнал».",
        wraplength=700,
        foreground="#333",
    ).pack(anchor=tk.W, pady=(0, 12))

    steps = ttk.LabelFrame(parent, text="Порядок проверки", padding=10)
    steps.pack(fill=tk.X, pady=(0, 10))
    for i, text in enumerate(
        [
            "1. Вкладка CRM — URL и gateway_token → «Сохранить всё» → «Проверить CRM» (HTTP 200).",
            "2. Оборудование — режим «Слушать», host 0.0.0.0, порт 8765, net.server = IP ПК.",
            "3. «Запустить агент» → C01 [ON] (или «Подключить симулятор» для теста без железа).",
            "4. QR с телефона (< 15 сек) или кнопка ниже «Проверить проход».",
        ],
        start=1,
    ):
        ttk.Label(steps, text=text, wraplength=680).pack(anchor=tk.W, pady=2)

    qr_frame = ttk.LabelFrame(parent, text="QR с приложения", padding=10)
    qr_frame.pack(fill=tk.X, pady=8)
    app.entry_qr_passage = ttk.Entry(qr_frame, width=85)
    app.entry_qr_passage.pack(fill=tk.X, pady=4)
    app.entry_qr_passage.insert(0, "FITNESSCLUB:ENTRY:user-1:0")

    bf = ttk.Frame(qr_frame)
    bf.pack(anchor=tk.W, pady=6)
    ttk.Button(bf, text="Обновить timestamp", command=app._qr_refresh_ts_passage).pack(side=tk.LEFT, padx=(0, 8))
    ttk.Button(bf, text="Проверить проход", command=app._run_passage_test).pack(side=tk.LEFT, padx=4)
    ttk.Button(bf, text="Только открыть дверь", command=app._open_door).pack(side=tk.LEFT, padx=4)

    app.lbl_passage_result = ttk.Label(parent, text="", wraplength=700, font=("", 10))
    app.lbl_passage_result.pack(anchor=tk.W, pady=8)
