# Технические меры безопасности (для проверки Сбер ID / опросник ДКБ)

Документ описывает **реализованные в коде** меры. Его можно приложить к опроснику ДКБ (п. 22, 26) или отправить интегратору Сбера по запросу.

## Как Сбер проверяет

Сбер **не имеет постоянного доступа** к вашему серверу. Обычно:

1. **Опросник ДКБ** — вы отмечаете пункты и прикладываете документы/URL (организационные пункты готовит юрист/ИБ).
2. **Техническая часть** — по запросу могут попросить: описание архитектуры, скриншоты CRM, подтверждение HTTPS, иногда **тестовый вход** через Сбер ID на стенде.
3. **Пакет Professional** — после одобления в кабинете [id.sber.ru](https://id.sber.ru) в токене появляется scope `maindoc`, в userinfo — блок `identification`.

Проверка **автоматически «зайти на сервер»** не выполняется — только то, что вы предоставите + работа OAuth на проде/IFT.

## Архитектура (п. 26)

```
[Android app] --HTTPS--> [nginx :443] --HTTP--> [Symfony app :8000]
                              |
                         TLS (Let's Encrypt)
[Sber oauth.sber.ru] <--mTLS PKCS12--> [Symfony] --GET userinfo-->
[MySQL 8] <--docker network only--> (не публикуется на 0.0.0.0:3306 в prod compose)
```

## Реализованные меры в проекте

| Мера | Где |
|------|-----|
| HTTPS, редирект 80→443 | `docker/nginx/default.conf` |
| mTLS к API Сбера | `SberIdOAuthService`, `etc/sber/sber_certificate.p12` |
| PKCE + state + nonce | `SberOAuthPkceStateService`, `SberIdTokenValidator` |
| Bearer access-token (TTL 1 ч), refresh в БД | `MobileAuthTokenIssuer`, `CurrentUserResolver` |
| Запрет подмены пользователя через X-User-Id | `CurrentUserResolver` (только Bearer) |
| Блокировка клиента | `User.isBlocked`, `AuthController` |
| Паспорт после Сбер ID не редактируется из API | `User::isPassportLockedFromClientEdit`, `MobileClientPayloadApplier` |
| Удаление аккаунта / обезличивание ПДн | `DELETE /api/v1/user/account`, `UserAccountDeletionService` |
| Паспорт в CRM — только ROLE_ADMIN / SUPER_ADMIN | `PassportAccessPolicy`, `client_show.html.twig`, export |
| Лог userinfo без паспорта (по умолчанию выкл.) | `SBER_USERINFO_LOG=0`, `SberIdUserinfoJsonLogger` |
| MySQL не наружу в prod | `compose.yaml` (без `3306:3306`) |
| HTTP body в логах только debug-сборка Android | `AppModule.kt` + `BuildConfig.DEBUG` |

## Что показать на демо (если попросят)

1. Вход через Сбер ID в приложении → в CRM карточка клиента с паспортом (под admin).
2. Запрос к `/api/v1/user/profile` **без** Bearer → `401`.
3. Запрос с чужим `X-User-Id` без токена → `401`.
4. `docker compose ps` — MySQL без published port (prod).
5. Фрагмент этого файла или выгрузка из git (коммит с security).

## Переменные окружения

```env
MOBILE_API_ACCESS_TTL=3600
SBER_USERINFO_LOG=0
```

После деплоя: `php bin/console doctrine:migrations:migrate --no-interaction`

## Локальная разработка

Проброс MySQL на хост:

```bash
COMPOSE_FILE=compose.yaml:compose.dev.yaml docker compose up -d
```
