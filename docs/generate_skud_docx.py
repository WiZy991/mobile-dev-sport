#!/usr/bin/env python3
"""Generate SKUD integration documentation as DOCX (stdlib only)."""
from __future__ import annotations

import zipfile
from pathlib import Path
from xml.sax.saxutils import escape

OUT = Path(__file__).resolve().parent / "Документация_интеграция_СКУД.docx"


def p(text: str, bold: bool = False) -> str:
    rpr = "<w:b/>" if bold else ""
    return (
        "<w:p><w:pPr><w:spacing w:after='120'/></w:pPr>"
        f"<w:r><w:rPr>{rpr}<w:rFonts w:ascii='Calibri' w:hAnsi='Calibri'/>"
        f"<w:sz w:val='22'/></w:rPr><w:t xml:space='preserve'>{escape(text)}</w:t></w:r></w:p>"
    )


def h(text: str, level: int) -> str:
    sizes = {1: "32", 2: "28", 3: "24"}
    sz = sizes.get(level, "24")
    return (
        f"<w:p><w:pPr><w:spacing w:before='240' w:after='120'/></w:pPr>"
        f"<w:r><w:rPr><w:b/><w:rFonts w:ascii='Calibri' w:hAnsi='Calibri'/>"
        f"<w:sz w:val='{sz}'/></w:rPr><w:t>{escape(text)}</w:t></w:r></w:p>"
    )


def mono(text: str) -> str:
    return (
        "<w:p><w:pPr><w:spacing w:after='80'/></w:pPr>"
        f"<w:r><w:rPr><w:rFonts w:ascii='Consolas' w:hAnsi='Consolas'/>"
        f"<w:sz w:val='20'/></w:rPr><w:t xml:space='preserve'>{escape(text)}</w:t></w:r></w:p>"
    )


def table(headers: list[str], rows: list[list[str]]) -> str:
    cols = len(headers)
    tbl_pr = (
        "<w:tbl><w:tblPr>"
        "<w:tblW w:w='5000' w:type='pct'/>"
        "<w:tblBorders>"
        "<w:top w:val='single' w:sz='4' w:space='0' w:color='auto'/>"
        "<w:left w:val='single' w:sz='4' w:space='0' w:color='auto'/>"
        "<w:bottom w:val='single' w:sz='4' w:space='0' w:color='auto'/>"
        "<w:right w:val='single' w:sz='4' w:space='0' w:color='auto'/>"
        "<w:insideH w:val='single' w:sz='4' w:space='0' w:color='auto'/>"
        "<w:insideV w:val='single' w:sz='4' w:space='0' w:color='auto'/>"
        "</w:tblBorders></w:tblPr>"
    )

    def cell(text: str, header: bool = False) -> str:
        b = "<w:b/>" if header else ""
        return (
            "<w:tc><w:tcPr><w:tcW w:w='2000' w:type='dxa'/></w:tcPr>"
            "<w:p><w:r><w:rPr>" + b +
            "<w:rFonts w:ascii='Calibri' w:hAnsi='Calibri'/>"
            "<w:sz w:val='20'/></w:rPr>"
            f"<w:t>{escape(text)}</w:t></w:r></w:p></w:tc>"
        )

    parts = [tbl_pr, "<w:tr>"] + [cell(x, True) for x in headers] + ["</w:tr>"]
    for row in rows:
        parts.append("<w:tr>")
        for i in range(cols):
            parts.append(cell(row[i] if i < len(row) else ""))
        parts.append("</w:tr>")
    parts.append("</w:tbl>")
    return "".join(parts)


