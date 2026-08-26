# Per-club entry QR format Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Admins set `entry_qr_format` (`ascii`|`wiegand`) per club; iOS and Android generate entry QR from that field instead of hardcoding club id 11.

**Architecture:** Store format on `clubs`, expose in club list/detail and user profile (`entry_qr_format` next to `club_id`). Mobile apps pass format into existing Wiegand vs ASCII generators.

**Tech Stack:** Symfony/Doctrine, Twig admin, SwiftUI iOS, Kotlin Android.

## Global Constraints

- Values: only `ascii` and `wiegand`; default `ascii`.
- Seed club id 11 → `wiegand`.
- Staffapp out of scope.
- Commits: author Stepan only; **no** `Co-authored-by: Cursor` (use `git commit-tree` if the environment injects trailers).
- Do not push unless asked.

---

### Task 1: CRM schema + entity

**Files:**
- Create: `crm-backend-symfony/migrations/Version20260826183000.php`
- Modify: `crm-backend-symfony/src/Entity/Club.php`

- [ ] Add column `entry_qr_format` VARCHAR(16) NOT NULL DEFAULT `'ascii'`; UPDATE id=11 to `wiegand`.
- [ ] Entity field + getters/setters; constants `FORMAT_ASCII` / `FORMAT_WIEGAND`.
- [ ] Commit (no co-author trailer).

### Task 2: CRM admin + API

**Files:**
- Modify: `AdminFranchiseController.php` (save)
- Modify: `templates/admin/franchise/edit.html.twig`
- Modify: `ClubController.php` (list + show)
- Modify: `MobileAuthTokenIssuer.php` (`userArray`)

- [ ] Radio `entry_qr_format` on franchise edit; validate on save.
- [ ] Expose `entry_qr_format` in clubs list/show and user payload.
- [ ] Commit.

### Task 3: iOS

**Files:**
- Modify: `APIModels.swift` (`User.entryQrFormat`)
- Modify: `QRCodeGenerator.swift`
- Modify: `QrViews.swift`
- Modify: `QRCodeGeneratorTests.swift`

- [ ] Parse `entry_qr_format` on User.
- [ ] `usesWiegandNumeric(format:)` / `entryPayload(..., entryQrFormat:)`; remove hardcoded `["11"]`.
- [ ] Wire QrViews from `currentUser?.entryQrFormat`.
- [ ] Update tests; commit submodule + parent pointer.

### Task 4: Android

**Files:**
- Modify: `User.kt`
- Modify: `WiegandEntryQrCodec.kt`
- Modify: `QrCodeViewModel.kt`

- [ ] Field `entryQrFormat` on User.
- [ ] Drive Wiegand vs ASCII from format; remove `setOf("11")`.
- [ ] Commit.

---

**Spec coverage:** schema, admin, API clubs + profile, iOS, Android, seed 11, fallback ascii — all covered. Staffapp excluded.
