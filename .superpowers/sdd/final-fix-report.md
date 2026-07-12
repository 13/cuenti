# Final-review fix report — feature/rest-api-expansion

Date: 2026-07-12

## CRITICAL 1 — PUT /api/transactions/{id}: managed-entity mutation breaks balance reversal

**Root cause:** `TransactionApiController.updateTransaction` loaded `existing` via a
`readOnly` service call, mutated it in the controller, then called
`TransactionService.saveTransaction`, which re-loaded the "same" row inside its own
`@Transactional` method. Under OSIV the two calls could share the same first-level
cache instance, so `reverseBalanceEffect` ran against the already-mutated (new)
amount/type/accounts instead of the true old ones — silent balance corruption on any
PUT that changes amount/type/accounts. Scalar mutation of an entity loaded during a
readOnly transaction is also flagged read-only by Spring ORM 7 / Hibernate and may
not flush.

**Fix:**
- Added `TransactionService.updateTransaction(Long id, Consumer<Transaction> mutator)`
  (`src/main/java/com/cuenti/app/service/TransactionService.java`). It loads the
  entity fresh inside its own write `@Transactional` method, calls
  `reverseBalanceEffect(existing)` using the OLD state, THEN runs the mutator, THEN
  re-validates (amount non-negative, account ownership) and calls a new shared
  `finishSave(...)` helper (extracted from the old `saveTransaction` tail: reload
  managed accounts, `applyBalanceEffect`, set status, save, audit-log).
- Refactored `saveTransaction` to reuse `validateAmountNotNegative`,
  `checkAccountOwnership`, and `finishSave` — its behavior/ordering for existing
  callers (create flow, and the several Vaadin-view/import-service update call
  sites) is unchanged.
- `TransactionApiController.updateTransaction` now only does read-only
  existence/ownership lookup and DTO/split validation before calling
  `transactionService.updateTransaction(id, fresh -> { applySplitsMutation(fresh,
  dto); applyDtoFields(fresh, dto); })`. No controller code mutates a
  readOnly-transaction-loaded entity anymore.

**Test:** `TransactionSplitApiTest.putChangingAmountReversesOldBalanceNotNewOne` —
account startBalance 1000, POST expense 100 (balance 900 confirmed via GET
/api/accounts/{id}), PUT amount -> 50, asserts balance == 950 (not 800, which is
what the bug would have produced) and that GET /api/transactions shows the
persisted new amount.

## IMPORTANT 2 — GET /api/forecasts?year= unbounded → CPU DoS

**Fix:** `ForecastApiController.getForecast` now bounds `year` to
`[2000, currentYear + 50]`; out-of-range returns 400 with `{"error": ...}`.

**Test:** `ForecastApiControllerTest.extremeYearIs400` — `year=999999` → 400,
non-empty `$.error`.

## IMPORTANT 3 — split-sum invariant hole when amount changes without resending splits

**Fix:** Split `TransactionApiController.applySplits` into pure `validateSplits(dto)`
(structural checks only, no mutation) and `applySplitsMutation(transaction, dto)`
(actual replace-splits mutation). Added
`validateSplitSumInvariant(existing, dto)`: when `dto.getSplits() == null` and the
existing transaction has splits, the (possibly changed) `dto.getAmount()` must equal
the existing splits' sum, else 400 `{"error": ...}`. Called from the PUT handler
before invoking the service.

**Test:**
`TransactionSplitApiTest.putChangingAmountWithoutSplitsMismatchingExistingSplitSumIs400`
— create tx 50 with splits 30/20, PUT amount 80 without a `splits` key → 400, and
confirms the original amount/splits are untouched afterward.

## MINORS

- `AuditLogApiController.getAuditLog`: clamped `size` to `[1,200]` and `page` to
  `>=0` (was: `size=0`/`page<0` → `PageRequest` `IllegalArgumentException` → 500).
  Tests added: `zeroSizeIsClampedInsteadOf500` (size=0 → 200, `$.size == 1`),
  `negativePageIsClampedInsteadOf500` (page=-5 → 200, `$.page == 0`).
- `TransactionSearchApiTest.noParamsReturnsPlainArray`: added ordering assertion
  `$[0].payee == "Rewe 5"` (default `transactionDate` desc on the unpaged path).
- `TransactionApiController` GET: `end.atTime(23, 59, 59)` replaced with
  `end.atTime(java.time.LocalTime.MAX)`.

## Commands run

```
bash -c "cd /home/ben/repo/cuenti && /usr/bin/mvn -q compile"
bash -c "cd /home/ben/repo/cuenti && /usr/bin/mvn test -Dtest=TransactionSplitApiTest,TransactionSearchApiTest,ForecastApiControllerTest,AuditLogApiControllerTest"
bash -c "cd /home/ben/repo/cuenti && /usr/bin/mvn test"
```

## Test output summary

- Focused run: `Tests run: 23, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS.
- Full suite: `Tests run: 72, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS
  (67 baseline + 5 new: 2 in `TransactionSplitApiTest`, 1 in
  `ForecastApiControllerTest`, 2 in `AuditLogApiControllerTest`).

## Files changed

- `src/main/java/com/cuenti/app/service/TransactionService.java`
- `src/main/java/com/cuenti/app/api/TransactionApiController.java`
- `src/main/java/com/cuenti/app/api/ForecastApiController.java`
- `src/main/java/com/cuenti/app/api/AuditLogApiController.java`
- `src/test/java/com/cuenti/app/api/TransactionSplitApiTest.java`
- `src/test/java/com/cuenti/app/api/TransactionSearchApiTest.java`
- `src/test/java/com/cuenti/app/api/ForecastApiControllerTest.java`
- `src/test/java/com/cuenti/app/api/AuditLogApiControllerTest.java`
