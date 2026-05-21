"""Окно агента клуба (tkinter)."""
from __future__ import annotations

import queue
import time
import tkinter as tk
import uuid
from datetime import datetime
from tkinter import messagebox, scrolledtext, ttk
from typing import Callable, Optional
import json

from agent_core import ClubAgent
from c01_configurator import apply_club_setup, read_net, read_state, write_net
from c01_protocol import exdev_close
from c01_simulator import C01Simulator
from config import AgentConfig, config_file_path, normalize_crm_base_url
from equipment import EquipmentItem
from gui_clipboard import install_edit_bindings
from gui_equipment_tab import build_equipment_tab
from gui_passage_tab import build_passage_tab
from gui_protocol import run_in_thread, run_with_session
from net_util import get_lan_ip, tcp_refused_c01_hint


def _format_ws_connect_error(exc: BaseException) -> str:
    """Пояснение к частой ошибке: на порту 80 отвечает HTTP, а не WebSocket C01."""
    msg = str(exc)
    if "WebSocket" in msg and "200" in msg:
        return (
            msg
            + "\n\n——————————————————————————————————\n"
            "По этому адресу приходит обычный HTTP (часто порт 80 — веб-страница), "
            "а не ответ WebSocket C01 (нужен код 101 Switching Protocols).\n\n"
            "Что сделать:\n"
            "• Укажите порт WebSocket из документации PERCo для вашей прошивки; или\n"
            "• Режим «Слушать», порт 8765 на ПК → «Записать net» (net.server = IP этого ПК) → "
            "«Сохранить всё» → перезапуск агента — контроллер сам подключится к ПК."
        )
    hint = tcp_refused_c01_hint(exc)
    if hint:
        return msg + "\n\n——————————————————————————————————\n" + hint
    return msg


