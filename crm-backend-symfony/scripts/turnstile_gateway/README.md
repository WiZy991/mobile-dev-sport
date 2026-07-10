# Шлюз клуба (turnstile_gateway)

ПК-приложение, которое ставится в **каждом клубе франшизы** и связывает облачный CRM
с локальной СКУД PERCo-Web в LAN клуба.

## Зачем это нужно

Облачный CRM на VPS не может (и не должен) ходить во внутренние сети сотен клубов:
у клубов могут совпадать локальные IP-адреса, нет публичных адресов у PERCo, и пробрасывать
PERCo наружу — небезопасно. Шлюз решает задачу инверсией соединения: ПК внутри клуба
сам ходит на CRM по HTTPS (как браузер), а CRM передаёт ему задания.

```
[Приложение] → CRM (cloud) ──long-poll──→ [Шлюз клуба] → PERCo-Web (LAN) → турникет
                  ▲                            │
                  └──── /api/v1/gateway/* ─────┘
```

## Что умеет

- **Вход по QR (stdin)**: считыватель → stdin шлюза → `POST /api/v1/gateway/access/entry`.
- **Вход по QR (PERCo events)**: шлюз поллит `GET /api/events/identifications`, извлекает
  `FITNESSCLUB:...` и отправляет в CRM (без подключения сканера к ПК).
- Если CRM разрешает вход и в ответе есть `open_device` — шлюз сам шлёт команду в локальный PERCo-Web.
- **Удалённое открытие из админки**: «Франшиза → клуб → Открыть дверь сейчас» создаёт
  команду в очереди, шлюз забирает её через long-poll.
- **Heartbeat**: статус «онлайн» каждого клуба видно в `/admin/franchise`.
- **Произвольные вызовы PERCo**: команда `perco_call` позволяет дёргать любой эндпоинт
  PERCo-Web (методы PERCo-Web см. https://ru.percoweb.com/dev — `accessReportsIndoorGET`
  и др. покрыты в `perco_client.py`, остальное — через generic `call(method, path)`).

## Файлы

| Файл                  | Назначение                                                          |
|-----------------------|---------------------------------------------------------------------|
| `gateway.py`          | Демон + CLI: `daemon`, `qr`, `open-now`, `healthcheck`              |
| `perco_client.py`     | Клиент PERCo-Web (auth, devices, staff, identifications, events, reports) |
| `config.example.ini`  | Шаблон конфигурации                                                 |

## Установка

Требуется Python 3.10+. Сторонние пакеты не нужны — только стандартная библиотека.

### 1. Получить токен в админке CRM

1. Войти в CRM → **Настройки → Франшиза / Шлюзы**.
2. Открыть карточку нужного клуба, нажать **«Сгенерировать токен»**, скопировать.
3. На той же странице заполнить **PERCo-Web** (URL, логин, пароль, ID устройства входа).

### 2. На ПК клуба

```bash
git clone ... mobile-dev-sport
cd mobile-dev-sport/crm-backend-symfony/scripts/turnstile_gateway
cp config.example.ini config.ini
# отредактируйте config.ini, вставьте gateway_token и параметры PERCo
python3 gateway.py healthcheck
```

`healthcheck` должен напечатать `CRM heartbeat: HTTP 200 …` и `PERCo auth: OK`. Если нет —
смотрите вывод, обычно это адрес, токен или сертификат.

### 3. Запуск как сервис

#### Linux (systemd)

`/etc/systemd/system/fc-gateway.service`:

```ini
[Unit]
Description=FitnessClub gateway
After=network-online.target

[Service]
Type=simple
User=fc
WorkingDirectory=/opt/fc-gateway
ExecStart=/usr/bin/python3 gateway.py daemon
Restart=on-failure
RestartSec=5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now fc-gateway
journalctl -u fc-gateway -f
```

#### Windows (Task Scheduler)

1. `Win+R` → `taskschd.msc` → **Create Task**.
2. Trigger: **At startup**.
3. Action: `python.exe`, аргументы: `C:\fc-gateway\gateway.py daemon`,
   рабочая папка: `C:\fc-gateway`.
4. Run whether user is logged on or not, Highest privileges.
5. (Опционально) Установить NSSM и завернуть в полноценный Windows-сервис, если нужны автоперезапуски и логи.

### 4. Считыватель в режиме «клавиатуры»

Считыватели обычно имитируют клавиатуру: каждый QR — строка + Enter. Подключите его
ввод к stdin шлюза одним из способов:

- Запуск шлюза в терминале с фокусом — он сам читает строки из stdin.
- Скрипт-обёртка, которая принимает QR и зовёт `python3 gateway.py qr "<text>"`
  (так удобнее, если нужен отдельный UI / TTL-проверка).

### 5. Считыватель подключен к PERCo (без stdin)

