"""Вставка/копирование в полях ввода: в frozen Tk на Windows стандартный Ctrl+V иногда не срабатывает."""
from __future__ import annotations

import tkinter as tk


def _clipboard_get(root: tk.Misc) -> str:
    try:
        return root.clipboard_get()
    except tk.TclError:
        return ""


def _delete_selection(w: tk.Widget) -> None:
    try:
        w.delete("sel.first", "sel.last")
    except tk.TclError:
        pass


def _paste(event: tk.Event) -> str:
    w = event.widget
    root = w.winfo_toplevel()
    clip = _clipboard_get(root)
    if not clip:
        return "break"
    cls = w.winfo_class()
    if cls not in ("TEntry", "Entry", "TCombobox"):
        return "break"
    _delete_selection(w)
    try:
        w.insert(tk.INSERT, clip)
    except tk.TclError:
        return "break"
    return "break"


def _copy(event: tk.Event) -> str:
    w = event.widget
    root = w.winfo_toplevel()
    cls = w.winfo_class()
    if cls not in ("TEntry", "Entry", "TCombobox"):
        return "break"
    try:
        text = w.selection_get()
    except tk.TclError:
        return "break"
    root.clipboard_clear()
    root.clipboard_append(text)
    return "break"


def _cut(event: tk.Event) -> str:
    _copy(event)
    w = event.widget
    try:
        w.delete("sel.first", "sel.last")
    except tk.TclError:
        pass
    return "break"


def _select_all(event: tk.Event) -> str:
    w = event.widget
    cls = w.winfo_class()
    if cls not in ("TEntry", "Entry", "TCombobox"):
        return "break"
    try:
        w.selection_range(0, tk.END)
        w.icursor(tk.END)
    except tk.TclError:
        return "break"
    return "break"


def install_edit_bindings(root: tk.Misc) -> None:
    """Повесить на классы виджетов (один раз на приложение)."""
    for cls in ("TEntry", "Entry", "TCombobox"):
        for seq in ("<Control-v>", "<Control-V>", "<Shift-Insert>"):
            root.bind_class(cls, seq, _paste)
        for seq in ("<Control-c>", "<Control-C>"):
            root.bind_class(cls, seq, _copy)
        for seq in ("<Control-x>", "<Control-X>"):
            root.bind_class(cls, seq, _cut)
        for seq in ("<Control-a>", "<Control-A>"):
            root.bind_class(cls, seq, _select_all)
