"""Фоновые задачи: CRM + C01 (режимы listen / connect)."""
from __future__ import annotations

import json
import logging
import threading
import time
import urllib.error
from datetime import datetime
from typing import Any, Callable, Optional, Union

from c01_client import C01Outbound
from c01_protocol import access_deny, control_cross_reference, control_output, exdev_open
from c01_server import C01Server
from config import AgentConfig
from crm_client import CrmClient
from dahua_camera import DahuaCameraListener
from equipment import EquipmentItem

LOG = logging.getLogger("agent")


def _looks_like_numeric_reader_payload(qr: str) -> bool:
    """Считыватель часто отдаёт только цифры вместо полной ASCII-строки QR из приложения."""
    q = (qr or "").strip()
    if not q or ":" in q:
        return False
    return q.isdigit() and len(q) <= 24


def _is_wiegand_entry_qr(qr: str) -> bool:
    """9 цифр UUUUUTTTC — компактный QR для PERCo Wiegand (см. WiegandEntryQrCodec в CRM)."""
    q = (qr or "").strip()
    return len(q) == 9 and q.isdigit()


def _hint_full_fitnessclub_qr() -> str:
    return (
        "Приложение отдаёт строку вида FITNESSCLUB:ENTRY:<id_пользователя>:<время_мс> — её должен "
        "передать контроллер в поле id события card/pass_personal. Если видите только цифры "
        "(например 14829991), настройте считыватель/reader в PERCo: режим полного текста QR, "
        "не Wiegand-only, не обрезание до кода карты; проверьте тип reader (Barcode / QR). "
        "Проверка: вкладка «Проход» — вставьте строку из приложения вручную — в журнале должен "
        "появиться полный FITNESSCLUB:..."
    )


def _fitnessclub_entry_timestamp_tail(qr: str) -> str:
    """Последний сегмент FITNESSCLUB:ENTRY:user-x:<ts> — в приложении это System.currentTimeMillis()."""
    parts = (qr or "").strip().split(":")
    if len(parts) == 4 and parts[0] == "FITNESSCLUB" and parts[1] == "ENTRY":
        return parts[3]
    return ""


def _repair_mojibake_utf8(value: Any) -> Any:
    """
    Если строка — это UTF-8-текст, ошибочно прочитанный как Latin-1 (частая ошибка MySQL/клиента),
    восстанавливаем нормальный Unicode для отображения в журнале.
    """
    if isinstance(value, str):
        if not value or all(ord(c) < 128 for c in value):
            return value
        try:
            repaired = value.encode("latin-1").decode("utf-8")
        except (UnicodeDecodeError, UnicodeEncodeError):
            return value
        if repaired != value and any("\u0400" <= c <= "\u04FF" for c in repaired):
            return repaired
        return value
    if isinstance(value, dict):
        return {k: _repair_mojibake_utf8(v) for k, v in value.items()}
    if isinstance(value, list):
        return [_repair_mojibake_utf8(v) for v in value]
    return value