def build_document_body() -> str:
    blocks: list[str] = []

    def add(*items: str) -> None:
        blocks.extend(items)

    add(
        h("Документация по интеграции с системой СКУД", 1),
        p("Оператор: ООО «Ворлдкэшбокс»"),
        p("Продукт: FitnessClub CRM + мобильное приложение клиента"),
        p("Версия документа: 1.0"),
        p("Дата: 11.06.2026"),
        h("1. Общие сведения", 2),
        h("1.1. Назначение интеграции", 3),
        p(
            "Интеграция обеспечивает бесконтактный проход клиентов в фитнес-клуб через турникет/дверь. "
            "Клиент открывает динамический QR-код в мобильном приложении FitnessClub. Считыватель на турникете "
            "сканирует код. Облачная CRM проверяет действительность кода, статус абонемента и права доступа. "
            "При положительном решении локальный шлюз отправляет команду открытия в СКУД. Турникет открывается; "
            "событие фиксируется в журнале проходов CRM."
        ),
        h("1.2. Используемая СКУД", 3),
        table(
            ["Параметр", "Значение"],
            [
                ["Производитель / ПО", "PERCo (PERCo-Web)"],
                ["Протокол интеграции", "HTTP/JSON REST API"],
                ["Официальная документация API", "https://ru.percoweb.com/dev"],
                ["Примеры интеграции", "https://github.com/percodev/api_examples"],
            ],
        ),
        p(
            "СКУД PERCo-Web развёрнута локально в LAN клуба (на ПК с контроллером). "
            "Облачная CRM не имеет прямого сетевого доступа к PERCo — взаимодействие идёт через локальный шлюз."
        ),
        h("1.3. Принцип безопасности соединения", 3),
        p("Применяется схема инверсии соединения (outbound-only):"),
        p("• ПК-шлюз в клубе сам инициирует исходящие HTTPS-запросы к облачной CRM."),
        p("• Входящие порты на ПК клуба не открываются."),
        p("• PERCo-Web не публикуется в интернет."),
        p("• Учётные данные PERCo хранятся только на локальном ПК клуба и в защищённой админке CRM."),
        h("2. Архитектура решения", 2),
        mono(
            "[Мобильное приложение] → QR на экране → [Считыватель на турникете]\n"
            "      → [PERCo-Web (LAN)] → [Локальный шлюз (ПК в клубе)]\n"
            "      → HTTPS (исходящий) → [Облачная CRM (VPS)]"
        ),
        h("2.1. Компоненты системы", 3),
        table(
            ["Компонент", "Расположение", "Назначение"],
            [
                [
                    "Мобильное приложение FitnessClub (Android)",
                    "Смартфон клиента",
                    "Генерация динамического QR, обновление каждые 15 сек",
                ],
                [
                    "Облачная CRM (Symfony, PHP)",
                    "VPS (HTTPS)",
                    "Проверка QR, абонемента, журнал проходов, очередь команд",
                ],
                [
                    "Локальный шлюз (turnstile_gateway / FitnessClubAgent)",
                    "ПК в LAN клуба",
                    "Связь CRM ↔ PERCo, открытие турникета",
                ],
                [
                    "PERCo-Web",
                    "ПК/сервер в LAN клуба",
                    "Управление контроллерами, считывателями, турникетами",
                ],
                [
                    "Контроллер PERCo C01",
                    "На турникете",
                    "Исполнительное устройство (открытие/закрытие прохода)",
                ],
            ],
        ),
        h("2.2. Варианты локального шлюза", 3),
        table(
            ["Вариант", "Путь в репозитории", "Описание"],
            [
                [
                    "turnstile_gateway",
                    "crm-backend-symfony/scripts/turnstile_gateway/",
                    "Python-демон: heartbeat, long-poll, PERCo events",
                ],
                [
                    "FitnessClubAgent",
                    "crm-backend-symfony/scripts/club_agent/",
                    "Windows-приложение (EXE) с GUI, WebSocket к C01",
                ],
            ],
        ),
        p("Оба варианта работают с одними и теми же API CRM (/api/v1/gateway/*)."),
        h("2.3. ПО «FitnessClub Agent» (подробно)", 3),
        p(
            "FitnessClub Agent — локальное Windows-приложение с графическим интерфейсом, "
            "устанавливаемое на ПК в клубе. Собирается в исполняемый файл FitnessClubAgent.exe "
            "(путь: crm-backend-symfony/scripts/club_agent/dist/). "
            "Назначение: связать контроллер PERCo C01 на турникете с облачной CRM — "
            "принять событие сканирования QR, запросить решение в CRM, открыть или запретить проход."
        ),
        p(
            "В отличие от turnstile_gateway, Agent не опрашивает PERCo-Web по HTTP. "
            "Он общается с контроллером C01 напрямую по протоколу WebSocket (JSON-команды PERCo C01). "
            "Команда открытия турникета уходит в C01 (control: exdev), а не в PERCo-Web API."
        ),
        mono(
            "[QR на экране телефона] → [Считыватель → C01] → WebSocket → [FitnessClub Agent]\n"
            "      → HTTPS → [CRM /api/v1/gateway/access/entry] → решение\n"
            "      → WebSocket → [C01: открыть / запретить турникет]"
        ),
        h("2.3.1. Интерфейс и вкладки", 3),
        table(
            ["Вкладка", "Назначение"],
            [
                ["CRM", "URL облачной CRM (корень сайта, без /admin), gateway_token, проверка связи (heartbeat)"],
                ["Оборудование", "Подключение к C01: режим «Слушать» или «К контроллеру», IP/порт, роль точки (entry/exit), реле"],
                ["Проход", "Ручная проверка QR без считывателя (вставить строку с телефона → «Проверить проход»)"],
                ["Журнал", "Полный лог: события C01, запросы в CRM, команды на турникет, причины отказа"],
            ],
        ),
        h("2.3.2. Подключение к контроллеру C01", 3),
        p("Рекомендуемый режим — «Сервер системы (слушать)»:"),
        p("• Агент слушает WebSocket на host 0.0.0.0, порт 8765 (входящий TCP 8765 открыт в брандмауэре Windows)."),
        p("• В веб-интерфейсе PERCo на контроллере C01 в поле «Сервер» указывается IP ПК с агентом и порт, например 192.168.0.63:8765."),
        p("• После перезагрузки C01 в шапке агента должно быть «C01: 1/1 подключено»."),
        p("Альтернатива — режим «К контроллеру»: агент сам подключается к ws://<ip-c01>:<port>/tcp (часто порт 80)."),
        p("При включённом пароле на C01 поле «Пароль» в агенте должно совпадать с net.password контроллера."),
        h("2.3.3. Настройка CRM в агенте", 3),
        p("1. В поле URL — только корень сайта (пример: https://worldcashfit.ru), без /admin и /api/v1."),
        p("2. gateway_token — из админки CRM (Настройки → Франшиза → клуб → «Сгенерировать токен»)."),
        p("3. «Сохранить всё» → «Проверить CRM» — в журнале ожидается HTTP 200."),
        p("Один gateway_token = один клуб. Абонемент клиента в CRM должен быть привязан к этому клубу."),
        h("2.3.4. Роли точек: вход и выход", 3),
        p("Для каждого подключённого C01/турникета в карточке оборудования задаётся «Роль точки»:"),
        table(
            ["Роль", "API CRM", "Проверки"],
            [
                ["entry (вход)", "POST /api/v1/gateway/access/entry", "Формат QR, срок 15 сек, активный абонемент, клуб"],
                ["exit (выход)", "POST /api/v1/gateway/access/exit", "Тот же QR ENTRY; абонемент и срок не проверяются"],
            ],
        ),
        p("На выходе и на входе используется один формат QR из приложения: FITNESSCLUB:ENTRY:user-<id>:<timestamp>."),
        h("2.3.5. Сценарий прохода через Agent", 3),
        p("1. C01 получает скан QR со считывателя (события card или pass_personal)."),
        p("2. Agent извлекает полную строку FITNESSCLUB:ENTRY:... и отправляет в CRM."),
        p("3. CRM возвращает access_granted: true/false и данные пользователя."),
        p("4. При допуске Agent шлёт в C01 команду открытия (control: exdev). При отказе — запрет прохода (control: access)."),
        p("5. Все шаги фиксируются во вкладке «Журнал»."),
        h("2.3.6. Журналирование в Agent", 3),
        table(
            ["Строка в журнале", "Смысл"],
            [
                ["card: / pass_personal:", "C01 прислал скан; в CRM уходит полная строка QR"],
                ["ВХОД / ВЫХОД", "Роль точки: запрос к entry или exit API"],
                ["CRM запрос / HTTP 200/400", "Ответ облака: допуск или reason отказа"],
                ["C01 команда (открыть турникет)", "Отправлена команда exdev после access_granted: true"],
                ["запрет прохода", "CRM отказал или ошибка — проход запрещён на турникете"],
            ],
        ),
        h("2.3.7. Сравнение turnstile_gateway и FitnessClub Agent", 3),
        table(
            ["Критерий", "turnstile_gateway", "FitnessClub Agent"],
            [
                ["Платформа", "Python 3.10+, Linux/Windows, без GUI", "Windows, GUI, EXE"],
                ["Связь с PERCo", "HTTP API PERCo-Web", "WebSocket напрямую к C01"],
                ["Получение QR", "PERCo events API или stdin", "События C01 по WebSocket"],
                ["Открытие турникета", "POST /api/devices/{id}/command в PERCo-Web", "JSON-команда exdev в C01"],
                ["Удалённое «Открыть дверь»", "Long-poll команд из CRM", "Через long-poll (если включён) или кнопка в GUI"],
                ["Диагностика", "healthcheck, journalctl", "Вкладка «Журнал», «Проверить CRM», симулятор C01"],
            ],
        ),
        h("2.3.8. Быстрый старт Agent", 3),
        p("1. Установить FitnessClubAgent.exe на ПК в LAN клуба."),
        p("2. Вкладка CRM: URL + gateway_token → Сохранить → Проверить CRM."),
        p("3. Вкладка Оборудование: режим «Слушать», порт 8765; на C01 указать IP:8765; открыть брандмауэр."),
        p("4. Запустить агент, дождаться C01: 1/1 подключено."),
        p("5. Сканировать QR из приложения (< 15 сек) или проверить на вкладке «Проход»."),
        p("Без железа: в списке оборудования можно включить симулятор C01 для тестирования."),
        h("2.3.9. Типичные проблемы Agent", 3),
        table(
            ["Симптом", "Решение"],
            [
                ["C01: 0/1 подключено", "Проверить net.server на C01, порт 8765, брандмауэр, совпадение IP"],
                ["HTTP 401 на heartbeat", "Неверный gateway_token — обновить из админки CRM"],
                ["В журнале только цифры, не FITNESSCLUB:", "В PERCo включить передачу полного содержимого QR"],
                ["qr_expired при полном QR", "Обрезка timestamp на C01 — обновить приложение и CRM (base62, 7 символов)"],
                ["HTTP 200 при WebSocket", "На порту отвечает веб-интерфейс, не WS — проверить порт C01 (часто 80 или 8765)"],
            ],
        ),
        h("3. Сценарии прохода", 2),
        h("3.1. Вход по QR (основной сценарий)", 3),
        p("1. Клиент открывает QR в приложении. Формат: FITNESSCLUB:ENTRY:<user_id>:<timestamp>"),
        p("2. Считыватель на турникете считывает QR (сканер подключён к контроллеру PERCo или к ПК шлюза)."),
        p("3. Шлюз получает строку QR и отправляет в CRM:"),
        mono("POST /api/v1/gateway/access/entry"),
        mono("Authorization: Bearer <gateway_token>"),
        mono('Body: {"qr": "FITNESSCLUB:ENTRY:123:Ab3xYz1", "device_id": "club-1-gateway"}'),
        p("4. CRM выполняет проверки: формат QR; срок действия (15 секунд); пользователь существует и не заблокирован; активный абонемент; абонемент привязан к клубу."),
        p("5. При успехе CRM возвращает access_granted: true и блок open_device (для turnstile_gateway)."),
        p("6. Локальное ПО открывает турникет:"),
        p("   • turnstile_gateway → POST /api/devices/{id}/command в PERCo-Web;"),
        p("   • FitnessClub Agent → WebSocket-команда exdev в контроллер C01."),
        p("7. Турникет открывается."),
        h("3.2. Получение QR локальным ПО", 3),
        p("Режим A (turnstile_gateway) — считыватель на PERCo: шлюз опрашивает GET /api/events/identifications, извлекает FITNESSCLUB:..."),
        p("Режим B (turnstile_gateway) — считыватель USB «клавиатура»: шлюз читает stdin или gateway.py qr \"<text>\""),
        p("Режим C (FitnessClub Agent) — считыватель на C01: контроллер шлёт событие card/pass_personal по WebSocket, агент пересылает QR в CRM."),
        h("3.3. Выход из зала", 3),
        p("Используется тот же формат QR (FITNESSCLUB:ENTRY:...). Отдельный считыватель с ролью exit. Запрос: POST /api/v1/gateway/access/exit. Абонемент не проверяется, срок QR не ограничивается — только фиксация выхода в журнале."),
        h("3.4. Гостевой пропуск", 3),
        p("Формат: FITNESSCLUB:GUEST:<pass_id>:<token>. Проверяется валидность гостевого пропуска в CRM; при успехе — открытие турникета."),
        h("3.5. Удалённое открытие из админки", 3),
        p("Администратор в CRM: Франшиза → Клуб → «Открыть дверь сейчас». CRM создаёт команду open_door. Шлюз забирает её через long-poll GET /api/v1/gateway/commands и подтверждает выполнение."),
        h("4. Формат QR-кода", 2),
        mono("FITNESSCLUB:ENTRY:<user_id>:<timestamp>"),
        table(
            ["Сегмент", "Описание", "Пример"],
            [
                ["FITNESSCLUB", "Префикс системы", "фиксированный"],
                ["ENTRY", "Тип операции", "ENTRY или GUEST"],
                ["<user_id>", "ID пользователя в CRM", "123 или user-123"],
                ["<timestamp>", "Метка времени генерации", "7 символов base62"],
            ],
        ),
        p("QR обновляется каждые 15 секунд. CRM отклоняет код старше 15 секунд (reason: qr_expired)."),
        p("Пример: FITNESSCLUB:ENTRY:42:K7mN2pQ"),
        h("5. API-интеграция", 2),
        h("5.1. Эндпоинты облачной CRM (для шлюза)", 3),
        p("Базовый URL: https://<домен-crm>/api/v1/gateway"),
        table(
            ["Метод", "Путь", "Назначение"],
            [
                ["POST", "/access/entry", "Проверка QR на вход"],
                ["POST", "/access/exit", "Фиксация выхода"],
                ["POST", "/heartbeat", "Мониторинг связности шлюза"],
                ["GET", "/commands", "Long-poll очереди команд"],
                ["POST", "/commands/{id}/ack", "Подтверждение выполнения команды"],
            ],
        ),
        p("Авторизация: Authorization: Bearer <gateway_token>. Токен уникален для каждого клуба."),
        h("5.2. Эндпоинты PERCo-Web (локально, из шлюза)", 3),
        table(
            ["Метод", "Путь", "Назначение"],
            [
                ["POST", "/api/system/auth", "Авторизация (login/password → JWT)"],
                ["GET", "/api/devices", "Список устройств"],
                ["GET", "/api/devices/{id}", "Карточка устройства"],
                ["POST", "/api/devices/{id}/command", "Команда открытия/закрытия турникета"],
                ["GET", "/api/events/identifications", "События идентификации (сканирования QR)"],
                ["GET", "/api/reports/indoor", "Отчёт «находящиеся внутри»"],
                ["GET", "/api/reports/time", "Отчёт по времени проходов"],
            ],
        ),
        h("5.3. Команды CRM → шлюз", 3),
        table(
            ["kind", "Действие"],
            [
                ["open_door", "Открыть устройство в PERCo"],
                ["perco_call", "Произвольный вызов API PERCo"],
                ["ping", "Проверка связи"],
            ],
        ),
        h("5.4. Коды отказа CRM", 3),
        table(
            ["reason", "Описание"],
            [
                ["invalid_format", "Неверный формат QR"],
                ["qr_expired", "QR старше 15 секунд"],
                ["user_not_found", "Пользователь не найден"],
                ["user_blocked", "Пользователь заблокирован"],
                ["no_active_subscription", "Нет активного абонемента"],
                ["subscription_wrong_club", "Абонемент не для этого клуба"],
                ["guest_pass_invalid", "Недействительный гостевой пропуск"],
                ["unauthorized", "Неверный gateway_token"],
            ],
        ),
        h("6. Безопасность и аутентификация", 2),
        table(
            ["Уровень", "Механизм"],
            [
                ["CRM ↔ Шлюз", "Bearer-токен (gateway_token), уникальный на клуб"],
                ["Шлюз ↔ PERCo", "Логин/пароль PERCo-Web → JWT"],
                ["QR-код", "Динамический, TTL 15 сек; привязка к user_id"],
                ["Транспорт", "HTTPS (TLS) для CRM"],
                ["Сеть", "Только исходящие соединения от шлюза"],
            ],
        ),
        p("В продакшене франшизы PERCO_OPEN_FROM_CRM=0 — CRM не обращается к PERCo напрямую."),
        h("6.1. Журналирование", 3),
        p("Все попытки прохода записываются в таблицу access_logs: user_id, club_id, raw_data, device_id, event_type (entry/exit), result (granted/denied), reason, created_at."),
        h("7. Сетевая инфраструктура", 2),
        table(
            ["Направление", "Протокол", "Порт", "Назначение"],
            [
                ["ПК шлюза → CRM", "HTTPS", "443", "API gateway, heartbeat"],
                ["ПК шлюза → PERCo (LAN)", "HTTP/HTTPS", "80/8443", "Команды, события"],
                ["PERCo C01 ↔ Агент (LAN)", "WebSocket/TCP", "8765", "События считывателя (вариант Agent)"],
            ],
        ),
        p("Требования: ПК в клубе с Python 3.10+ или Windows; стабильный исходящий HTTPS; PERCo-Web в LAN; контроллер C01; QR-считыватель на PERCo."),
        p("Шлюз отправляет heartbeat каждые 30 секунд. Статус отображается в админке CRM (Франшиза)."),
        h("8. Конфигурация", 2),
        p("В админке CRM (Настройки → Франшиза / Шлюзы): gateway_token, perco_base_url, perco_login/password, perco_entry_device_id."),
        p("На ПК клуба — config.ini шлюза:"),
        mono("[crm]"),
        mono("base_url = https://<домен-crm>"),
        mono("gateway_token = <токен из админки>"),
        mono("[perco]"),
        mono("base_url = https://<ip-perco-в-lan>"),
        mono("entry_device_id = <id устройства>"),
        mono("events_enabled = 1"),
        h("9. Логика проверки абонемента", 2),
        p("При входе CRM проверяет: календарь (абонемент на текущую дату); статус active; привязку к клубу; лимит посещений (visits_total/visits_used)."),
        h("10. Обрабатываемые персональные данные", 2),
        p("В рамках интеграции СКУД CRM обрабатывает: ID пользователя, ФИО, телефон (для журнала), строку QR (raw_data), время прохода."),
        p("Прямая передача ПДн в PERCo не осуществляется — в PERCo уходит только команда открытия устройства."),
        h("11. Установка и эксплуатация", 2),
        p("1. Установить PERCo-Web и контроллер C01."),
        p("2. Настроить считыватель на передачу полного содержимого QR."),
        p("3. В CRM: создать клуб, сгенерировать gateway_token, указать параметры PERCo."),
        p("4. На ПК клуба: установить шлюз, заполнить config.ini, выполнить healthcheck."),
        p("5. Запустить шлюз как сервис."),
        p("6. Провести тестовый проход с QR из приложения."),
        h("12. Контакты и ссылки", 2),
        table(
            ["Ресурс", "URL / путь"],
            [
                ["Документация шлюза", "crm-backend-symfony/scripts/turnstile_gateway/README.md"],
                ["Документация агента", "crm-backend-symfony/scripts/club_agent/README.md"],
                ["API PERCo-Web", "https://ru.percoweb.com/dev"],
            ],
        ),
        h("Приложение А. Последовательность входа", 2),
        p("1. Приложение генерирует QR (TTL 15 сек)."),
        p("2. Считыватель сканирует QR → PERCo фиксирует событие."),
        p("3. Шлюз опрашивает PERCo events → извлекает FITNESSCLUB:ENTRY:..."),
        p("4. Шлюз → CRM: POST /access/entry."),
        p("5. CRM проверяет абонемент → access_granted + open_device."),
        p("6. Шлюз → PERCo: POST /api/devices/{id}/command → турникет открывается."),
    )
    return "".join(blocks)


def main() -> None:
    body = build_document_body()
    document_xml = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
        f"<w:body>{body}<w:sectPr>"
        "<w:pgSz w:w='11906' w:h='16838'/>"
        "<w:pgMar w:top='1134' w:right='1134' w:bottom='1134' w:left='1701'/>"
        "</w:sectPr></w:body></w:document>"
    )

    content_types = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

    rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    doc_rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>"""

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("[Content_Types].xml", content_types)
        zf.writestr("_rels/.rels", rels)
        zf.writestr("word/_rels/document.xml.rels", doc_rels)
        zf.writestr("word/document.xml", document_xml.encode("utf-8"))

    print(f"Created: {OUT}")


if __name__ == "__main__":
    main()
