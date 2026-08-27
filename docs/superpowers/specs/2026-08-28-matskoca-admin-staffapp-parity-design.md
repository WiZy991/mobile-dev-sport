# Design: MatskocaAdmin full parity with Android staffapp

**Date:** 2026-08-28  
**Scope:** iOS `MatskocaAdmin` ↔ Android `staffapp` 1:1 screens + logic; CRM-driven data; staff entry QR respects club `entry_qr_format` (iOS + Android + gateway).

## Goal

Admin/specialist iPhone app must match every Android staffapp screen and flow. No hardcoded business data — CRM/API. Staff hall QR uses the same scanner mode as the client app for that club (`ascii` | `wiegand`).

## Decisions

- **Parity baseline:** Android `staffapp` is source of truth for UX and flows.
- **One delivery:** all missing surfaces in one program of work (not phased releases), verified screen-by-screen against Android.
- **QR (option B):** follow `clubs.entry_qr_format` of the staff member’s **active rental club**.
  - `ascii` → `FITNESSCLUB:STAFF:{staffUserId}:{base62Ts7}` (current Android).
  - `wiegand` → same 7-digit `WiegandEntryQrCodec` as client, encoding `staffUserId`.
- **Gateway:** numeric Wiegand payload → try **staff** with valid rental for gate club first; else client ENTRY path (avoids staff/client id collision).
- **Android staffapp** updated to the same QR rules (not iOS-only).
- Hardcoded allowed only: brand strings, API base URL, UI chrome; legal PDFs may stay bundled if Android does, with URLs from CRM when available.

## Screen inventory (must match Android)

### Already on iOS (tighten to 1:1)

| Surface | Android | iOS target |
|---------|---------|------------|
| Login / register | `LoginScreen` | `LoginView` |
| Work tabs | Home / Schedule / Clients / Profile / Support | `WorkView` |
| Admin hub | `AdminHubScreen` | `AdminHubView` |
| Admin section | `AdminSectionScreen` | `AdminSectionView` |
| Client detail | `ClientDetailScreen` | `ClientDetailView` |

Parity checklist per tab: same CRM gates (`appSections` / `adminSections`), metrics, feeds, actions, empty/error states, pull-to-refresh.

### Missing on iOS (build)

| Surface | Android reference | Notes |
|---------|-------------------|--------|
| Onboarding | `OnboardingActivity` / `OnboardingScreen` | Approval → rental pay → trainer profile |
| Rental | `RentalActivity` / `RentalScreen` | Clubs, pay, history, consent |
| Entry QR sheet + full screen | `StaffEntryQrCard` / `StaffEntryQrActivity` | 15s rotation; rental gate; active club |
| Active club picker | Profile / rental | Paid clubs → set active for QR |
| Trainer profile edit | `TrainerProfileActivity` | Required fields as Android |
| Schedule create/edit | `CreateSessionDialog` | |
| Assign client | `AssignClientDialog` | |
| Staff feedback | `StaffFeedbackActivity` | |
| Legal PDFs / consent | `LegalPdfScreen`, purchase consent | Bundled assets + CRM links |
| Payment deep link | `staffapp://payment/callback` | iOS URL scheme equivalent |

## QR design

### Inputs

- `staffUserId` (int)
- Active club `entry_qr_format` from CRM (onboarding/rental/home payload or `GET` club / rental status)
- Clock ms, 15s validity (same as client / current STAFF ascii)

### Encode

| Format | Payload |
|--------|---------|
| `ascii` | `FITNESSCLUB:STAFF:{id}:{base62(ms)}` |
| `wiegand` | `WiegandEntryQrCodec.encode(staffUserId, ms)` → 7 digits |

### Gateway

1. If payload is Wiegand-numeric: resolve candidate id from codec.
2. If `StaffUser` exists **and** approved **and** valid rental for **gate club** → `handleStaffEntry` semantics (granted).
3. Else → existing client ENTRY Wiegand path.
4. ASCII `FITNESSCLUB:STAFF:…` unchanged.

### Apps

- MatskocaAdmin: generate per format; show blocked UI when rental inactive (`StaffRentalAccess` parity).
- staffapp Android: replace always-STAFF-ascii with format switch; pass `entry_qr_format` from CRM.

## CRM / API

Reuse existing staff endpoints (`/api/v1/staff/...`): auth, config, onboarding, rental, clients, bookings/schedule, support, trainer profile, admin sections, push.

Additions only if parity blocked:

- Expose `entry_qr_format` (and club id/name) on rental/onboarding/home payloads used for QR (avoid extra round-trips if already present).
- Gateway Wiegand staff-first resolution (above).

No new “preferred club” table beyond existing staff active club / rental.

## Data rules

- Sections, roles, metrics, lists, rental amounts, clubs: **API only**.
- Specialization catalog: from backend if endpoint exists; else same default list as Android until CRM owns it (track as follow-up, do not invent parallel hardcode).
- Club timezone for rental day: prefer CRM/club TZ; if Android still hardcodes Vladivostok, match Android then move both to CRM later.

## Verification (definition of done)

For **every** Android screen/dialog:

1. Side-by-side checklist: layout blocks, CTAs, copy, loading/error/empty.
2. Same API calls and success/failure behavior.
3. Role with/without each `appSections` key.
4. QR: ascii club shows STAFF string; wiegand club shows 7 digits; gateway opens door for staff rental; client QR still works.
5. No client WorldFitness regression.

## Non-goals

- Rewriting CRM web admin UI.
- Changing client ENTRY Wiegand codec length/Luhn.
- staffapp2 empty scaffold.
- Pixel-perfect font metrics if system fonts differ; visual structure and behavior must match.

## Rollout

1. Spec + plan committed.
2. Gateway staff-Wiegand resolution + API `entry_qr_format` on staff QR context.
3. Android staffapp QR format switch (keeps ascii clubs working).
4. MatskocaAdmin: missing flows in dependency order (onboarding/rental/QR → schedule CRUD → feedback/profile/legal → UI polish pass).
5. Screen-by-screen verification until 1:1.
