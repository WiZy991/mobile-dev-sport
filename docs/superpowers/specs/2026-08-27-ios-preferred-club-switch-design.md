# Design: iOS preferred club switch (Android parity)

**Date:** 2026-08-27  
**Scope:** WorldFitness iOS only. CRM unchanged. Mirror FitnessClub Android.

## Goal

User registers with one club (`users.club_id`) but can switch preferred club in Profile. Home occupancy/map, QR format (`entry_qr_format`), subscription list filter, and next purchase follow the preferred club. Existing subscriptions keep their own `club_id`; turnstile still validates sub vs gate club.

## Decisions

- Reuse `PUT /api/v1/user/profile` with `{ "club_id": "…" }` (`FitnessAPI.assignClub`).
- UI parity with Android `SelectPreferredClubScreen` + Profile «Выбрать другой клуб».
- Filter profile subscriptions by preferred `clubId` (show also legacy `club_id` null/0).
- Pass `club_id` on subscription purchase init when API supports it.
- No network-wide single subscription.

## iOS changes

1. **Models:** `Subscription.clubId` (decode int/string); keep `clubName`.
2. **Profile:** button → select-club screen; list filtered by preferred club.
3. **SelectPreferredClubView:** `getClubs()`, highlight current, `assignClub`, `updateCachedUser`, dismiss.
4. **Home / QR:** already keyed off `currentUser.clubId` / `entryQrFormat` — refresh user after switch.
5. **Purchase:** include preferred `club_id` in payment/subscription init request if field exists on Android parity path.

## Non-goals

- CRM schema / new endpoints
- Staffapp
- Changing which club an existing subscription is bound to
