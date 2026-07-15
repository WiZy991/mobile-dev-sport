## CRM backend на Symfony для FitnessClub

Этот каталог содержит каркас backend‑части CRM/админки для мобильного приложения **FitnessClub**.

### Запуск backend + MySQL через Docker

1. Перейдите в каталог backend:

```bash
cd crm-backend-symfony
```

2. Убедитесь, что в `.env` указан Docker-хост базы:

```env
DATABASE_URL="mysql://app:app_password@database:3306/world_fitness?charset=utf8mb4&serverVersion=8.0.32"
```

3. Поднимите контейнеры:

```bash
docker compose up -d --build
```

4. Проверка:

```bash
docker compose ps
docker compose logs -f app
```

API будет доступен на `http://<server-ip>:8000/api/v1/`.

Примечание: при старте контейнер `app` автоматически создаёт БД (если её нет) и применяет актуальную схему через Doctrine (`doctrine:schema:update --force`).

### Как развернуть Symfony‑проект локально без Docker

1. Установите PHP (>= 8.2), Composer и любой веб‑сервер (Symfony CLI или nginx/apache).
2. Войдите в каталог `crm-backend`:

```bash
cd crm-backend
```

3. Создайте скелет Symfony **внутри этого каталога**:

```bash
composer create-project symfony/skeleton . 
composer require symfony/orm-pack symfony/maker-bundle symfony/twig-bundle symfony/security-bundle symfony/serializer-pack symfony/validator symfony/runtime nelmio/cors-bundle
```

4. Скопируйте/оставьте файлы из подкаталогов `src/`, `config/`, `templates/` из репозитория поверх сгенерированного скелета (они уже написаны под стандартную структуру Symfony).

5. Настройте подключение к БД в `.env`:

```env
DATABASE_URL="mysql://username:password@127.0.0.1:3306/fitness_crm?serverVersion=8.0"
```

6. Выполните миграции и запустите сервер:

```bash
php bin/console doctrine:database:create
php bin/console doctrine:migrations:migrate
symfony server:start -d
```

API будет доступен, например, по `http://127.0.0.1:8000/api/v1/`.

### Основные модули CRM

- **Auth & Users**: регистрация, вход, refresh/ logout, профиль пользователя.
- **Trainings & Bookings**: расписание тренировок, детали тренировки, бронь/отмена, лист ожидания.
- **Subscriptions**: абонементы пользователя, тарифные планы, заморозка/разморозка.
- **Trainers**: тренеры, их карточки и расписание.
- **CRM‑модули** (админка, левое меню):
  - Задачи, Клиенты, Посещения, Продажи, Услуги, Лиды, Сделки, Комментарии,
    Мессенджеры, Звонки, Самообслуживание, Касса, Склад, Аналитика, Персонал,
    Документы, Настройки и др.
  - Для каждого пункта меню созданы контроллеры‑заглушки и страницы в админке, в
    которые можно постепенно добавлять бизнес‑логику.

### Интеграция с мобильным приложением

Мобильное приложение уже использует интерфейс `FitnessApi` и mапится на следующие эндпоинты:

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/user/profile`
- `PUT /api/v1/user/profile`
- `GET /api/v1/trainings`
- `GET /api/v1/trainings/{id}`
- `GET /api/v1/bookings`
- `POST /api/v1/trainings/{id}/book`
- `DELETE /api/v1/bookings/{id}`
- `POST /api/v1/trainings/{id}/waiting-list`
- `GET /api/v1/subscriptions`
- `GET /api/v1/subscriptions/plans`
- `POST /api/v1/subscriptions/{id}/freeze?days=...`
- `POST /api/v1/subscriptions/{id}/unfreeze`
- `GET /api/v1/trainers`
- `GET /api/v1/trainers/{id}`
- `POST /api/v1/user/push-token`

В Symfony‑backend эти эндпоинты реализованы в `Api`‑контроллерах и возвращают JSON в том формате, который сейчас отдает `MockInterceptor` в Android‑приложении.

### Что ещё сделать

- Доработать бизнес‑логику каждого CRM‑модуля (продажи, склад, аналитика и т.п.).
- Подключить реальные платёжные шлюзы при необходимости.
- Настроить роли и права доступа (администратор, менеджер, тренер и т.д.).
- Постепенно отключать `MockInterceptor` в Android‑приложении (`USE_MOCK = false`) и переводить экраны на работу с реальным API.

### Шлюз и камера: только подключить оборудование

Для интеграции проходной используется связка:
- CRM API `POST /api/v1/gateway/access/entry|exit|alarm`, `POST /api/v1/gateway/heartbeat`, `GET /api/v1/gateway/commands`;
- локальный агент клуба (`scripts/club_agent`) или шлюз (`scripts/turnstile_gateway`);
- камера Dahua для IVS Tripwire в сценарии анти-tailgating.

Для камеры Dahua в этом проекте используется стандартный CGI endpoint:
- `GET /cgi-bin/eventManager.cgi?action=attach&codes=[CrossLineDetection]&heartbeat=5&channel=<n>`
- авторизация: Digest/Basic (зависит от конфигурации камеры).

Отдельный cloud SDK/API производителя для базового tripwire-сценария не требуется.

Перед выездом на объект пройдите preflight-чек:
1. Применены миграции, включая `Version20260626090000` (`access_alarms`).
2. В CRM у клуба заполнен `gateway_token`, heartbeat отвечает `HTTP 200`.
3. В агенте кнопка `Проверить готовность ПО` возвращает `ГОТОВО`.
4. Тест QR-входа и тест тревоги проходят на стенде.
5. После этого остаются только сеть/питание/IP/логин камеры, линия Tripwire и калибровка порогов.

### Деплой на сервер (`git pull`)

Файл `.env` **не хранится в git** — на проде он свой (пароли, БД, ключи). Шаблон: `.env.example`.

```bash
cd ~/mobile-dev-sport
git pull
cd crm-backend-symfony
docker compose up -d --build
```

Убедитесь, что в `crm-backend-symfony/.env` задан `APP_SECRET` (раньше он мог быть в `.env.dev`).

Если `git pull` всё ещё ругается на старый `.env` (до обновления репозитория):

```bash
git stash push -m "prod-env" -- crm-backend-symfony/.env
git pull
git stash pop
```

