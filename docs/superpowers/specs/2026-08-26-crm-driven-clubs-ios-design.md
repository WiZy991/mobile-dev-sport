# Design: CRM-driven clubs in iOS registration & home

**Date:** 2026-08-26  
**Scope:** CRM + iOS (Android later)

## Goal

Stop hardcoding registration venues and club contacts in the iOS app. Admins mark clubs as visible in the CRM; the app loads name, address, contacts, image, occupancy, and map from the API by `club_id`.

## Decisions

- Per-club settings on franchise club edit (not org-wide settings only).
- Checkbox **«Показывать в приложении»** (`show_in_app`).
- Address as city/street text; **no manual lat/lon** — coordinates resolved from address / existing DB values.
- Registration card image: **upload in CRM** → public URL in API.
- `GET /api/v1/clubs` returns **only** `show_in_app = true`.
- iOS registration binds user via existing `club_id` on register/profile.
- Home occupancy / hall name / club detail already prefer `user.clubId`; extend contacts to come from club payload, not `AppConfiguration` placeholders.

## CRM

### Schema

- `clubs.show_in_app` BOOLEAN NOT NULL DEFAULT false
- `clubs.registration_image_path` VARCHAR nullable (path under `public/uploads/…`)
- Reuse existing `phone`, `email`, `working_hours`, `address`, `name`

Seed: set `show_in_app = true` for known open halls (e.g. ids 2 and 11) if present.

### Admin (`/admin/franchise/{id}`)

Editable fields:

- name, address (город/улица)
- phone, email, working hours
- checkbox show_in_app
- image upload (registration / list card)
- existing PERCo / gateway fields unchanged

### API

**GET `/api/v1/clubs`**

- Filter `show_in_app = true`
- Each item: `id`, `name`, `address`, `phone`, `email`, `working_hours`, `amenities`, `max_capacity`, `latitude`, `longitude` (resolved), `image_url` (absolute), legal URLs as today

**GET `/api/v1/clubs/{id}`**

- Same club fields + `network` / legal as today; resolve coordinates by address

**GET `/api/v1/club/occupancy`**

- Unchanged (user club / `?club_id=`)

## iOS

### Registration

- Load clubs via `getClubs()` on club-pick screen
- Cards: image from `image_url` (AsyncImage / URL), title, address from API
- On select → `ClubItem` with API `id` → register with `club_id`
- Remove hard dependency on `RegistrationVenues` list (optional local asset fallback by id only if URL empty)
- Empty / error state if API returns no clubs

### Home & club info

- Keep preferred club from `currentUser.clubId`
- Occupancy + hall name from that club
- `ClubInfoView`: phone, email, hours, address, map from `getClubDetails`; social links from API `network` when present, not hardcoded AppConfiguration URLs when network provides them

### Out of scope

- Android parity
- Changing club after registration in-app
- Org-level `club/info` rewrite

## Success criteria

1. Admin checks «Показывать в приложении» on a club → it appears in iOS registration without an app release (after CRM deploy).
2. New user selecting that club gets `club_id` set in CRM.
3. Home shows that club’s occupancy, name, address, map, contacts from CRM.
