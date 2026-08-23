# Академия Борьбы — отдельное Android-приложение (Google Play / RuStore)

Тот же код, что Доброзал (`FitnessClub`), другой `applicationId` и **своя организация в CRM**.

| Flavor | applicationId | Имя | CRM org |
|--------|---------------|-----|---------|
| `dobrozal` | `ru.worldcashfit.app` | Доброзал | default (без заголовка) |
| `academyWrestling` | `ru.academywrestling.app` | Академия Борьбы | `X-Organization-Slug: <slug>` |

## Привязка к организации в CRM

CRM уже multi-tenant. Приложение Академии шлёт на каждый запрос:

`X-Organization-Slug: <slug из BuildConfig.ORGANIZATION_SLUG>`

Slug берётся из формы организации в платформенной админке (поле «slug», например `fitpower`).

Сейчас в `app/build.gradle.kts` для `academyWrestling`:

```kotlin
buildConfigField("String", "ORGANIZATION_SLUG", "\"akademiy-borbi\"")
```

После этого `/club/info`, адреса, настройки, залы, регистрация — данные этой org, не Доброзала.

## Android Studio

1. Открыть `FitnessClub`
2. **Build Variants** → `academyWrestlingDebug` / `academyWrestlingRelease`
3. Run / Generate Signed Bundle → новое приложение в Play с `ru.academywrestling.app`

## Deep links (важно)

У каждого flavor **своя** URI-схема, иначе при двух установленных APK Android открывает чужое приложение после Сбер ID / оплаты:

| Flavor | Auth callback | Payment callback |
|--------|---------------|------------------|
| `dobrozal` | `dobrozal://auth/callback` | `dobrozal://payment/callback` |
| `academyWrestling` | `academywrestling://auth/callback` | `academywrestling://payment/callback` |

Клиент передаёт `app_bridge_uri` при старте Сбер PKCE; сервер кладёт его в signed `state` и редиректит на нужную схему.

**Важно:** Сбер ID в приложении открывается во встроенном WebView (не Custom Tabs), чтобы callback не уходил в соседний APK на том же телефоне.

## Firebase

`app/src/academyWrestling/google-services.json` — заменить PLACEHOLDER файлом из Firebase.