Если сканер подключен к контроллеру PERCo, а не к USB ПК, включите poller событий:

- В `config.ini` секция `[perco]`:
  - `events_enabled = 1`
  - `events_poll_interval = 1`
  - `events_page_size = 50`
  - `only_fitnessclub_qr = 1`
- Запустите `python3 gateway.py -v daemon` и сканируйте QR с приложения.
- В логе должны появляться строки `PERCo event → QR найден: FITNESSCLUB:...`.

## Команды CLI

```bash
python3 gateway.py daemon        # основной режим: heartbeat + long-poll команд (+ stdin QR)
python3 gateway.py qr "<text>"   # один QR → CRM → PERCo
python3 gateway.py open-now      # открыть турникет один раз (без CRM)
python3 gateway.py healthcheck   # проверка связи с CRM и PERCo
python3 gateway.py -v daemon     # режим verbose
```

## Команды от CRM (kind)

| kind        | payload                                                            | действие                                       |
|-------------|--------------------------------------------------------------------|------------------------------------------------|
| `open_door` | `{device_id?, cmd_number?, cmd_type?, cmd_value?, cmd_param?}`     | открыть устройство в локальном PERCo            |
| `perco_call`| `{method, path, params?, json?}`                                    | сырой вызов PERCo (например `GET /api/devices`) |
| `ping`      | `{...}`                                                              | эхо-ответ для проверки связи                    |

Если поля в `open_door` пустые — берутся значения из `config.ini` (`entry_device_id`, `cmd_*`).

## Эндпоинты PERCo-Web в `perco_client.py`

Из методов, перечисленных на https://ru.percoweb.com/dev, типизированы самые
нужные для CRM:

- **Auth**: `authenticate()`
- **Устройства**: `devices_list()`, `device_get(id)`, `device_command(id, cmd_*)`
- **Сотрудники / посетители**: `staff_list()`, `staff_create()`, `staff_update()`, `staff_delete()`,
  `assign_main_card(user_id, identifier)`
- **Идентификаторы (карты/коды)**: `identifications_list()`, `identification_create()`, `identification_delete()`
- **События**: `events()`, `events_identifications()`
- **Отчёты доступа**: `report_indoor()` (`accessReportsIndoorGET`), `report_time()` (`accessReportsTimeGET`)
- **Любой другой**: `call(method, path, json_body=, params=)` — через `perco_call`-команду из CRM
  или прямо в коде шлюза.

## Диагностика

| Симптом                                                  | Что проверять                                          |
|----------------------------------------------------------|--------------------------------------------------------|
| `CRM 401 unauthorized`                                   | `gateway_token` в `config.ini` совпадает с тем, что в админке CRM |
| `PERCo auth failed`                                      | URL/логин/пароль; снять галку SSL, если стенд          |
| Считыватель пищит, но CRM не получает QR                 | Если сканер в USB ПК: stdin не читается. Если сканер в PERCo: проверьте `events_enabled=1` и что в событии есть строка `FITNESSCLUB:` |
| Команда `open_door` уходит в `failed`                    | Посмотрите `journalctl -u fc-gateway` / лог ошибки PERCo. ID устройства корректный? Контроллер в сети? |
| В CRM «шлюз офлайн»                                       | Сервис запущен? Сеть на ПК есть? `python3 gateway.py healthcheck` |
| Старые `/api/v1/access/entry` нужны для других клиентов  | Эндпоинт оставлен для обратной совместимости — `ACCESS_GATE_TOKEN` всё ещё работает; новый поток — через `/api/v1/gateway/access/entry` с `Authorization: Bearer <gateway_token>` |

## Безопасность

- Токен `gateway_token` уникален для каждого клуба, легко ротируется кнопкой в админке.
- Облачная CRM никогда не получает прямой доступ к PERCo: все вызовы инициирует шлюз изнутри LAN.
- Реки трафика только наружу — никаких входящих портов на ПК клуба открывать не нужно.
- Рекомендуется развернуть CRM по HTTPS и оставить `verify_ssl = 1` в проде.

## Единый чеклист «осталось только подключить оборудование»

Используйте перед выездом на объект:

1. `python3 gateway.py healthcheck` возвращает `CRM heartbeat: HTTP 200` и `PERCo auth: OK`.
2. В CRM-клубе создан `gateway_token`, применены миграции (включая `access_alarms`).
3. Команда из админки `Открыть дверь` доходит до `done` через `/api/v1/gateway/commands`.
4. Если используется камера Dahua anti-tailgating — проверка выполняется в `scripts/club_agent`
   через кнопку `Проверить готовность ПО` (используется Dahua CGI `eventManager.cgi?action=attach`).
5. После пунктов выше остаются только физические шаги: сеть/питание, IP-адреса, подключение турникета,
   размещение камеры и калибровка порогов/направления.
