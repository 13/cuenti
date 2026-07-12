# REST API Expansion — Phase 1 of Mobile Overhaul

**Date:** 2026-07-12
**Repo:** `~/repo/cuenti` (Spring Boot + Vaadin)
**Status:** Approved design.
**Roadmap:** `~/repo/cuenti_mobile/docs/superpowers/specs/2026-07-12-mobile-overhaul-roadmap-design.md`

## Goal

Expose every feature that today exists only in the Vaadin web views through
the REST API, so the Flutter app (Phase 4) can reach feature parity. All new
endpoints follow the existing conventions: JWT auth via
`JwtAuthenticationFilter`, user-scoping through `SecurityUtil`, DTOs in
`api/dto`, mapping in `DtoMapper`.

Backward compatibility is a hard requirement: the shipped mobile app (v1.3.1)
must keep working against the upgraded backend.

## 1. Service extraction

Computation logic currently embedded in Vaadin views moves into services so
both the views and the new controllers share it.

### 1.1 `ForecastService` (from `ForecastsView.loadData`)

- Input: user, year.
- Iterates the user's enabled scheduled transactions, expands occurrences
  within the year using the existing recurrence logic, filters accounts with
  `excludeFromReports`, converts amounts to the user's default currency via
  `ExchangeRateService`. Transfers are ignored (as today).
- Output: per-month income and expense maps (`YYYY-MM` keys), totals, net.
- `ForecastsView` is refactored to call this service; behavior unchanged.

### 1.2 `VehicleReportService` (from `VehiclesView`)

- Input: user, expense category id, date range.
- Loads the user's transactions in that category/range, parses each memo into
  a `FuelEntry` (odometer, liters, full-tank flag, station from payee) using
  the existing regex patterns, converts amounts to the default currency.
- Output: fuel entries plus derived stats (total cost, total liters, total
  distance, l/100km, cost/km, per-fill series for the consumption chart) —
  the same derivations `calculateDerivedValues` does today.
- `VehiclesView` is refactored to call this service; behavior unchanged.

## 2. New controllers

### 2.1 `BudgetApiController` — `/api/budgets`

Backed by the existing `BudgetService`.

- `GET /api/budgets` — list the user's budgets (`BudgetDTO`: id, categoryId,
  categoryName, monthlyLimit, active).
- `POST /api/budgets` / `PUT /api/budgets/{id}` — create/update; reject a
  second budget for the same category via `existsForCategory` (400).
- `DELETE /api/budgets/{id}`.
- `GET /api/budgets/progress` — budgets joined with
  `BudgetService.getSpentThisMonth`: per budget, the limit, spent amount, and
  remaining for the current month.

### 2.2 `ForecastApiController` — `/api/forecasts`

- `GET /api/forecasts?year=YYYY` (default: current year) — response:
  `{ year, months: [{month: "YYYY-MM", income, expense, net}], totalIncome,
  totalExpense, netForecast, currency }`.

### 2.3 `VehicleApiController` — `/api/vehicles`

- `GET /api/vehicles/report?categoryId=&start=&end=` — `categoryId` defaults
  to the user's saved `defaultVehicleCategoryId`; response contains the fuel
  entries and derived stats from `VehicleReportService`.
- The default vehicle category is saved through the existing
  `PUT /api/user/preferences` (add `defaultVehicleCategoryId` to the accepted
  preference keys and to `UserProfileDTO`).

### 2.4 `AuditLogApiController` — `/api/audit-log`

- `GET /api/audit-log?page=&size=` — admin-only (same guard as the existing
  `/api/user/admin/*` endpoints). Returns a page of audit entries (id, userId,
  username, timestamp, entityType, entityId, action, details) plus total
  count.

### 2.5 `SavedViewApiController` — `/api/saved-views`

Backed by the existing `SavedViewService`.

- `GET /api/saved-views` — the user's saved views (id, name, params,
  createdAt). `params` stays an opaque string, as stored.
- `POST /api/saved-views`, `PUT /api/saved-views/{id}`,
  `DELETE /api/saved-views/{id}`.

## 3. Transaction API changes

### 3.1 Splits

- `TransactionDTO` gains `splits: List<TransactionSplitDTO>` (id, categoryId,
  categoryName, amount, memo).
- `GET` responses include splits; `POST`/`PUT` accept them and replace the
  transaction's split set. Validation: split amounts must sum to the
  transaction amount when splits are present (400 otherwise), matching the
  web UI's rule.
- Omitting the field keeps current behavior — back-compatible.

### 3.2 Pagination, filtering, sorting

`GET /api/transactions` gains optional parameters:

- `page`, `size` — when either is present, the response becomes
  `{content: [...], page, size, totalElements, totalPages}`. **Without them
  the endpoint returns the plain array exactly as today** (back-compat).
- Filters: `accountId` (existing), `start`, `end` (ISO dates), `categoryId`,
  `payee` (substring), `tag`, `type`, `search` (matches payee/memo/number).
- `sort` — `field,direction` (default `transactionDate,desc`).
- Implemented with a repository/criteria query, not in-memory filtering.

## 4. Error handling

Follow existing controller conventions: 404 for missing/foreign entities,
400 with a message body for validation failures, 403 where admin gating
applies. No new global exception machinery.

## 5. Testing

Spring MockMvc controller tests per new/changed endpoint covering: happy
path, user-scoping (user A cannot read/modify user B's data), validation
failures, admin gating for audit log, and transaction back-compat (no-param
call still returns a plain array; DTO without splits still parses).
Service-level unit tests for `ForecastService` occurrence expansion and
`VehicleReportService` memo parsing/derived stats.

## Out of scope

- XHB import/export and TradeRepublic import over REST (web-only for now).
- Any Flutter changes (Phases 2–4).
- Changes to existing endpoint semantics beyond the additive ones above.