class AgentApp(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title("FitnessClub — агент клуба")
        self.minsize(800, 560)
        self.geometry("960x680")

        self.cfg = AgentConfig.load()
        self.agent: Optional[ClubAgent] = None
        self._simulators: dict[str, C01Simulator] = {}
        self._log_queue: queue.Queue[tuple[str, str]] = queue.Queue()
        self._running = False
        self._selected_eq_id: Optional[str] = None

        self._build_ui()
        self._refresh_equipment_list()
        self._load_crm_fields()
        self.after(150, self._drain_log_queue)
        self.after(1000, self._refresh_status)
        self.protocol("WM_DELETE_WINDOW", self._on_close)

    def _build_ui(self) -> None:
        top = ttk.Frame(self, padding=8)
        top.pack(fill=tk.X)
        self.lbl_crm = ttk.Label(top, text="CRM: —")
        self.lbl_crm.pack(side=tk.LEFT, padx=(0, 16))
        self.lbl_c01 = ttk.Label(top, text="C01: —")
        self.lbl_c01.pack(side=tk.LEFT, padx=(0, 16))
        self.lbl_lan = ttk.Label(top, text=f"IP ПК для C01: {get_lan_ip()}", foreground="#0066aa")
        self.lbl_lan.pack(side=tk.LEFT)

        btns = ttk.Frame(self, padding=(8, 0, 8, 8))
        btns.pack(fill=tk.X)
        self.btn_start = ttk.Button(btns, text="Запустить агент", command=self._toggle_agent)
        self.btn_start.pack(side=tk.LEFT, padx=(0, 6))
        ttk.Button(btns, text="Проверить CRM", command=self._test_crm).pack(side=tk.LEFT, padx=6)
        ttk.Button(btns, text="Очистить лог", command=self._clear_log).pack(side=tk.LEFT, padx=6)

        nb = ttk.Notebook(self, padding=8)
        nb.pack(fill=tk.BOTH, expand=True)

        tab_main = ttk.Frame(nb)
        nb.add(tab_main, text="Журнал")
        ttk.Label(
            tab_main,
            text="Запросы/ответы CRM (HTTP JSON) и команды C01 при скане QR.",
            foreground="gray",
        ).pack(anchor=tk.W, padx=4, pady=(4, 0))
        self.log = scrolledtext.ScrolledText(tab_main, wrap=tk.WORD, font=("Consolas", 10))
        self.log.pack(fill=tk.BOTH, expand=True, padx=4, pady=4)
        self.log.configure(state=tk.DISABLED)

        tab_pass = ttk.Frame(nb, padding=8)
        nb.add(tab_pass, text="Проход")
        build_passage_tab(self, tab_pass)

        tab_crm = ttk.Frame(nb, padding=8)
        nb.add(tab_crm, text="CRM")
        self._build_crm_tab(tab_crm)

        tab_eq = ttk.Frame(nb, padding=8)
        nb.add(tab_eq, text="Оборудование")
        build_equipment_tab(self, tab_eq)

        install_edit_bindings(self)

        self.entry_qr = self.entry_qr_passage

        bottom = ttk.Frame(self, padding=8)
        bottom.pack(fill=tk.X)
        ttk.Button(bottom, text="Сохранить всё", command=self._save_all).pack(side=tk.LEFT)
        ttk.Label(bottom, text=f"Конфиг: {config_file_path()}", foreground="gray").pack(
            side=tk.LEFT, padx=12
        )

    def _build_crm_tab(self, parent: ttk.Frame) -> None:
        grid = ttk.Frame(parent)
        grid.pack(fill=tk.BOTH, expand=True)
        grid.columnconfigure(1, weight=1)
        self.var_crm_url = tk.StringVar()
        self.var_token = tk.StringVar()
        self.var_device_id = tk.StringVar()
        self.var_ssl = tk.BooleanVar()
        self.var_only_fc = tk.BooleanVar()
        rows = [
            ("URL CRM", ttk.Entry(grid, textvariable=self.var_crm_url, width=60)),
            ("Gateway token", ttk.Entry(grid, textvariable=self.var_token, width=60, show="*")),
            ("Device ID (логи)", ttk.Entry(grid, textvariable=self.var_device_id, width=40)),
        ]
        for i, (label, w) in enumerate(rows):
            ttk.Label(grid, text=label).grid(row=i, column=0, sticky=tk.W, pady=4, padx=(0, 8))
            w.grid(row=i, column=1, sticky=tk.EW, pady=4)
        ttk.Checkbutton(grid, text="Проверять SSL CRM", variable=self.var_ssl).grid(
            row=3, column=1, sticky=tk.W, pady=4
        )
        ttk.Checkbutton(grid, text="Только QR FITNESSCLUB:", variable=self.var_only_fc).grid(
            row=4, column=1, sticky=tk.W, pady=4
        )
        ttk.Label(
            grid,
            text="Рекомендуется включить. Если в журнале при скане только цифры (без «FITNESSCLUB:») — "
            "считыватель отрезает QR; CRM и приложение работают по полной строке.",
            foreground="gray",
            wraplength=560,
            font=("", 8),
        ).grid(row=5, column=1, sticky=tk.W, pady=(0, 4))

    def _load_crm_fields(self) -> None:
        c = self.cfg
        self.var_crm_url.set(c.crm_base_url)
        self.var_token.set(c.gateway_token)
        self.var_device_id.set(c.device_id)
        self.var_ssl.set(c.crm_verify_ssl)
        self.var_only_fc.set(c.only_fitnessclub_qr)

    def _crm_from_fields(self) -> None:
        raw_url = self.var_crm_url.get().strip()
        norm_url = normalize_crm_base_url(raw_url)
        if norm_url != raw_url:
            self.var_crm_url.set(norm_url)
        self.cfg.crm_base_url = norm_url
        self.cfg.gateway_token = self.var_token.get().strip()
        self.cfg.device_id = self.var_device_id.get().strip() or "club-agent"
        self.cfg.crm_verify_ssl = bool(self.var_ssl.get())
        self.cfg.only_fitnessclub_qr = bool(self.var_only_fc.get())

    def _refresh_equipment_list(self) -> None:
        self.lst_equipment.delete(0, tk.END)
        for eq in self.cfg.equipment:
            mark = "●" if eq.enabled else "○"
            conn = ""
            if self.agent and self._running:
                ok = self.agent.equipment_connected().get(eq.id, False)
                conn = " [ON]" if ok else " [—]"
            self.lst_equipment.insert(tk.END, f"{mark} {eq.label()}{conn}")
        if self.cfg.equipment and self._selected_eq_id is None:
            self._selected_eq_id = self.cfg.equipment[0].id
            self.lst_equipment.selection_set(0)
            self._load_equipment_form(self.cfg.equipment[0])

    def _on_equipment_select(self, _event: object = None) -> None:
        sel = self.lst_equipment.curselection()
        if not sel:
            return
        idx = int(sel[0])
        if 0 <= idx < len(self.cfg.equipment):
            eq = self.cfg.equipment[idx]
            self._selected_eq_id = eq.id
            self._load_equipment_form(eq)
            if self.agent:
                self.agent.set_selected_equipment(eq.id)

    def _load_equipment_form(self, eq: EquipmentItem) -> None:
        lan = get_lan_ip()
        self.var_eq_name.set(eq.name)
        self.var_eq_enabled.set(eq.enabled)
        self.var_conn_mode.set(eq.connection_mode)
        self.var_eq_host.set(eq.listen_host)
        self.var_eq_port.set(str(eq.listen_port))
        self.var_c01_ip.set(eq.c01_host)
        self.var_c01_ws_port.set(str(eq.c01_ws_port))
        self.var_eq_pwd.set(eq.password)
        self.var_net_ip.set(eq.net_ip)
        self.var_net_mask.set(eq.net_mask)
        self.var_net_gw.set(eq.net_gateway)
        self.var_net_server.set(eq.net_server or lan)
        self.var_reader_type.set(eq.reader_type)
        self.var_reader_port.set(str(eq.reader_port))
        self.var_exdev_type.set(eq.exdev_type)
        self.var_eq_exdev_n.set(str(eq.exdev_number))
        self.var_eq_exdev_d.set(str(eq.exdev_direction))
        self.var_wait_cmd.set(str(eq.wait_command_time))
        self.var_access_mode.set(eq.access_mode)
        self.var_open_time.set(str(eq.open_time_ms))
        self.var_gate_role.set(getattr(eq, "gate_role", "entry") or "entry")
        self.var_relay_after.set(bool(eq.relay_after_grant) and not bool(eq.relay_use_cross_reference))
        self.var_relay_out_n.set(str(eq.relay_output_number))
        self.var_relay_pulse.set(str(eq.relay_pulse_ms))
        self.var_relay_cross.set(bool(eq.relay_use_cross_reference))
        self.var_relay_cross_n.set(str(eq.relay_cross_number))
        self.var_eq_notes.set(eq.notes)
        mode_txt = (
            f"Слушать: ws://{lan}:{eq.listen_port}  |  К C01: {eq.ws_connect_url()}"
        )
        self.lbl_eq_hint.configure(
            text=(
                f"{mode_txt}\n"
                "Первичная настройка: режим «к контроллеру» → Мастер → затем «слушать» и перезапуск агента."
            )
        )

    def _form_to_equipment(self) -> EquipmentItem:
        return EquipmentItem(
            id=self._selected_eq_id or uuid.uuid4().hex[:8],
            name=self.var_eq_name.get().strip() or "Турникет",
            enabled=bool(self.var_eq_enabled.get()),
            connection_mode=self.var_conn_mode.get(),
            listen_host=self.var_eq_host.get().strip() or "0.0.0.0",
            listen_port=int(self.var_eq_port.get().strip()),
            c01_host=self.var_c01_ip.get().strip() or "192.168.1.201",
            c01_ws_port=int(self.var_c01_ws_port.get().strip() or "80"),
            password=self.var_eq_pwd.get(),
            net_ip=self.var_net_ip.get().strip(),
            net_mask=self.var_net_mask.get().strip() or "255.255.255.0",
            net_gateway=self.var_net_gw.get().strip(),
            net_server=self.var_net_server.get().strip(),
            reader_type=self.var_reader_type.get().strip() or "Barcode-USB",
            reader_port=int(self.var_reader_port.get().strip() or "0"),
            exdev_number=int(self.var_eq_exdev_n.get().strip()),
            exdev_direction=int(self.var_eq_exdev_d.get().strip()),
            exdev_type=self.var_exdev_type.get().strip() or "turnstyle",
            wait_command_time=int(self.var_wait_cmd.get().strip()),
            access_mode=self.var_access_mode.get().strip() or "control",
            open_time_ms=int(self.var_open_time.get().strip()),
            gate_role=(
                gr
                if (gr := self.var_gate_role.get().strip().lower()) in ("entry", "exit")
                else "entry"
            ),
            relay_after_grant=bool(self.var_relay_after.get()) and not bool(self.var_relay_cross.get()),
            relay_output_number=int(self.var_relay_out_n.get().strip() or "0"),
            relay_pulse_ms=int(self.var_relay_pulse.get().strip() or "0"),
            relay_use_cross_reference=bool(self.var_relay_cross.get()),
            relay_cross_number=int(self.var_relay_cross_n.get().strip() or "0"),
            notes=self.var_eq_notes.get().strip(),
            open_type="open once",
        )

    def _fill_lan_server(self) -> None:
        self.var_net_server.set(get_lan_ip())

    def _proto_worker(self, fn: Callable[[], None]) -> None:
        run_in_thread(fn, on_done=lambda: self.after(0, lambda: None))

    def _get_eq_for_proto(self) -> Optional[EquipmentItem]:
        try:
            return self._form_to_equipment()
        except ValueError as e:
            messagebox.showerror("Ошибка", str(e))
            return None

    def _proto_read_net(self) -> None:
        def work() -> None:
            eq = self._get_eq_for_proto()
            if not eq:
                return
            try:
                data = run_with_session(
                    eq,
                    read_net,
                    agent=self.agent if self._running else None,
                    on_log=self._enqueue_log,
                )
                net = data.get("net", data)
                self.after(
                    0,
                    lambda: messagebox.showinfo(
                        "net",
                        json.dumps(net, ensure_ascii=False, indent=2)[:2000],
                    ),
                )
            except Exception as e:
                self.after(0, lambda err=e: messagebox.showerror("Сеть C01 (net)", _format_ws_connect_error(err)))

        self._proto_worker(work)

    def _proto_write_net(self) -> None:
        def work() -> None:
            eq = self._get_eq_for_proto()
            if not eq:
                return
            try:
                run_with_session(
                    eq,
                    lambda s: write_net(s, eq, get_lan_ip()),
                    agent=self.agent if self._running else None,
                    on_log=self._enqueue_log,
                )
                self.after(0, lambda: messagebox.showinfo("net", "Записано (answer net ok)"))
            except Exception as e:
                self.after(0, lambda err=e: messagebox.showerror("Сеть C01 (net)", _format_ws_connect_error(err)))

        self._proto_worker(work)

    def _proto_read_state(self) -> None:
        def work() -> None:
            eq = self._get_eq_for_proto()
            if not eq:
                return
            try:
                data = run_with_session(
                    eq,
                    read_state,
                    agent=self.agent if self._running else None,
                    on_log=self._enqueue_log,
                )
                self.after(
                    0,
                    lambda: messagebox.showinfo(
                        "state",
                        json.dumps(data.get("state", data), ensure_ascii=False, indent=2)[:2000],
                    ),
                )
            except Exception as e:
                self.after(0, lambda err=e: messagebox.showerror("Состояние C01 (state)", _format_ws_connect_error(err)))

        self._proto_worker(work)

    def _proto_master(self) -> None:
        def work() -> None:
            eq = self._get_eq_for_proto()
            if not eq:
                return
            try:
                self._apply_equipment_form_silent(eq)

                async def setup(session: object) -> None:
                    await apply_club_setup(session, eq, on_log=self._enqueue_log)  # type: ignore[arg-type]

                run_with_session(
                    eq,
                    setup,
                    agent=self.agent if self._running else None,
                    on_log=self._enqueue_log,
                )
                self.after(
                    0,
                    lambda: messagebox.showinfo(
                        "Мастер",
                        "Настройка контроллера выполнена.\n"
                        "Переключите режим на «слушать», укажите net.server на ПК, запустите агент.",
                    ),
                )
            except Exception as e:
                self.after(0, lambda err=e: messagebox.showerror("Мастер настройки C01", _format_ws_connect_error(err)))

        self._proto_worker(work)

    def _proto_close(self) -> None:
        if not self.agent or not self._running:
            messagebox.showwarning("Агент", "Запустите агент и подключите C01.")
            return
        eq = self._get_eq_for_proto()
        if not eq:
            return

        async def close_cmd(session: object) -> None:
            await session.send_json(  # type: ignore[attr-defined]
                exdev_close(eq.exdev_number, eq.exdev_direction)
            )

        def work() -> None:
            try:
                run_with_session(
                    eq,
                    close_cmd,
                    agent=self.agent,
                    on_log=self._enqueue_log,
                )
                self._append_log("info", "control exdev close отправлен")
            except Exception as e:
                self.after(0, lambda: messagebox.showerror("close", str(e)))

        self._proto_worker(work)

    def _apply_equipment_form_silent(self, updated: EquipmentItem) -> None:
        for i, eq in enumerate(self.cfg.equipment):
            if eq.id == self._selected_eq_id:
                self.cfg.equipment[i] = updated
                self._selected_eq_id = updated.id
                break

    def _apply_equipment_form(self) -> None:
        if not self._selected_eq_id:
            messagebox.showwarning("Оборудование", "Выберите устройство в списке.")
            return
        try:
            updated = self._form_to_equipment()
        except ValueError:
            messagebox.showerror("Ошибка", "Порт и номера ИУ — целые числа.")
            return
        for i, eq in enumerate(self.cfg.equipment):
            if eq.id == self._selected_eq_id:
                self.cfg.equipment[i] = updated
                self._selected_eq_id = updated.id
                break
        self._refresh_equipment_list()
        self._append_log("info", f"Оборудование обновлено: {updated.name}")

    def _add_equipment(self) -> None:
        port = 8765
        used = {e.listen_port for e in self.cfg.equipment}
        while port in used:
            port += 1
        eq = EquipmentItem(name=f"Турникет {len(self.cfg.equipment) + 1}", listen_port=port)
        self.cfg.equipment.append(eq)
        self._selected_eq_id = eq.id
        self._refresh_equipment_list()
        self.lst_equipment.selection_clear(0, tk.END)
        self.lst_equipment.selection_set(tk.END)
        self._load_equipment_form(eq)

    def _delete_equipment(self) -> None:
        if not self._selected_eq_id or len(self.cfg.equipment) <= 1:
            messagebox.showwarning("Оборудование", "Нельзя удалить единственное устройство.")
            return
        self.cfg.equipment = [e for e in self.cfg.equipment if e.id != self._selected_eq_id]
        self._selected_eq_id = self.cfg.equipment[0].id
        self._refresh_equipment_list()
        self._load_equipment_form(self.cfg.equipment[0])

    def _get_selected_equipment(self) -> Optional[EquipmentItem]:
        if not self._selected_eq_id:
            return None
        return self.cfg.get_equipment(self._selected_eq_id)

    def _save_all(self) -> None:
        try:
            self._apply_equipment_form()
        except Exception:
            pass
        self._crm_from_fields()
        try:
            self.cfg.save()
            if self.agent:
                self.agent.cfg = self.cfg
            messagebox.showinfo("Сохранено", "Настройки CRM и оборудования записаны.")
            self._append_log("info", "Конфиг сохранён")
        except Exception as e:
            messagebox.showerror("Ошибка", str(e))

    def _toggle_agent(self) -> None:
        if self._running:
            self._stop_simulators()
            if self.agent:
                self.agent.stop()
            self._running = False
            self.btn_start.configure(text="Запустить агент")
            self._append_log("info", "Агент остановлен")
            self._refresh_status_labels(False, {})
            self._refresh_equipment_list()
            return
        self._crm_from_fields()
        try:
            self._apply_equipment_form()
        except Exception:
            pass
        if not self.cfg.enabled_equipment():
            messagebox.showerror("Оборудование", "Включите хотя бы одно устройство.")
            return
        self.cfg.save()
        self.agent = ClubAgent(self.cfg, self._enqueue_log, on_status=self._on_agent_status)
        if self._selected_eq_id:
            self.agent.set_selected_equipment(self._selected_eq_id)
        self.agent.start()
        self._running = True
        self.btn_start.configure(text="Остановить агент")
        self._append_log("info", "Агент запущен — подключите C01 или симулятор")
        self._refresh_equipment_list()

    def _on_agent_status(self, crm_ok: bool, eq_states: dict[str, bool]) -> None:
        self.after(0, lambda: self._refresh_status_labels(crm_ok, eq_states))

    def _refresh_status_labels(self, crm_ok: bool, eq_states: dict[str, bool]) -> None:
        self.lbl_crm.configure(text=f"CRM: {'онлайн' if crm_ok else 'офлайн'}")
        n = sum(1 for v in eq_states.values() if v)
        total = len(eq_states)
        self.lbl_c01.configure(text=f"C01: {n}/{total} подключено")
        eq = self._get_selected_equipment()
        if eq:
            on = eq_states.get(eq.id, False)
            self.lbl_eq_status.configure(
                text=f"Статус «{eq.name}»: {'подключён' if on else 'ожидание WebSocket'}"
            )

    def _refresh_status(self) -> None:
        if self.agent and self._running:
            self._refresh_status_labels(self.agent.crm_online, self.agent.equipment_connected())
            self._refresh_equipment_list()
        self.after(1000, self._refresh_status)

    def _toggle_simulator(self) -> None:
        eq = self._get_selected_equipment()
        if not eq:
            messagebox.showwarning("Симулятор", "Выберите оборудование.")
            return
        if not self._running:
            messagebox.showwarning("Агент", "Сначала запустите агент.")
            return
        sim = self._simulators.get(eq.id)
        if sim and sim.is_connected:
            sim.stop()
            del self._simulators[eq.id]
            self.btn_sim.configure(text="Подключить симулятор")
            self._append_log("info", "Симулятор отключён")
            return
        sim = C01Simulator(eq, on_log=self._enqueue_log)
        self._simulators[eq.id] = sim
        sim.start()
        self.btn_sim.configure(text="Отключить симулятор")
        url = eq.ws_listen_url().replace("0.0.0.0", "127.0.0.1")
        self._append_log("info", f"Симулятор → {url}")

    def _stop_simulators(self) -> None:
        for sim in self._simulators.values():
            sim.stop()
        self._simulators.clear()
        if hasattr(self, "btn_sim"):
            self.btn_sim.configure(text="Подключить симулятор")

    def _sim_send_card(self) -> None:
        eq = self._get_selected_equipment()
        if not eq:
            return
        sim = self._simulators.get(eq.id)
        if not sim or not sim.is_connected:
            messagebox.showwarning("Симулятор", "Сначала подключите симулятор.")
            return
        card_id = self.entry_qr.get().strip()
        if not card_id:
            card_id = f"FITNESSCLUB:ENTRY:user-1:{int(time.time() * 1000)}"
        if sim.send_card(card_id):
            self._append_log("info", "Тестовая карта отправлена через симулятор")

    def _enqueue_log(self, level: str, msg: str) -> None:
        self._log_queue.put((level, msg))

    def _drain_log_queue(self) -> None:
        while True:
            try:
                level, msg = self._log_queue.get_nowait()
            except queue.Empty:
                break
            self._append_log(level, msg)
        self.after(150, self._drain_log_queue)

    def _append_log(self, level: str, msg: str) -> None:
        ts = datetime.now().strftime("%H:%M:%S")
        line = f"[{ts}] [{level.upper():5}] {msg}\n"
        self.log.configure(state=tk.NORMAL)
        self.log.insert(tk.END, line)
        self.log.see(tk.END)
        self.log.configure(state=tk.DISABLED)

    def _clear_log(self) -> None:
        self.log.configure(state=tk.NORMAL)
        self.log.delete("1.0", tk.END)
        self.log.configure(state=tk.DISABLED)

    def _open_door(self) -> None:
        if not self.agent or not self._running:
            messagebox.showwarning("Агент", "Сначала запустите агент.")
            return
        eid = self._selected_eq_id
        if self.agent.open_door(eid):
            self._append_log("info", "Команда open отправлена")
        else:
            messagebox.showwarning("C01", "Устройство не подключено (C01 или симулятор).")

    def _test_crm(self) -> None:
        self._crm_from_fields()
        ok, msg = ClubAgent(self.cfg, self._enqueue_log).test_crm()
        self._append_log("info" if ok else "error", f"CRM: {msg}")
        if ok:
            messagebox.showinfo("CRM", msg)
        else:
            messagebox.showerror("CRM", msg)

    def _qr_refresh_ts_passage(self) -> None:
        entry = getattr(self, "entry_qr_passage", None)
        if not entry:
            return
        parts = entry.get().strip().split(":")
        if len(parts) >= 4 and parts[0] == "FITNESSCLUB" and parts[1] == "ENTRY":
            parts[3] = str(int(time.time() * 1000))
            entry.delete(0, tk.END)
            entry.insert(0, ":".join(parts))

    def _run_passage_test(self) -> None:
        self._qr_refresh_ts_passage()
        qr = self.entry_qr_passage.get().strip()
        if not qr:
            return
        self._crm_from_fields()
        if self.agent and self._running:
            ok, msg = self.agent.submit_qr_manual(qr, self._selected_eq_id)
        else:
            ok, msg = ClubAgent(self.cfg, self._enqueue_log).submit_qr_manual(qr)
        color = "#0a7a0a" if ok else "#a30"
        self.lbl_passage_result.configure(
            text=f"{'ДОПУСК' if ok else 'ОТКАЗ'}: {msg}\nПодробности — вкладка «Журнал».",
            foreground=color,
        )
        self._append_log("info" if ok else "warning", f"Итог прохода: {msg}")

    def _on_close(self) -> None:
        self._stop_simulators()
        if self.agent and self._running:
            self.agent.stop()
        self.destroy()


def run_app() -> None:
    app = AgentApp()
    app.mainloop()
