Каталог PKCS#12 для **mTLS при вызовах `oauth.sber.ru`** (обмен авторизационного кода).

- Файл `sber_certificate.p12` копируется в Docker-образ (`COPY . .` из корня приложения → путь в контейнере `/app/etc/sber/sber_certificate.p12`).
- Пароль задаётся в `.env`: `SBER_ID_MTLS_PKCS12_PASS`.

После `git pull` на сервере выполните пересборку образа приложения (`docker compose build app` или `deploy-https`/ваш деплой), иначе в образ может не попасть новый `.p12`.

Заполнение CRM (ФИО, телефон, email, паспорт из `identification` / `priority_doc`) идёт из **GET userinfo**: задайте `SBER_ID_USERINFO_URL`, запрос отправляет заголовок `x-introspect-rquid` (инструкция Сбер ID). В кабинете приложения нужны согласия под scope из `SBER_ID_OIDC_SCOPE` (или уменьшите переменную, если какой‑то пакет не подключён).
