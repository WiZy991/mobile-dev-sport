# CRM Admin Performance Implementation Plan

> **For agentic workers:** Implement task-by-task. Steps use checkbox syntax.

**Goal:** Make Symfony admin CRM responsive at ~3400+ clients via AJAX pickers, pagination, default date windows, and N+1 fixes.

**Architecture:** Reuse existing `crm-client-picker` + `/admin/clients/search-json`. Paginate section queries in `AdminController::section`. Shared Twig partial for picker + pagination.

**Tech Stack:** Symfony, Doctrine, Twig, existing admin JS.

## Global Constraints

- Preserve existing filters/actions; only change defaults and page size.
- Do not break form POSTs that expect `client_id`.
- Match clients page pagination UX (~50/page).

---

### Task 1: Shared client picker partial
- Extract picker markup/JS from `subscription_plans.html.twig` into `templates/admin/partials/_crm_client_picker.html.twig` (+ boot JS if needed).
- Wire dashboard, sales, tasks (and keep subscriptions).

### Task 2: Stop loading all users
- Remove `User::findBy([])` from dashboard, sales, tasks in `AdminController.php`.
- Pass `clientsSearchUrl` instead.

### Task 3: Paginate + join list sections
- subscriptions, sales (default 30d), visits, bookings (default today), leads, tasks, self-service.

### Task 4: Schedule bookings batch + issuedByPlan GROUP BY

### Task 5: Smoke-check templates render with new vars
