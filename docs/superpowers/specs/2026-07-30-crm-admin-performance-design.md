# CRM Admin Performance Design

**Date:** 2026-07-30  
**Status:** Approved (user: «поехали», UX option 1)

## Problem

Admin CRM feels very slow with ~3400 clients. Clients list is already paginated (50/page). Main bottlenecks:

1. Full `User::findBy([])` dumped into HTML `<select>` on dashboard, sales, tasks
2. Unpaginated tables: subscriptions, sales, visits, bookings, leads, self-service
3. N+1 lazy associations in list templates
4. Schedule: per-training booking queries

## Approach

Wave 1 (UX + queries): AJAX client picker everywhere; paginate heavy lists; default date windows; JOINs; batch schedule bookings.

## Scope (wave 1)

- Replace full-user selects with `crm-client-picker` + `admin_clients_search_json`
- Paginate (~50): subscriptions, sales, visits, bookings, leads, tasks, self-service
- Defaults: sales last 30 days; bookings today; visits keep period resolver but paginate table
- JOINs for user/plan/club/etc. on list queries
- One GROUP BY for issued-by-plan counts
- Schedule: single bookings query by training IDs
- Optional: skip/cache `countClientsWithoutSubscription` if cheap

## Out of scope

- Mobile API (already limited)
- SPA / DataTables rewrite
- Turnstile / subscription business logic

## Success

Dashboard/sales/tasks open without loading 3400 users; list pages stay responsive as data grows.
