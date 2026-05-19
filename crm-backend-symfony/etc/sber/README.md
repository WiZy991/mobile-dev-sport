Каталог PKCS#12 для **mTLS при вызовах `oauth.sber.ru`** (обмен авторизационного кода).

- Файл `sber_certificate.p12` копируется в Docker-образ (`COPY . .` из корня приложения → путь в контейнере `/app/etc/sber/sber_certificate.p12`).
- Пароль задаётся в `.env`: `SBER_ID_MTLS_PKCS12_PASS`.

После `git pull` на сервере выполните пересборку образа приложения (`docker compose build app` или `deploy-https`/ваш деплой), иначе в образ может не попасть новый `.p12`.

Заполнение CRM (ФИО, телефон, email, паспорт) идёт из **GET userinfo** (`SBER_ID_USERINFO_URL`, заголовок `x-introspect-rquid`).

- **ФИО / телефон / email** — scope `name`, `mobile`, `email` (пакеты Standard / Light).
- **Паспорт** — только scope **`maindoc`** → в JSON поле **`identification`** (пакет Professional в кабинете [id.sber.ru](https://id.sber.ru)). Без `maindoc` в выданном токене паспорт в CRM не появится, хотя вход будет успешным.
- В списке клиентов зелёная галочка в колонке «Паспорт» — только если заполнены **серия и номер** в карточке.

## Лог JSON userinfo (как в instruction.txt)

После каждого входа через Сбер ID в контейнере пишется файл:

`/app/var/log/sber-userinfo.log`

Смотреть в реальном времени:

```bash
docker exec -it crm_backend_app tail -f /app/var/log/sber-userinfo.log
```

Последний блок (между разделителями `---`):

```bash
docker exec crm_backend_app tail -n 80 /app/var/log/sber-userinfo.log
```

Каждый блок между `---` — **тот же JSON**, что в ответе GET userinfo в инструкции (шаг 3): `sub`, `family_name`, `identification`, `phone_number`, … без дополнительных полей-обёрток.
