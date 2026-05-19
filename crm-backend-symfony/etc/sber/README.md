Каталог PKCS#12 для **mTLS при вызовах `oauth.sber.ru`** (обмен авторизационного кода).

- Файл `sber_certificate.p12` копируется в Docker-образ (`COPY . .` из корня приложения → путь в контейнере `/app/etc/sber/sber_certificate.p12`).
- Пароль задаётся в `.env`: `SBER_ID_MTLS_PKCS12_PASS`.

После `git pull` на сервере выполните пересборку образа приложения (`docker compose build app` или `deploy-https`/ваш деплой), иначе в образ может не попасть новый `.p12`.
