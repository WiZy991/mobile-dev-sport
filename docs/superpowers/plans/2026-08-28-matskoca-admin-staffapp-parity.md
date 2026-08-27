# MatskocaAdmin ↔ staffapp Full Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** iOS MatskocaAdmin matches Android staffapp 1:1; staff QR follows club `entry_qr_format`; gateway resolves Wiegand staff before client.

**Architecture:** Extend CRM payloads + gateway; update Android QR encoder; build missing MatskocaAdmin flows against existing `/api/v1/staff/*` APIs; verify screen-by-screen.

**Tech Stack:** Symfony gateway/onboarding, Kotlin staffapp, SwiftUI MatskocaAdmin.

**Spec:** `docs/superpowers/specs/2026-08-28-matskoca-admin-staffapp-parity-design.md`

---

## Progress

### Task 1: CRM — entry_qr_format on rental clubs + gateway staff-Wiegand — DONE (code)

- [x] Add `entry_qr_format` to each rental club row and onboarding active club.
- [x] On Wiegand entry: if StaffUser(id) approved + valid rental for gate club → staff grant; else client path.
- [x] On Wiegand exit: same staff-first then client.
- [ ] Commit.

### Task 2: Android staffapp QR format switch — DONE (code)

- [x] `buildStaffEntryQr(staffId, ms, entryQrFormat)` → ascii STAFF or Wiegand encode.
- [x] Wire format from active club / onboarding.
- [ ] Commit.

### Task 3: MatskocaAdmin — QR + rental access helpers — DONE (code)

- [x] Encode ascii/wiegand; 15s rotation; rental gate UI.
- [ ] Commit.

### Task 4: MatskocaAdmin — Onboarding + Rental + Active club — DONE (code)

- [x] Port Onboarding / Rental / active club picker (+ payment URL open).
- [ ] Payment deep-link callback + full consent dialog polish.
- [ ] Commit.

### Task 5: MatskocaAdmin — Trainer profile + Legal PDFs + Feedback — DONE (code)

- [x] Trainer profile + feedback screens/API.
- [x] Bundled legal PDFs / LegalPdfView + consent dialog.
- [ ] Commit.

### Task 6: MatskocaAdmin — Schedule create/edit + assign client — DONE (code)

- [x] Create/edit session sheet + API.
- [x] Assign client dialog; cancel booking.
- [ ] Commit.

### Task 7: Polish existing Work/Admin/Client to 1:1 + verification pass — DONE (code)

- [x] Critical gaps closed (photo, profile card, schedule week, client tabs, feedback categories, home→assign).
- [x] Medium gaps closed (login legal, push banner, gated CTAs, copy/filters).
- [x] Build succeeded on iPhone 17 simulator.
- [ ] Manual device QA vs Android side-by-side.
- [ ] Commit.
