"""Сессия WebSocket с C01: auth, запрос/ответ set/get/control."""
from __future__ import annotations

import asyncio
import hashlib
import json
import logging
from typing import Any, Awaitable, Callable, Optional

from c01_protocol import access_deny, auth_response, exdev_open
from equipment import EquipmentItem

LOG = logging.getLogger("c01.session")

# Считыватель/турникет может прислать не только event=card, но и pass_personal
# (см. plugins/event.py в примере PERCo ctl_websock) — структура та же: number, direction, id.
_PRESENTATION_EVENTS = frozenset({"card", "pass_personal"})


def _parse_presented_credential(
    data: dict[str, Any],
    equipment: EquipmentItem,
) -> tuple[str | None, int, int, str] | None:
    """
    Если это событие «предъявлен носитель / QR», вернуть
    (строка_id, number, direction, имя_события), иначе None.
    """
    ev = data.get("event")
    if ev not in _PRESENTATION_EVENTS:
        return None
    key = str(ev)
    block = data.get(key)
    if not isinstance(block, dict):
        return (None, equipment.exdev_number, equipment.exdev_direction, key)
    raw = (
        block.get("id")
        or block.get("code")
        or block.get("data")
        or block.get("text")
        or ""
    )
    qr = str(raw).strip() if raw is not None else ""
    try:
        number = int(block.get("number", equipment.exdev_number))
    except (TypeError, ValueError):
        number = equipment.exdev_number
    try:
        direction = int(block.get("direction", equipment.exdev_direction))
    except (TypeError, ValueError):
        direction = equipment.exdev_direction
    return (qr or None, number, direction, key)

OnLog = Optional[Callable[[str, str], None]]
CardHandler = Callable[[str, int, int, EquipmentItem], Awaitable[bool]]


class C01Session:
    """Общая логика для входящего (сервер) и исходящего (клиент) WebSocket."""

    def __init__(
        self,
        equipment: EquipmentItem,
        *,
        on_log: OnLog = None,
        on_card: Optional[CardHandler] = None,
        on_connected: Optional[Callable[[bool], None]] = None,
    ) -> None:
        self.equipment = equipment
        self.on_log = on_log
        self.on_card = on_card
        self.on_connected = on_connected
        self._ws: Any = None
        self._authenticated = False
        self._reply_queue: asyncio.Queue[dict[str, Any]] = asyncio.Queue()

    def _emit(self, level: str, msg: str) -> None:
        if self.on_log:
            self.on_log(level, f"[{self.equipment.name}] {msg}")

    def _auth_hash(self, salt: str) -> str:
        pwd = self.equipment.password or ""
        return hashlib.md5(f"{salt}{pwd}".encode()).hexdigest()

    async def send_json(self, msg: dict[str, Any]) -> None:
        if not self._ws:
            raise RuntimeError("WebSocket не подключён")
        raw = json.dumps(msg, ensure_ascii=False)
        self._emit("debug", f"→ {raw[:500]}")
        await self._ws.send(raw)

    async def request(
        self,
        msg: dict[str, Any],
        *,
        timeout: float = 8.0,
        expect_key: Optional[str] = None,
    ) -> dict[str, Any]:
        """Отправить set/get/control и дождаться answer/result."""
        while not self._reply_queue.empty():
            try:
                self._reply_queue.get_nowait()
            except asyncio.QueueEmpty:
                break
        await self.send_json(msg)
        key = expect_key or msg.get("set") or msg.get("get") or msg.get("control")
        deadline = asyncio.get_event_loop().time() + timeout
        while asyncio.get_event_loop().time() < deadline:
            remaining = deadline - asyncio.get_event_loop().time()
            if remaining <= 0:
                break
            try:
                data = await asyncio.wait_for(self._reply_queue.get(), timeout=remaining)
            except asyncio.TimeoutError:
                break
            if key and isinstance(key, str):
                if data.get("answer", {}).get(key) == "ok":
                    return data
                if data.get("result", {}).get(key) == "ok":
                    return data
                if key in data and data.get("answer"):
                    return data
            if data.get("answer") or data.get("result"):
                return data
        raise TimeoutError(f"Нет ответа на {msg!r} за {timeout}s")

    async def handle_incoming(self, data: dict[str, Any]) -> Optional[dict[str, Any]]:
        """Обработка сообщения от C01. Для card/pass_personal — ответ access/exdev."""
        if data.get("event") not in _PRESENTATION_EVENTS:
            self._emit("debug", f"← {json.dumps(data, ensure_ascii=False)[:400]}")

        if data.get("event") == "need_auth":
            salt = (data.get("need_auth") or {}).get("salt", "")
            await self.send_json(auth_response(self._auth_hash(salt)))
            self._emit("info", "Авторизация: отправлен hash (salt+пароль)")
            return None

        ans = data.get("answer") or {}
        if ans.get("auth") == "ok":
            self._authenticated = True
            self._emit("info", "Авторизация OK")
            await self._reply_queue.put(data)
            return None
        if ans.get("auth") == "error":
            self._emit("error", "Авторизация отклонена — проверьте пароль (net.password)")
            await self._reply_queue.put(data)
            return None

        if data.get("answer") or data.get("result") or data.get("net") or data.get("state"):
            await self._reply_queue.put(data)

        presented = _parse_presented_credential(data, self.equipment)
        if presented is not None and self.on_card:
            qr, number, direction, ev_name = presented
            if not qr:
                preview = json.dumps(data, ensure_ascii=False)[:500]
                self._emit(
                    "warning",
                    f"{ev_name}: в событии нет id/code для CRM — проверьте reader/exdev в PERCo. JSON: {preview}",
                )
                return access_deny(number, direction)
            self._emit("info", f"{ev_name}: данные для CRM ({len(qr)} симв.): {qr[:120]}")
            try:
                granted = await self.on_card(qr, number, direction, self.equipment)
            except Exception as e:
                self._emit("error", f"{ev_name} → CRM/C01: {e}")
                granted = False
            if granted:
                return exdev_open(
                    number,
                    direction,
                    open_type=self.equipment.open_type,
                    open_time_ms=self.equipment.open_time_ms,
                )
            return access_deny(number, direction)

        ev = data.get("event")
        if ev and ev not in ("need_auth",) and ev not in _PRESENTATION_EVENTS:
            self._emit("info", f"Событие: {ev}")

        return None

    async def attach(self, ws: Any) -> None:
        self._ws = ws
        self._authenticated = not bool(self.equipment.password)
        if self.on_connected:
            self.on_connected(True)

    async def detach(self) -> None:
        self._ws = None
        self._authenticated = False
        if self.on_connected:
            self.on_connected(False)

    @property
    def connected(self) -> bool:
        return self._ws is not None

    @property
    def authenticated(self) -> bool:
        return self._authenticated
