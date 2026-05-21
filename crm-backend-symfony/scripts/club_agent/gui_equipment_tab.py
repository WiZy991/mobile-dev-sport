"""Вкладка «Оборудование» — настройка контроллера PERCo-C01."""
from __future__ import annotations

import tkinter as tk
from tkinter import ttk
from typing import TYPE_CHECKING

from c01_protocol import EXDEV_TYPES, READER_TYPES
from net_util import get_lan_ip

if TYPE_CHECKING:
    from gui import AgentApp


def build_equipment_tab(app: "AgentApp", parent: ttk.Frame) -> None:
    paned = ttk.PanedWindow(parent, orient=tk.HORIZONTAL)
    paned.pack(fill=tk.BOTH, expand=True)

    left = ttk.Frame(paned, padding=4)
    paned.add(left, weight=1)
    ttk.Label(left, text="Оборудование (PERCo-C01)", font=("", 10, "bold")).pack(anchor=tk.W)
    app.lst_equipment = tk.Listbox(left, height=14, exportselection=False, width=28)
    app.lst_equipment.pack(fill=tk.BOTH, expand=True, pady=4)
    app.lst_equipment.bind("<<ListboxSelect>>", app._on_equipment_select)
    lf = ttk.Frame(left)
    lf.pack(fill=tk.X)
    ttk.Button(lf, text="+ Добавить", command=app._add_equipment).pack(side=tk.LEFT, padx=2)
    ttk.Button(lf, text="Удалить", command=app._delete_equipment).pack(side=tk.LEFT, padx=2)

    right_outer = ttk.Frame(paned, padding=0)
    paned.add(right_outer, weight=4)
    canvas = tk.Canvas(right_outer, highlightthickness=0)
    scroll = ttk.Scrollbar(right_outer, orient=tk.VERTICAL, command=canvas.yview)
    right = ttk.Frame(canvas, padding=8)
    right.bind("<Configure>", lambda e: canvas.configure(scrollregion=canvas.bbox("all")))
    canvas.create_window((0, 0), window=right, anchor=tk.NW)
    canvas.configure(yscrollcommand=scroll.set)
    canvas.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
    scroll.pack(side=tk.RIGHT, fill=tk.Y)

    def _row(parent: ttk.Frame, label: str, widget: tk.Widget, r: int) -> None:
        ttk.Label(parent, text=label).grid(row=r, column=0, sticky=tk.W, pady=2, padx=(0, 8))
        widget.grid(row=r, column=1, sticky=tk.EW, pady=2)

    conn = ttk.LabelFrame(right, text="1. Подключение WebSocket", padding=8)
    conn.pack(fill=tk.X, pady=(0, 8))
    conn.columnconfigure(1, weight=1)
    app.var_conn_mode = tk.StringVar(value="listen")
    modes = ttk.Frame(conn)
    modes.grid(row=0, column=0, columnspan=2, sticky=tk.W, pady=4)
    ttk.Radiobutton(
        modes,
        text="Сервер системы (слушать) — C01 подключается к этому ПК после net.server",
        variable=app.var_conn_mode,
        value="listen",
    ).pack(anchor=tk.W)
    ttk.Radiobutton(
        modes,
        text="К контроллеру — агент подключается к IP C01 (первичная настройка)",
        variable=app.var_conn_mode,
        value="connect",
    ).pack(anchor=tk.W)
    app.var_eq_host = tk.StringVar(value="0.0.0.0")
    app.var_eq_port = tk.StringVar(value="8765")
    app.var_c01_ip = tk.StringVar(value="192.168.1.201")
    app.var_c01_ws_port = tk.StringVar(value="80")
    _row(conn, "Слушать host", ttk.Entry(conn, textvariable=app.var_eq_host), 1)
    _row(conn, "Порт (для C01)", ttk.Entry(conn, textvariable=app.var_eq_port), 2)
    _row(conn, "IP контроллера", ttk.Entry(conn, textvariable=app.var_c01_ip), 3)
    _row(conn, "Порт WS на C01", ttk.Entry(conn, textvariable=app.var_c01_ws_port), 4)
    ttk.Label(
        conn,
        text="Если в журнале «HTTP 200» при подключении — это не WebSocket (часто порт 80 = веб-страница по пути /). Агент подключается к пути /tcp, как в примере PERCo ctl_websock. Укажите порт WS из документации C01 или перейдите в режим «Слушать» (8765) после записи net.server.",
        foreground="gray",
        wraplength=560,
    ).grid(row=5, column=0, columnspan=2, sticky=tk.W, pady=(4, 0))

    auth = ttk.LabelFrame(right, text="2. Пароль доступа", padding=8)
    auth.pack(fill=tk.X, pady=(0, 8))
    auth.columnconfigure(1, weight=1)
    app.var_eq_pwd = tk.StringVar()
    _row(auth, "Пароль", ttk.Entry(auth, textvariable=app.var_eq_pwd, show="*"), 0)
    ttk.Label(
        auth,
        text="При подключении: md5(salt+пароль). Тот же пароль можно записать в net.password на контроллере.",
        foreground="gray",
        wraplength=560,
    ).grid(row=1, column=0, columnspan=2, sticky=tk.W, pady=4)

    net = ttk.LabelFrame(right, text="3. Сеть (net)", padding=8)
    net.pack(fill=tk.X, pady=(0, 8))
    net.columnconfigure(1, weight=1)
    app.var_eq_name = tk.StringVar()
    app.var_eq_enabled = tk.BooleanVar(value=True)
    app.var_net_ip = tk.StringVar()
    app.var_net_mask = tk.StringVar(value="255.255.255.0")
    app.var_net_gw = tk.StringVar()
    app.var_net_server = tk.StringVar()
    _row(net, "Название", ttk.Entry(net, textvariable=app.var_eq_name), 0)
    ttk.Checkbutton(net, text="Устройство включено", variable=app.var_eq_enabled).grid(
        row=1, column=1, sticky=tk.W
    )
    _row(net, "net.ip (C01)", ttk.Entry(net, textvariable=app.var_net_ip), 2)
    _row(net, "net.mask", ttk.Entry(net, textvariable=app.var_net_mask), 3)
    _row(net, "net.gateway", ttk.Entry(net, textvariable=app.var_net_gw), 4)
    _row(net, "net.server (этот ПК)", ttk.Entry(net, textvariable=app.var_net_server), 5)
    ttk.Label(
        net,
        text="Если C01 не подключается: в веб-интерфейсе PERCo в «Адрес сервера» укажите IP:порт, например 192.168.0.63:8765 (если поле принимает двоеточие). Иначе контроллер может ходить на порт 80, а агент слушает 8765.",
        foreground="gray",
        wraplength=560,
    ).grid(row=6, column=0, columnspan=2, sticky=tk.W, pady=(0, 4))
    nf = ttk.Frame(net)
    nf.grid(row=7, column=1, sticky=tk.W, pady=4)
    ttk.Button(nf, text=f"Подставить IP ПК ({get_lan_ip()})", command=app._fill_lan_server).pack(
        side=tk.LEFT, padx=(0, 8)
    )
    ttk.Button(nf, text="Прочитать net", command=app._proto_read_net).pack(side=tk.LEFT, padx=4)
    ttk.Button(nf, text="Записать net", command=app._proto_write_net).pack(side=tk.LEFT, padx=4)

    reader = ttk.LabelFrame(right, text="4. Считыватель (reader)", padding=8)
    reader.pack(fill=tk.X, pady=(0, 8))
    reader.columnconfigure(1, weight=1)
    app.var_reader_type = tk.StringVar(value="Barcode-USB")
    app.var_reader_port = tk.StringVar(value="0")
    _row(reader, "reader.type", ttk.Combobox(reader, textvariable=app.var_reader_type, values=READER_TYPES), 0)
    _row(reader, "reader.port", ttk.Entry(reader, textvariable=app.var_reader_port, width=8), 1)

    exdev = ttk.LabelFrame(right, text="5. Турникет (exdev) и режим доступа", padding=8)
    exdev.pack(fill=tk.X, pady=(0, 8))
    exdev.columnconfigure(1, weight=1)
    app.var_exdev_type = tk.StringVar(value="turnstyle")
    app.var_eq_exdev_n = tk.StringVar(value="0")
    app.var_eq_exdev_d = tk.StringVar(value="0")
    app.var_wait_cmd = tk.StringVar(value="3000")
    app.var_access_mode = tk.StringVar(value="control")
    app.var_open_time = tk.StringVar(value="3000")
    _row(exdev, "exdev.type", ttk.Combobox(exdev, textvariable=app.var_exdev_type, values=EXDEV_TYPES), 0)
    _row(exdev, "ИУ number", ttk.Entry(exdev, textvariable=app.var_eq_exdev_n, width=8), 1)
    _row(exdev, "ИУ direction", ttk.Entry(exdev, textvariable=app.var_eq_exdev_d, width=8), 2)
    _row(exdev, "wait_command_time (мс)", ttk.Entry(exdev, textvariable=app.var_wait_cmd), 3)
    _row(
        exdev,
        "РКД access_mode",
        ttk.Combobox(exdev, textvariable=app.var_access_mode, values=("control", "open"), width=12),
        4,
    )
    _row(exdev, "open_time (мс)", ttk.Entry(exdev, textvariable=app.var_open_time), 5)

    ctrl = ttk.LabelFrame(right, text="6. Команды контроллеру", padding=8)
    ctrl.pack(fill=tk.X, pady=(0, 8))
    cf = ttk.Frame(ctrl)
    cf.pack(fill=tk.X)
    ttk.Button(cf, text="Мастер: net+reader+exdev+control", command=app._proto_master).pack(
        side=tk.LEFT, padx=(0, 6), pady=2
    )
    ttk.Button(cf, text="Состояние (get state)", command=app._proto_read_state).pack(side=tk.LEFT, padx=4, pady=2)
    ttk.Button(cf, text="Открыть", command=app._open_door).pack(side=tk.LEFT, padx=4, pady=2)
    ttk.Button(cf, text="Закрыть", command=app._proto_close).pack(side=tk.LEFT, padx=4, pady=2)
    ttk.Button(ctrl, text="Применить поля → список", command=app._apply_equipment_form).pack(
        anchor=tk.W, pady=6
    )

    app.lbl_eq_hint = ttk.Label(right, text="", foreground="#0066aa", wraplength=580)
    app.lbl_eq_hint.pack(anchor=tk.W, pady=4)

    test = ttk.LabelFrame(right, text="7. Проверка без железа (симулятор)", padding=8)
    test.pack(fill=tk.X, pady=8)
    ttk.Label(
        test,
        text="Режим «слушать» + симулятор. Либо «к контроллеру» — мастер настройки на реальный IP.",
        wraplength=560,
    ).pack(anchor=tk.W)
    tf = ttk.Frame(test)
    tf.pack(fill=tk.X, pady=6)
    app.btn_sim = ttk.Button(tf, text="Подключить симулятор", command=app._toggle_simulator)
    app.btn_sim.pack(side=tk.LEFT, padx=(0, 8))
    ttk.Button(tf, text="Тестовая карта", command=app._sim_send_card).pack(side=tk.LEFT, padx=4)
    app.lbl_eq_status = ttk.Label(test, text="Статус: —")
    app.lbl_eq_status.pack(anchor=tk.W)

    app.var_eq_notes = tk.StringVar()
