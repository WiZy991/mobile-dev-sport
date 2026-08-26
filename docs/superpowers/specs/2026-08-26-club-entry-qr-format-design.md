# Design: Per-club entry QR format (Wiegand vs ASCII)

**Date:** 2026-08-26  
**Scope:** CRM + WorldFitness (iOS) + FitnessClub (Android). Staffapp — out of scope.

## Goal

Admins choose how the client app encodes the entry QR for each club (Wiegand digits like Sedanka, or ASCII `FITNESSCLUB:ENTRY:…` like Cooper). Apps stop hardcoding `club_id = 11`.

## Decisions

- Field on club: `entry_qr_format` with values `ascii` | `wiegand`.
- Default for all clubs: `ascii`.
- Migration seed: club id **11** → `wiegand` (if row exists).
- Format follows the client’s **home club** (`user.club_id`), not the physical scanner they stand at.
- Staffapp / trainer QR: later.
- CRM gateway already accepts both payloads — no access-path changes.

## CRM

### Schema

- `clubs.entry_qr_format` VARCHAR(16) NOT NULL DEFAULT `'ascii'`
- Allowed values: `ascii`, `wiegand` (reject others on save)

### Admin (`/admin/franchise/{id}`)

Radio (or select) **«Формат QR входа»**:

- `ascii` — «Как на Купере (текст FITNESSCLUB:ENTRY)»
- `wiegand` — «Wiegand (цифры, как Седанка)»

Saved with other franchise club fields.

### API

Expose `entry_qr_format` on:

- `GET /api/v1/clubs` (list item)
- `GET /api/v1/clubs/{id}`
- User profile / auth user payload (`MobileAuthTokenIssuer::userArray` and equivalents) next to `club_id`

Missing club → omit field or `null`; clients treat as `ascii`.

## iOS (WorldFitness)

- Parse `entry_qr_format` on `User` / club models from API.
- `QRCodeGenerator.entryPayload(...)` uses format string (or cached flag), **not** hardcoded Set `["11"]`.
- When opening / rotating entry QR, use format from refreshed profile (or last cached profile).
- Fallback if field absent: `ascii`.
- Update unit tests accordingly.

## Android (FitnessClub)

- Same field on user/club models.
- `WiegandEntryQrCodec.usesWiegandNumeric` (or caller) driven by `entry_qr_format == "wiegand"`, remove `setOf("11")`.
- `QrCodeViewModel` keeps generating ASCII vs Wiegand from profile `club_id` + format.
- Fallback: `ascii`.

## Non-goals

- Changing Wiegand codec length / Luhn rules.
- Per-scan venue override (guest at another hall).
- Staffapp QR format setting.
- Forcing app update of clubs list solely for QR (profile field is enough).

## Rollout

1. Deploy CRM + run migration.
2. Confirm admin: Купера = ascii, Седанка = wiegand.
3. Ship iOS + Android builds that read `entry_qr_format`.
4. Until apps ship, old builds still hardcode `11` → Wiegand (compatible).