class ClubAgent:
    def __init__(
        self,
        cfg: AgentConfig,
        on_log: Callable[[str, str], None],
        on_status: Optional[Callable[[bool, dict[str, bool]], None]] = None,
    ) -> None:
        self.cfg = cfg
        self.on_log = on_log
        self.on_status = on_status
        self._stop = threading.Event()
        self._crm_thread: Optional[threading.Thread] = None
        self._endpoints: dict[str, Endpoint] = {}
        self._c01_connected: dict[str, bool] = {}
        self._crm_ok = False
        self._selected_equipment_id: Optional[str] = None
        self._camera: Optional[DahuaCameraListener] = None
        self._camera_last_alarm: str = ""
        # Дедупликация: момент последнего успешного прохода по QR (monotonic) — чтобы контроль
        # входа без QR не дублировал tailgating, и момент последней тревоги «вход без QR» (cooldown).
        self._last_qr_entry_mono: float = 0.0
        self._last_standalone_alarm_mono: float = 0.0
        self._standalone_thread: Optional[threading.Thread] = None
        # Один QR не должен уходить в CRM дважды (card + pass_personal / эхо C01).
        # Значение: (monotonic_ts, granted) — повтор эха после отказа не должен открывать турникет.
        self._qr_dedupe_lock = threading.Lock()
        self._recent_qrs: dict[str, tuple[float, bool]] = {}

    QR_DEDUPE_SECONDS = 90.0

    def _qr_dedupe_lookup(self, qr: str) -> Optional[bool]:
        """Если QR уже обрабатывали недавно — вернуть прежний granted; иначе None (слать в CRM)."""
        q = (qr or "").strip()
        if not q:
            return None
        now = time.monotonic()
        with self._qr_dedupe_lock:
            if len(self._recent_qrs) > 500:
                cutoff = now - self.QR_DEDUPE_SECONDS
                self._recent_qrs = {k: v for k, v in self._recent_qrs.items() if v[0] >= cutoff}
            prev = self._recent_qrs.get(q)
            if prev is not None and (now - prev[0]) < self.QR_DEDUPE_SECONDS:
                return prev[1]
            return None

    def _qr_dedupe_store(self, qr: str, granted: bool) -> None:
        q = (qr or "").strip()
        if not q:
            return
        with self._qr_dedupe_lock:
            self._recent_qrs[q] = (time.monotonic(), bool(granted))

    def _emit(self, level: str, msg: str) -> None:
        self.on_log(level, msg)

    def _notify_status(self) -> None:
        if self.on_status:
            self.on_status(self._crm_ok, dict(self._c01_connected))

    def _on_c01_connection(self, equipment_id: str, connected: bool) -> None:
        self._c01_connected[equipment_id] = connected
        self._notify_status()

    def _crm_client(self) -> CrmClient:
        return CrmClient(self.cfg, on_exchange=self._log_crm_exchange)

    def _log_crm_exchange(
        self,
        method: str,
        path: str,
        request_body: Optional[dict],
        status: int,
        response: dict,
    ) -> None:
        self._emit("info", "────── CRM запрос ──────")
        self._emit("info", f"→ {method} {path}")
        if request_body is not None:
            self._emit("info", f"  тело: {json.dumps(request_body, ensure_ascii=False)}")
        self._emit("info", f"← HTTP {status}")
        display = _repair_mojibake_utf8(response) if isinstance(response, dict) else response
        self._emit("info", f"  тело: {json.dumps(display, ensure_ascii=False)}")
        granted = response.get("access_granted")
        reason = response.get("reason", "")
        if granted is True:
            user = response.get("user") or {}
            name = _repair_mojibake_utf8(user.get("name") or user.get("id", ""))
            if response.get("passage") == "exit":
                self._emit("info", f"  итог: ВЫХОД разрешён — {name}" if name else "  итог: ВЫХОД разрешён")
            else:
                self._emit("info", f"  итог: ДОПУСК — {name}")
        elif granted is False:
            self._emit("warning", f"  итог: ОТКАЗ — reason={reason}")

    def _log_c01_command(self, equipment: EquipmentItem, cmd: dict, label: str) -> None:
        self._emit("info", f"────── C01 команда ({label}) ──────")
        self._emit("info", f"→ {json.dumps(cmd, ensure_ascii=False)}")

    def update_config(self, cfg: AgentConfig) -> None:
        """Применить сохранённый конфиг без перезапуска (номера/direction считывателей)."""
        self.cfg = cfg
        for eq in cfg.equipment:
            ep = self._endpoints.get(eq.id)
            if ep is None:
                continue
            ep.equipment = eq
            if getattr(ep, "session", None) is not None:
                ep.session.equipment = eq
        self._emit(
            "info",
            "Конфиг оборудования применён к работающему агенту "
            "(вход/выход: number + direction из сохранённых настроек).",
        )

    def _live_equipment(self, equipment: EquipmentItem) -> EquipmentItem:
        live = self.cfg.get_equipment(equipment.id)
        return live if live is not None else equipment

    async def _handle_card(self, qr: str, number: int, direction: int, equipment: EquipmentItem) -> bool:
        equipment = self._live_equipment(equipment)
        passage, passage_err = equipment.resolve_passage(number, direction)
        if passage is None:
            self._emit("warning", f"══════ ОТКАЗ: {passage_err} ══════")
            return False

        title = "ВЫХОД" if passage == "exit" else "ВХОД"
        self._emit("info", f"══════ {title}: скан QR ══════")
        prev = qr[:200] + ("…" if len(qr) > 200 else "")
        self._emit(
            "info",
            f"← C01 number={number} direction={direction} → CRM: "
            f"строка доступа {len(qr)} симв. (превью до 200 для журнала; в HTTP уходит полностью): {prev}",
        )
        if equipment.readers_configured():
            self._emit(
                "info",
                f"Считыватель: number={number} direction={direction} → {passage} "
                f"(вход n={equipment.entry_reader_number!r}/d={equipment.entry_reader_direction!r}, "
                f"выход n={equipment.exit_reader_number!r}/d={equipment.exit_reader_direction!r}); "
                f"toggle вход↔выход выключен",
            )
        else:
            self._emit(
                "warning",
                "Считыватели вход/выход не заданы (number/direction) — CRM может перепутать вход и выход. "
                "Укажите в Оборудование пары для входа и выхода → Сохранить всё.",
            )

        if (
            self.cfg.only_fitnessclub_qr
            and not qr.startswith("FITNESSCLUB:")
            and not _is_wiegand_entry_qr(qr)
        ):
            self._emit(
                "warning",
                "Строка не FITNESSCLUB: и не 9-значный Wiegand-QR — в CRM не отправляем "
                "(галочка «Только QR FITNESSCLUB» на вкладке CRM). "
                "Для PERCo Wiegand приложение отдаёт 9 цифр; обновите iOS/Android.",
            )
            if _looks_like_numeric_reader_payload(qr) and not _is_wiegand_entry_qr(qr):
                self._emit("info", _hint_full_fitnessclub_qr())
            return False
        if not self.cfg.crm_ready():
            self._emit("error", "CRM не настроен (URL / gateway_token) — проход запрещён")
            return False

        cached = self._qr_dedupe_lookup(qr)
        if cached is not None:
            self._emit(
                "info",
                f"QR dedupe: повтор того же кода за {self.QR_DEDUPE_SECONDS:.0f} с — в CRM не шлём "
                f"(эхо C01 → {'открыть' if cached else 'отказ'}, как в предыдущем ответе).",
            )
            return cached

        import asyncio

        # Раздельные считыватели: на входе CRM не должен сам писать exit.
        allow_toggle = not equipment.readers_configured()
        try:
            code, body = await asyncio.to_thread(
                lambda: self._crm_client().submit_qr(
                    qr,
                    passage=passage,
                    allow_exit_toggle=allow_toggle,
                )
            )
        except urllib.error.URLError as e:
            self._emit("error", f"CRM сеть (нет ответа): {e}")
            code, body = 0, {}
        except Exception as e:
            self._emit("error", f"CRM ошибка запроса: {e}")
            code, body = 0, {}

        granted = code == 200 and bool(body.get("access_granted"))
        self._qr_dedupe_store(qr, granted)
        if not granted and isinstance(body, dict):
            reason = body.get("reason", "")
            if reason and code != 0:
                self._emit("warning", f"CRM: доступ не выдан — HTTP {code}, reason={reason!r}")
            if reason == "no_active_subscription":
                self._emit(
                    "info",
                    "CRM: у этого пользователя нет действующего абонемента (не ошибка турникета). "
                    "В админке CRM откройте клиента по id из QR и оформите/активируйте абонемент, проверьте даты и клуб.",
                )
            if reason == "visits_exhausted":
                self._emit(
                    "info",
                    "CRM: абонемент есть, но лимит посещений исчерпан. "
                    "Клиенту нужно купить/продлить абонемент или увеличить число посещений в CRM.",
                )
            if reason == "subscription_wrong_club":
                self._emit(
                    "info",
                    "CRM: абонемент есть, но привязан к другому клубу (или в БД не тот club). "
                    "В админке → Абонементы укажите клуб как у шлюза (gateway_token этого ПК), либо перевыдайте абонемент с выбором клуба.",
                )
            if reason == "already_inside":
                self._emit(
                    "info",
                    "CRM: клиент уже в зале — считыватель входа не засчитывает выход. "
                    "Для выхода нужен скан на считывателе выхода.",
                )
            if reason == "qr_expired":
                tail = _fitnessclub_entry_timestamp_tail(qr)
                if len(tail) == 7 and tail.isalnum() and not tail.isdigit():
                    self._emit(
                        "info",
                        "Последний сегмент из 7 символов (есть буквы) — компактное время base62. "
                        "Если отказ сохраняется — проверьте часы телефона/сервера и окно 15 с.",
                    )
                elif tail.isdigit() and len(tail) < 11:
                    self._emit(
                        "warning",
                        "Короткая только-цифровая метка в конце QR — часто лимит длины поля id на PERCo/C01 "
                        f"(~32 символа): полные миллисекунды не помещаются, пришло «{tail}». "
                        "Обновите CRM и приложение (время в 7 символах base62) или настройте передачу полного id.",
                    )
                elif tail.isdigit() and len(tail) >= 11:
                    self._emit(
                        "info",
                        "Если метка времени в QR полная, отказ qr_expired значит: прошло больше ~15 с между "
                        "моментом генерации QR в приложении и проверкой на сервере CRM, либо часы телефона/сервера "
                        "сильно расходятся. Покажите свежий QR и убедитесь, что запрос до CRM уходит сразу после скана "
                        "(сеть, wait_command_time на C01).",
                    )
            if reason == "invalid_format" and _looks_like_numeric_reader_payload(qr):
                self._emit("info", _hint_full_fitnessclub_qr())

        if granted:
            cmd = exdev_open(
                number,
                direction,
                open_type=equipment.open_type,
                open_time_ms=equipment.open_time_ms,
            )
            self._log_c01_command(equipment, cmd, "открыть турникет")
            if getattr(equipment, "relay_use_cross_reference", False):
                cn = int(equipment.relay_cross_number)
                pulse = max(0, int(getattr(equipment, "relay_pulse_ms", 0)))
                self._log_c01_command(
                    equipment,
                    control_cross_reference(number=cn, activate=True),
                    f"внутренняя реакция №{cn} (вкл), затем пауза {pulse} мс и выкл",
                )
            elif getattr(equipment, "relay_after_grant", False):
                out_n = int(equipment.relay_output_number)
                pulse = max(0, int(equipment.relay_pulse_ms))
                self._log_c01_command(
                    equipment,
                    control_output(out_n, True),
                    f"реле/выход №{out_n} (вкл), импульс {pulse} мс",
                )
                if pulse > 0:
                    self._log_c01_command(
                        equipment,
                        control_output(out_n, False),
                        f"реле/выход №{out_n} (выкл)",
                    )
            if passage == "entry":
                self._arm_tailgating_window(qr)
        else:
            cmd = access_deny(number, direction)
            self._log_c01_command(equipment, cmd, "запрет прохода")

        return granted

    def _arm_tailgating_window(self, qr: str) -> None:
        """После открытия двери по одному QR посчитать пересечения линии входа камерой.

        Если за окно прошло >= min_people человек — это проход вдвоём (tailgating): шлём тревогу в CRM.
        """
        # Фиксируем момент прохода по QR даже без камеры — для дедупликации контроля входа без QR.
        self._last_qr_entry_mono = time.monotonic()
        cam = self._camera
        if cam is None or not self.cfg.camera.enabled:
            return
        cam_cfg = self.cfg.camera
        window_ms = max(500, int(cam_cfg.tailgating_window_ms))
        pre_roll_ms = max(0, int(cam_cfg.pre_roll_ms))
        min_people = max(2, int(cam_cfg.min_people))
        t0 = time.monotonic()
        self._emit(
            "info",
            f"камера: окно подсчёта прохода {window_ms} мс (контроль прохода вдвоём, порог {min_people})",
        )

        def worker() -> None:
            end = t0 + window_ms / 1000.0
            while not self._stop.is_set():
                now = time.monotonic()
                if now >= end:
                    break
                self._stop.wait(min(0.2, end - now))
            crossings = cam.crossings_between(t0 - pre_roll_ms / 1000.0, time.monotonic())
            count = len(crossings)
            if count >= min_people:
                self._emit(
                    "warning",
                    f"камера: по одному QR прошло {count} чел. — тревога «проход вдвоём» (tailgating)",
                )
                self._camera_last_alarm = f"{datetime.now():%H:%M:%S} — {count} чел."
                self._send_tailgating_alarm(qr, count, crossings, window_ms)
            else:
                self._emit("info", f"камера: прошло {count} чел. — норма")

        threading.Thread(target=worker, name="tailgating-window", daemon=True).start()

    def _send_tailgating_alarm(
        self, qr: str, people_count: int, crossings: list[dict], window_ms: int
    ) -> None:
        try:
            code, body = self._crm_client().submit_alarm(
                qr=qr,
                alarm_type="tailgating",
                people_count=people_count,
                details={"window_ms": window_ms, "crossings": crossings},
            )
        except Exception as e:  # noqa: BLE001 — сеть; тревогу не теряем в логе
            self._emit("error", f"CRM: ошибка отправки тревоги: {e}")
            return
        if code == 200 and isinstance(body, dict) and body.get("ok"):
            self._emit("info", "CRM: тревога «проход вдвоём» принята (уведомления отправлены)")
        else:
            self._emit(
                "warning",
                f"CRM: тревога не принята — HTTP {code}, тело {json.dumps(body, ensure_ascii=False)}",
            )

    def _start_standalone_watcher(self) -> None:
        """Непрерывный контроль входа без QR: тревога, если за окно прошло >= порога человек.

        Работает независимо от прохода по QR. Дедупликация: пропускаем интервалы, перекрывающиеся
        с окном tailgating после успешного прохода по QR (там уже своя тревога), и держим cooldown,
        чтобы одна группа не порождала много тревог.
        """
        cam = self._camera
        cam_cfg = self.cfg.camera
        if cam is None or not cam_cfg.standalone_enabled:
            return
        window_ms = max(500, int(cam_cfg.standalone_window_ms))
        min_people = max(2, int(cam_cfg.standalone_min_people))
        cooldown_ms = max(0, int(cam_cfg.standalone_cooldown_ms))
        tg_window_ms = max(0, int(cam_cfg.tailgating_window_ms))
        poll_sec = max(0.3, min(1.0, window_ms / 2000.0))
        self._emit(
            "info",
            f"камера: контроль входа без QR включён (окно {window_ms} мс, порог {min_people})",
        )

        def worker() -> None:
            while not self._stop.is_set():
                if self._stop.wait(poll_sec):
                    break
                now = time.monotonic()
                # cooldown между тревогами
                if (now - self._last_standalone_alarm_mono) * 1000.0 < cooldown_ms:
                    continue
                # дедуп против tailgating: проход по QR «объясняет» проходы в своём окне
                if (now - self._last_qr_entry_mono) * 1000.0 < (tg_window_ms + window_ms):
                    continue
                crossings = cam.crossings_between(now - window_ms / 1000.0, now)
                count = len(crossings)
                if count >= min_people:
                    self._emit(
                        "warning",
                        f"камера: вход группой без прохода по QR — {count} чел. (тревога group_entry)",
                    )
                    self._camera_last_alarm = f"{datetime.now():%H:%M:%S} — {count} чел. (без QR)"
                    self._last_standalone_alarm_mono = now
                    self._send_group_entry_alarm(count, crossings, window_ms)

        self._standalone_thread = threading.Thread(
            target=worker, name="standalone-entry-watcher", daemon=True
        )
        self._standalone_thread.start()

    def _send_group_entry_alarm(
        self, people_count: int, crossings: list[dict], window_ms: int
    ) -> None:
        try:
            code, body = self._crm_client().submit_alarm(
                qr="",
                alarm_type="group_entry",
                people_count=people_count,
                details={"window_ms": window_ms, "crossings": crossings, "no_qr": True},
            )
        except Exception as e:  # noqa: BLE001 — сеть; тревогу не теряем в логе
            self._emit("error", f"CRM: ошибка отправки тревоги (вход без QR): {e}")
            return
        if code == 200 and isinstance(body, dict) and body.get("ok"):
            self._emit("info", "CRM: тревога «вход группой без QR» принята (уведомления отправлены)")
        else:
            self._emit(
                "warning",
                f"CRM: тревога не принята — HTTP {code}, тело {json.dumps(body, ensure_ascii=False)}",
            )

    def _crm_loop(self) -> None:
        while not self._stop.is_set():
            if not self.cfg.crm_ready():
                self._crm_ok = False
                self._notify_status()
                self._stop.wait(5.0)
                continue
            try:
                code, _body = self._crm_client().heartbeat(
                    {"agent": "FitnessClubAgent", "equipment": self._c01_connected},
                    log=False,
                )
                self._crm_ok = code == 200
            except Exception as e:
                self._crm_ok = False
                self._emit("error", f"CRM heartbeat: {e}")
            self._notify_status()

            if self.cfg.crm_poll_enabled:
                try:
                    code, data = self._crm_client().poll_commands(timeout=30.0)
                    if code == 200:
                        for cmd in data.get("commands") or []:
                            self._execute_command(None, cmd)
                except Exception as e:
                    self._emit("debug", f"CRM poll: {e}")

            self._stop.wait(max(1.0, self.cfg.heartbeat_interval_sec))

    def _pick_endpoint(self) -> Optional[Endpoint]:
        if self._selected_equipment_id:
            ep = self._endpoints.get(self._selected_equipment_id)
            if ep and ep.connected:
                return ep
        for ep in self._endpoints.values():
            if ep.connected:
                return ep
        return None

    def _execute_command(self, crm: Optional[CrmClient], cmd: dict) -> None:
        if crm is None:
            crm = self._crm_client()
        cmd_id = cmd.get("id")
        kind = (cmd.get("kind") or "").lower()
        try:
            if kind == "ping":
                crm.ack_command(int(cmd_id), "done", {"echo": cmd.get("payload") or {}})
                return
            if kind == "open_door":
                ep = self._pick_endpoint()
                if ep is None or not ep.open_door_sync():
                    raise RuntimeError("C01 не подключён")
                crm.ack_command(int(cmd_id), "done", {"c01": "open"})
                return
            raise RuntimeError(f"Неизвестная команда: {kind}")
        except Exception as e:
            self._emit("error", f"Команда #{cmd_id}: {e}")
            if cmd_id is not None:
                crm.ack_command(int(cmd_id), "failed", {"error": str(e)})

    def start(self) -> None:
        self._stop.clear()
        self._endpoints.clear()
        self._c01_connected.clear()
        for eq in self.cfg.enabled_equipment():
            self._c01_connected[eq.id] = False
            if eq.connection_mode == "connect":
                ep: Endpoint = C01Outbound(
                    eq,
                    self._handle_card,
                    on_log=self.on_log,
                    on_connection_change=self._on_c01_connection,
                )
                self._emit("info", f"{eq.name}: режим «к контроллеру» {eq.ws_connect_url()}")
            else:
                ep = C01Server(
                    eq,
                    self._handle_card,
                    on_log=self.on_log,
                    on_connection_change=self._on_c01_connection,
                )
                self._emit("info", f"{eq.name}: сервер системы {eq.ws_listen_url()}")
            self._endpoints[eq.id] = ep
            ep.start_background()
        self._crm_thread = threading.Thread(target=self._crm_loop, daemon=True)
        self._crm_thread.start()
        self._start_camera()
        self._emit("info", "Агент запущен")

    def _start_camera(self) -> None:
        cam_cfg = self.cfg.camera
        if not cam_cfg.enabled or not cam_cfg.host.strip():
            return
        self._camera = DahuaCameraListener(
            host=cam_cfg.host,
            username=cam_cfg.username,
            password=cam_cfg.password,
            channel=cam_cfg.channel,
            event_codes=cam_cfg.event_codes,
            inbound_direction=cam_cfg.inbound_direction,
            require_human=cam_cfg.require_human,
            https=cam_cfg.https,
            verify_ssl=cam_cfg.verify_ssl,
            on_log=self.on_log,
        )
        self._camera.start()
        self._emit("info", f"Камера: запуск слежения за линией входа ({cam_cfg.host})")
        self._start_standalone_watcher()

    def stop(self) -> None:
        self._stop.set()
        for ep in self._endpoints.values():
            ep.stop()
        self._endpoints.clear()
        self._c01_connected.clear()
        if self._camera is not None:
            self._camera.stop()
            self._camera = None
        self._standalone_thread = None
        self._crm_ok = False
        self._notify_status()

    def get_server(self, equipment_id: str) -> Optional[C01Server]:
        ep = self._endpoints.get(equipment_id)
        return ep if isinstance(ep, C01Server) else None

    def get_endpoint(self, equipment_id: str) -> Optional[Endpoint]:
        return self._endpoints.get(equipment_id)

    def set_selected_equipment(self, equipment_id: Optional[str]) -> None:
        self._selected_equipment_id = equipment_id

    def test_crm(self) -> tuple[bool, str]:
        if not self.cfg.crm_ready():
            return False, "Укажите URL CRM и gateway_token"
        try:
            code, body = self._crm_client().heartbeat({"probe": True}, log=True)
            if code == 200:
                return True, f"HTTP {code} (см. журнал)"
            hint = ""
            if code == 401 and body.get("reason") == "unauthorized":
                hint = (
                    " — неверный или устаревший gateway_token. "
                    "Админка CRM → «Франшиза» → клуб → скопируйте токен шлюза; "
                    "после «Сгенерировать новый» вставьте новый токен в агент и «Сохранить всё»."
                )
            return False, f"HTTP {code}{hint} (см. журнал)"
        except Exception as e:
            return False, str(e)

    def submit_qr_manual(self, qr: str, equipment_id: Optional[str] = None) -> tuple[bool, str]:
        if not self.cfg.crm_ready():
            return False, "CRM не настроен"
        eq_item: Optional[EquipmentItem] = None
        if equipment_id:
            for e in self.cfg.equipment:
                if e.id == equipment_id:
                    eq_item = e
                    break
        if eq_item is None:
            ep = self._pick_endpoint()
            if ep is not None:
                eq_item = ep.equipment
            elif self.cfg.equipment:
                eq_item = self.cfg.equipment[0]
        passage = (eq_item.gate_role if eq_item else "entry") or "entry"
        if passage not in ("entry", "exit"):
            passage = "entry"
        title = "ВЫХОД" if passage == "exit" else "ВХОД"
        self._emit("info", f"══════ {title}: тест QR (вручную) ══════")
        self._emit("info", f"QR: {qr.strip()}")
        code, body = self._crm_client().submit_qr(qr.strip(), passage=passage)
        granted = code == 200 and bool(body.get("access_granted"))
        if granted:
            ep = None
            if equipment_id:
                ep = self._endpoints.get(equipment_id)
            if ep is None:
                ep = self._pick_endpoint()
            if ep and ep.connected:
                ep.open_door_sync()
                self._emit("info", "→ C01: команда open отправлена")
            else:
                self._emit("warning", "CRM разрешил, но C01 не подключён — дверь не открыта")
        reason = body.get("reason", "")
        name = (body.get("user") or {}).get("name", "")
        if isinstance(name, str):
            name = _repair_mojibake_utf8(name)
        summary = f"HTTP {code}, granted={granted}, reason={reason}"
        if reason == "qr_expired":
            summary += (
                " — окно 15 с: метка в QR и время сервера CRM разлишком сильно "
                "(см. в теле ответа delta_ms, server_now_ms, qr_timestamp_ms). "
                "Обновите QR на вкладке «Проход» / в приложении; на VPS проверьте `date` и NTP."
            )
        elif reason == "no_active_subscription":
            summary += " — у клиента в CRM нет действующего абонемента (админка → клиент → абонемент)."
        elif reason == "visits_exhausted":
            summary += " — лимит посещений по абонементу исчерпан."
        elif reason == "subscription_wrong_club":
            summary += " — абонемент не на этот клуб (CRM: subscriptions.club_id vs клуб шлюза по gateway_token)."
        if name:
            summary += f", клиент={name}"
        return granted, summary

    def open_door(
        self,
        equipment_id: Optional[str] = None,
        *,
        side: Optional[str] = None,
        number: Optional[int] = None,
        direction: Optional[int] = None,
    ) -> bool:
        """
        Ручное открытие.
        number/direction — явный ИУ; иначе side=entry|exit из настроек; иначе exdev_number.
        """
        eq: Optional[EquipmentItem] = None
        if equipment_id:
            eq = self.cfg.get_equipment(equipment_id)
            ep = self._endpoints.get(equipment_id)
        else:
            ep = self._pick_endpoint()
            eq = ep.equipment if ep else None

        if ep is None:
            return False

        open_n = number
        open_d = direction
        if side and eq is not None and (open_n is None or open_d is None):
            if not eq._side_configured(side):
                self._emit(
                    "warning",
                    f"Считыватель «{side}» не задан (number/direction) в настройках — открытие отменено.",
                )
                return False
            if open_n is None:
                open_n = eq.open_number_for_side(side)
            if open_d is None:
                open_d = eq.open_direction_for_side(side)
            if open_n is None:
                open_n = int(getattr(eq, "exdev_number", 0) or 0)
            if open_d is None:
                open_d = int(getattr(eq, "exdev_direction", 0) or 0)

        if open_n is not None:
            return ep.open_door_sync(
                number=int(open_n),
                direction=int(open_d if open_d is not None else 0),
            )
        return ep.open_door_sync()

    def preflight_readiness(self) -> tuple[bool, list[tuple[str, bool, str]]]:
        """Проверка готовности ПО без запуска прохода с реальным оборудованием."""
        checks: list[tuple[str, bool, str]] = []

        if self.cfg.crm_ready():
            ok, msg = self.test_crm()
            checks.append(("crm", ok, msg))
        else:
            checks.append(("crm", False, "CRM не настроен (URL / gateway_token)"))

        cam_cfg = self.cfg.camera
        if not cam_cfg.host.strip():
            if cam_cfg.enabled:
                checks.append(("camera", False, "Камера включена, но host пустой"))
            else:
                checks.append(("camera", True, "Камера выключена в настройках (host не указан)"))
        else:
            cam = DahuaCameraListener(
                host=cam_cfg.host,
                username=cam_cfg.username,
                password=cam_cfg.password,
                channel=cam_cfg.channel,
                event_codes=cam_cfg.event_codes,
                inbound_direction=cam_cfg.inbound_direction,
                require_human=cam_cfg.require_human,
                https=cam_cfg.https,
                verify_ssl=cam_cfg.verify_ssl,
                on_log=self.on_log,
            )
            ok_cam, msg_cam = cam.probe_connectivity()
            if not cam_cfg.enabled:
                prefix = "Камера выключена в настройках; "
                checks.append(("camera", ok_cam, prefix + msg_cam))
            else:
                checks.append(("camera", ok_cam, msg_cam))

        enabled_eq = self.cfg.enabled_equipment()
        if not enabled_eq:
            checks.append(("equipment", False, "Нет включенного оборудования C01/PERCo"))
        else:
            checks.append(("equipment", True, f"Включено устройств: {len(enabled_eq)}"))

        ready = all(ok for _name, ok, _msg in checks)
        return ready, checks

    @property
    def crm_online(self) -> bool:
        return self._crm_ok

    def equipment_connected(self) -> dict[str, bool]:
        return dict(self._c01_connected)

    @property
    def c01_online(self) -> bool:
        return any(self._c01_connected.values())

    @property
    def camera_enabled(self) -> bool:
        return bool(self.cfg.camera.enabled and self.cfg.camera.host.strip())

    @property
    def camera_online(self) -> bool:
        return self._camera is not None and self._camera.connected

    @property
    def camera_total_crossings(self) -> int:
        return self._camera.total_crossings if self._camera is not None else 0

    @property
    def camera_last_alarm(self) -> str:
        return self._camera_last_alarm
