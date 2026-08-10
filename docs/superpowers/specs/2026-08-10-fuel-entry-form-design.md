# Structured Fuel Entry Form — Design

**Date:** 2026-08-10
**Status:** Approved

## Problem

Tanking entries are typed by hand into the transaction memo TextArea as free text
(`d=45210 l=41.3 full`). Typos (`d 45210`, `L=40`) silently drop the entry from the
vehicle report — no feedback at save time. Format must be memorized.

## Goal

Guided entry of odometer km, liters, and full-tank flag with live plausibility
warnings. The memo string `d=… l=… full` remains the storage format: no schema
migration, existing entries keep working unchanged. Warnings inform, never block.

## Non-Goals

- No dedicated fuel table or schema change.
- No changes to the REST API or report aggregation logic.
- No dedicated "Add fillup" dialog on VehiclesView (may come later).
- No hard validation that blocks saving.

## Design

### 1. Service layer (`VehicleReportService`)

New public methods (pure additions, existing logic untouched):

- `boolean isFuelCategory(User user, Long categoryId)` — true when at least one
  existing transaction in the category has a memo matching the odometer or liters
  pattern. Powers auto-detection of the fuel section.
- `Integer lastOdometer(User user, Long categoryId, LocalDate beforeDate)` — latest
  known odometer reading before the given date. Powers the prefill hint and the
  "not increasing" plausibility check.
- `FuelTokens parseFuelTokens(String memo)` — exposes the existing regex parse as a
  record `(Integer odometer, Double liters, boolean fullTank, String remainderText)`
  where `remainderText` is the memo with fuel tokens stripped.
- `String buildFuelMemo(Integer odometer, Double liters, boolean fullTank, String remainderText)` —
  regenerates the memo in canonical form `d=<km> l=<liters> [full] <remainder>`,
  preserving non-fuel free text.

Round-trip invariant: `parseFuelTokens(buildFuelMemo(o, l, f, r))` returns the same
values.

### 2. Form UX (`TransactionHistoryView`)

Collapsible fuel section rendered below the category field:

- `IntegerField` odometer km — helper text shows last known reading
  (e.g. "last: 44 870").
- `NumberField` liters.
- `Checkbox` full tank.
- Live info line when odometer + liters present and a previous reading exists:
  "340 km since last, ~12.1 L/100km" (consumption shown only for full-tank entries).

Visibility rules:

- Category selected whose `isFuelCategory` is true → section opens automatically.
- Editing an existing transaction whose memo parses → section opens, fields
  populated from `parseFuelTokens`.
- Otherwise hidden; no manual toggle in this iteration.

Two-way sync with the memo field (memo stays visible and editable):

- Field change → rewrite the `d=` / `l=` / `full` tokens inside the memo via
  `buildFuelMemo`, preserving remainder free text.
- Manual memo edit → on blur, reparse into the fields.

### 3. Plausibility warnings (non-blocking)

Shown as field helper/error styling or a soft notification; save always proceeds:

- Odometer ≤ last known reading → "odometer not increasing".
- Odometer jump > 2 000 km since last reading → "big jump — typo?".
- Liters ≤ 0 or > 200 → "implausible liters".
- Fuel category selected but both fields empty on save → soft notification that the
  entry will not appear in the vehicle report; save proceeds.

### 4. Testing

Unit tests (`VehicleReportService`):

- Memo round-trip: parse → build → parse yields identical tokens.
- Token replacement preserves surrounding free text.
- `isFuelCategory` true/false cases; `lastOdometer` picks the latest prior reading.

Karibu UI tests (`TransactionHistoryView`):

- Fuel section appears when a fuel category is selected, hidden otherwise.
- Editing an entry with a parseable memo populates the fields.
- Changing fields regenerates the memo string.
- Warning shown for non-increasing odometer.
