# Structured Fuel Entry Form Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Guided odometer/liters/full-tank fields in the transaction dialog that read and write the existing `d=… l=… full` memo format, with non-blocking plausibility warnings.

**Architecture:** All parsing/building stays in `VehicleReportService` (static token helpers + two instance queries). `TransactionHistoryView` gains a fuel section inside the existing transaction dialog that two-way-syncs with the memo TextArea. No DB, no API changes — memo string remains the storage format.

**Tech Stack:** Java 21, Spring Boot, Vaadin Flow, Lombok, JUnit 5 + AssertJ + Mockito (unit), Vaadin Browserless (`SpringBrowserlessTest`) for view tests. Build: `./mvnw`.

**Spec:** `docs/superpowers/specs/2026-08-10-fuel-entry-form-design.md`

## Global Constraints

- Memo format `d=<km> l=<liters> [full] <free text>` is canonical output; parser also accepts legacy `v=`, `d:`, `l~`, `45210 km`, `40 l` variants (existing regexes).
- Warnings never block saving.
- All user-visible strings via `getTranslation(...)` with keys in BOTH `messages.properties` and `messages_de.properties`.
- View tests follow existing convention: `@SpringBootTest @ActiveProfiles("test") @WithMockUser(username = "demo")` extending `SpringBrowserlessTest`, named `UC110FuelEntryFormTest`, `@UseCase(id = "UC-110", scenario = "...")` annotations (see `src/test/java/com/cuenti/app/views/UC104TransactionWorkflowTest.java`).
- Run tests with `./mvnw -q test -Dtest=<ClassName>` from `/home/ben/repo/cuenti`.
- Commit messages end with `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

---

### Task 1: Fuel token parse/build helpers (`FuelTokens`)

**Files:**
- Modify: `src/main/java/com/cuenti/app/service/VehicleReportService.java` (add after `parseFuelEntry`, around line 132)
- Test: `src/test/java/com/cuenti/app/service/FuelMemoTokensTest.java` (create)

**Interfaces:**
- Consumes: existing private statics `extractValue(String, Pattern, String)`, `extractFullTank(String)`, patterns `ODOMETER_PATTERN`, `LITERS_PATTERN`, `FULL_TANK_PATTERN` (VehicleReportService.java:32-34).
- Produces (used by Tasks 2–4):
  - `public record FuelTokens(BigDecimal odometer, BigDecimal liters, boolean fullTank, String remainderText)` with method `public boolean hasFuelData()` — nested in `VehicleReportService`.
  - `public static FuelTokens parseFuelTokens(String memo)` — null-safe, never returns null.
  - `public static String buildFuelMemo(BigDecimal odometer, BigDecimal liters, boolean fullTank, String remainderText)`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/cuenti/app/service/FuelMemoTokensTest.java`:

```java
package com.cuenti.app.service;

import com.cuenti.app.service.VehicleReportService.FuelTokens;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip parsing/building of the fuel memo format used by the
 * structured fuel entry form: "d=<km> l=<liters> [full] <free text>".
 */
class FuelMemoTokensTest {

    @Test
    void parsesTokensAndPreservesRemainderText() {
        FuelTokens t = VehicleReportService.parseFuelTokens("d=45210 l=41.3 full Aral Autobahn");
        assertThat(t.odometer()).isEqualByComparingTo("45210");
        assertThat(t.liters()).isEqualByComparingTo("41.3");
        assertThat(t.fullTank()).isTrue();
        assertThat(t.remainderText()).isEqualTo("Aral Autobahn");
        assertThat(t.hasFuelData()).isTrue();
    }

    @Test
    void nullAndEmptyMemoYieldEmptyTokens() {
        FuelTokens t = VehicleReportService.parseFuelTokens(null);
        assertThat(t.odometer()).isNull();
        assertThat(t.liters()).isNull();
        assertThat(t.fullTank()).isFalse();
        assertThat(t.remainderText()).isEmpty();
        assertThat(t.hasFuelData()).isFalse();
    }

    @Test
    void parsesLegacySecondaryNotation() {
        FuelTokens t = VehicleReportService.parseFuelTokens("45210 km 40 l");
        assertThat(t.odometer()).isEqualByComparingTo("45210");
        assertThat(t.liters()).isEqualByComparingTo("40");
        assertThat(t.fullTank()).isFalse();
    }

    @Test
    void buildsCanonicalMemo() {
        String memo = VehicleReportService.buildFuelMemo(
                new BigDecimal("45210"), new BigDecimal("41.3"), true, "Aral");
        assertThat(memo).isEqualTo("d=45210 l=41.3 full Aral");
    }

    @Test
    void buildSkipsMissingParts() {
        assertThat(VehicleReportService.buildFuelMemo(null, new BigDecimal("40"), false, null))
                .isEqualTo("l=40");
        assertThat(VehicleReportService.buildFuelMemo(null, null, false, "just a note"))
                .isEqualTo("just a note");
        assertThat(VehicleReportService.buildFuelMemo(null, null, false, ""))
                .isEmpty();
    }

    @Test
    void roundTripIsStable() {
        String built = VehicleReportService.buildFuelMemo(
                new BigDecimal("100500"), new BigDecimal("38.5"), true, "Shell");
        FuelTokens t = VehicleReportService.parseFuelTokens(built);
        assertThat(t.odometer()).isEqualByComparingTo("100500");
        assertThat(t.liters()).isEqualByComparingTo("38.5");
        assertThat(t.fullTank()).isTrue();
        assertThat(t.remainderText()).isEqualTo("Shell");
        assertThat(VehicleReportService.buildFuelMemo(
                t.odometer(), t.liters(), t.fullTank(), t.remainderText())).isEqualTo(built);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw -q test -Dtest=FuelMemoTokensTest`
Expected: COMPILATION ERROR — `FuelTokens`, `parseFuelTokens`, `buildFuelMemo` do not exist.

- [ ] **Step 3: Implement the helpers**

In `src/main/java/com/cuenti/app/service/VehicleReportService.java`, first extract the two inline secondary regex strings (currently literals at lines 119-120) into constants next to the existing patterns (line 34):

```java
    private static final String SECONDARY_ODOMETER_REGEX = "(\\d{4,})\\s*km";
    private static final String SECONDARY_LITERS_REGEX = "(\\d+(?:[.,]\\d+)?)\\s*[Ll](?:\\s|$|\\))";
```

Update `parseFuelEntry` to use them:

```java
        BigDecimal odometer = extractValue(t.getMemo(), ODOMETER_PATTERN, SECONDARY_ODOMETER_REGEX);
        BigDecimal liters = extractValue(t.getMemo(), LITERS_PATTERN, SECONDARY_LITERS_REGEX);
```

Then add below `parseFuelEntry` (after line 132):

```java
    /**
     * Structured view of a memo's fuel tokens plus whatever free text
     * remains once they are stripped. Used by the transaction form.
     */
    public record FuelTokens(BigDecimal odometer, BigDecimal liters, boolean fullTank, String remainderText) {
        public boolean hasFuelData() {
            return odometer != null || liters != null;
        }
    }

    public static FuelTokens parseFuelTokens(String memo) {
        String safe = memo == null ? "" : memo;
        BigDecimal odometer = extractValue(safe, ODOMETER_PATTERN, SECONDARY_ODOMETER_REGEX);
        BigDecimal liters = extractValue(safe, LITERS_PATTERN, SECONDARY_LITERS_REGEX);
        boolean fullTank = extractFullTank(safe);
        String remainder = ODOMETER_PATTERN.matcher(safe).replaceAll("");
        remainder = LITERS_PATTERN.matcher(remainder).replaceAll("");
        remainder = remainder.replaceAll(SECONDARY_ODOMETER_REGEX, "");
        remainder = remainder.replaceAll(SECONDARY_LITERS_REGEX, " ");
        remainder = FULL_TANK_PATTERN.matcher(remainder).replaceAll("");
        remainder = remainder.replaceAll("\\s+", " ").trim();
        return new FuelTokens(odometer, liters, fullTank, remainder);
    }

    /** Inverse of {@link #parseFuelTokens}: canonical "d=… l=… full <text>". */
    public static String buildFuelMemo(BigDecimal odometer, BigDecimal liters, boolean fullTank, String remainderText) {
        StringBuilder sb = new StringBuilder();
        if (odometer != null) sb.append("d=").append(odometer.stripTrailingZeros().toPlainString());
        if (liters != null) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("l=").append(liters.stripTrailingZeros().toPlainString());
        }
        if (fullTank) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("full");
        }
        if (remainderText != null && !remainderText.isBlank()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(remainderText.trim());
        }
        return sb.toString();
    }
```

Note: `parsesLegacySecondaryNotation` uses `"45210 km 40 l"` (no comma) so the remainder strip leaves nothing odd; the test does not assert its remainder.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -q test -Dtest=FuelMemoTokensTest`
Expected: 6 tests PASS.

- [ ] **Step 5: Run existing fuel tests to verify no regression**

Run: `./mvnw -q test -Dtest='VehicleReportServiceTest,VehicleConsumptionCalcTest'`
Expected: all PASS (parseFuelEntry behavior unchanged).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/cuenti/app/service/VehicleReportService.java src/test/java/com/cuenti/app/service/FuelMemoTokensTest.java
git commit -m "feat(vehicles): fuel memo token parse/build helpers

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: `isFuelCategory` and `lastOdometer` queries

**Files:**
- Modify: `src/main/java/com/cuenti/app/service/VehicleReportService.java` (add after `getReport`, around line 116)
- Test: `src/test/java/com/cuenti/app/service/VehicleFuelCategoryQueryTest.java` (create)

**Interfaces:**
- Consumes: `parseFuelTokens` from Task 1; `transactionService.getTransactionsByUser(User)` (TransactionService.java:253); `Transaction.builder()` (Lombok).
- Produces (used by Task 3/4):
  - `public boolean isFuelCategory(User user, Long categoryId)` — instance method.
  - `public BigDecimal lastOdometer(User user, Long categoryId, LocalDate beforeDate)` — instance method, latest odometer from transactions strictly before `beforeDate`; null when none.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/cuenti/app/service/VehicleFuelCategoryQueryTest.java`:

```java
package com.cuenti.app.service;

import com.cuenti.app.model.Category;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Category fuel detection and last-known odometer lookup used by the
 * transaction form's fuel section.
 */
class VehicleFuelCategoryQueryTest {

    private final TransactionService transactionService = mock(TransactionService.class);
    private final ExchangeRateService exchangeRateService = mock(ExchangeRateService.class);
    private final User user = mock(User.class);
    private final VehicleReportService service =
            new VehicleReportService(transactionService, exchangeRateService);

    private Transaction tx(Long categoryId, String memo, LocalDateTime date) {
        Category cat = mock(Category.class);
        when(cat.getId()).thenReturn(categoryId);
        return Transaction.builder()
                .type(Transaction.TransactionType.EXPENSE)
                .amount(new BigDecimal("50"))
                .transactionDate(date)
                .category(cat)
                .memo(memo)
                .build();
    }

    @Test
    void categoryWithFuelMemosIsFuelCategory() {
        when(transactionService.getTransactionsByUser(user)).thenReturn(List.of(
                tx(5L, "d=45210 l=41.3 full", LocalDateTime.of(2026, 7, 1, 10, 0)),
                tx(7L, "groceries", LocalDateTime.of(2026, 7, 2, 10, 0))));

        assertThat(service.isFuelCategory(user, 5L)).isTrue();
        assertThat(service.isFuelCategory(user, 7L)).isFalse();
    }

    @Test
    void lastOdometerPicksLatestReadingBeforeDate() {
        when(transactionService.getTransactionsByUser(user)).thenReturn(List.of(
                tx(5L, "d=44000 l=40", LocalDateTime.of(2026, 6, 1, 10, 0)),
                tx(5L, "d=44870 l=38", LocalDateTime.of(2026, 7, 1, 10, 0)),
                tx(5L, "d=45500 l=41", LocalDateTime.of(2026, 8, 1, 10, 0)),
                tx(5L, "no odometer here l=20", LocalDateTime.of(2026, 7, 15, 10, 0))));

        assertThat(service.lastOdometer(user, 5L, LocalDate.of(2026, 7, 20)))
                .isEqualByComparingTo("44870");
    }

    @Test
    void lastOdometerNullWhenNoPriorReading() {
        when(transactionService.getTransactionsByUser(user)).thenReturn(List.of(
                tx(5L, "d=44000 l=40", LocalDateTime.of(2026, 6, 1, 10, 0))));

        assertThat(service.lastOdometer(user, 5L, LocalDate.of(2026, 6, 1))).isNull();
        assertThat(service.lastOdometer(user, 9L, LocalDate.of(2026, 12, 1))).isNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw -q test -Dtest=VehicleFuelCategoryQueryTest`
Expected: COMPILATION ERROR — `isFuelCategory`, `lastOdometer` do not exist.

- [ ] **Step 3: Implement the queries**

Add to `VehicleReportService` after `getReport` (around line 116). Add imports `java.util.Objects` if missing:

```java
    /** True when the category already contains at least one parseable fuel memo. */
    @Transactional(readOnly = true)
    public boolean isFuelCategory(User user, Long categoryId) {
        if (categoryId == null) return false;
        return transactionService.getTransactionsByUser(user).stream()
                .filter(t -> t.getCategory() != null && categoryId.equals(t.getCategory().getId()))
                .filter(t -> t.getType() == Transaction.TransactionType.EXPENSE)
                .anyMatch(t -> parseFuelTokens(t.getMemo()).hasFuelData());
    }

    /** Latest odometer reading in the category strictly before the given date; null when none. */
    @Transactional(readOnly = true)
    public BigDecimal lastOdometer(User user, Long categoryId, LocalDate beforeDate) {
        if (categoryId == null || beforeDate == null) return null;
        return transactionService.getTransactionsByUser(user).stream()
                .filter(t -> t.getCategory() != null && categoryId.equals(t.getCategory().getId()))
                .filter(t -> t.getType() == Transaction.TransactionType.EXPENSE)
                .filter(t -> t.getTransactionDate().toLocalDate().isBefore(beforeDate))
                .sorted(Comparator.comparing(Transaction::getTransactionDate).reversed())
                .map(t -> parseFuelTokens(t.getMemo()).odometer())
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -q test -Dtest=VehicleFuelCategoryQueryTest`
Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cuenti/app/service/VehicleReportService.java src/test/java/com/cuenti/app/service/VehicleFuelCategoryQueryTest.java
git commit -m "feat(vehicles): fuel category detection and last odometer lookup

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Fuel section in the transaction dialog (fields, visibility, two-way memo sync)

**Files:**
- Modify: `src/main/java/com/cuenti/app/views/TransactionHistoryView.java`
  - constructor (line 111): inject `VehicleReportService`
  - `openTransactionDialog` (line 1139): change `private` to package-private; add fuel section
  - memo area (line 1393), category combo (line 1299), edit pre-fill block (line 1548), section assembly (line 1586)
- Modify: `src/main/resources/messages.properties` (after line 437, end of `vehicles.*` block)
- Modify: `src/main/resources/messages_de.properties` (matching `vehicles.*` block)
- Test: `src/test/java/com/cuenti/app/views/UC110FuelEntryFormTest.java` (create)

**Interfaces:**
- Consumes: `VehicleReportService.parseFuelTokens(String)`, `buildFuelMemo(BigDecimal, Double-as-BigDecimal, boolean, String)`, `isFuelCategory(User, Long)`, `lastOdometer(User, Long, LocalDate)` (Tasks 1–2). Existing locals in `openTransactionDialog`: `categoryCombo`, `memoField`, `datePicker`, `currentFormTransaction[0]`, `coreSection`, `createFormSection(String)`.
- Produces (used by Task 4 and tests):
  - Component ids inside the dialog: `fuel-section` (Div), `fuel-odometer` (IntegerField), `fuel-liters` (NumberField), `fuel-full` (Checkbox), plus `tx-category` on the existing category ComboBox.
  - `void openTransactionDialog(Transaction)` package-visible for tests.
  - Local holders `String[] fuelRemainder`, `boolean[] fuelSyncing`, `BigDecimal[] fuelLastOdometer`, `Runnable syncMemoFromFuelFields`, `Runnable updateFuelVisibility` — Task 4 hooks into these.

- [ ] **Step 1: Add i18n keys**

`src/main/resources/messages.properties`, after the existing `vehicles.*` keys (line ~437):

```properties
vehicles.form_odometer=Odometer (km)
vehicles.form_liters=Liters
vehicles.form_full_tank=Full tank
vehicles.form_last=last: {0}
```

`src/main/resources/messages_de.properties`, same block:

```properties
vehicles.form_odometer=Kilometerstand (km)
vehicles.form_liters=Liter
vehicles.form_full_tank=Vollgetankt
vehicles.form_last=zuletzt: {0}
```

- [ ] **Step 2: Write the failing Browserless test**

Create `src/test/java/com/cuenti/app/views/UC110FuelEntryFormTest.java`:

```java
package com.cuenti.app.views;

import com.cuenti.app.model.Account;
import com.cuenti.app.model.Category;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import com.cuenti.app.service.AccountService;
import com.cuenti.app.service.CategoryService;
import com.cuenti.app.service.TransactionService;
import com.cuenti.app.service.UserService;
import com.cuenti.app.usecase.UseCase;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structured fuel entry: fuel section appears for fuel categories, fields
 * populate from an existing memo, field edits regenerate the memo string.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "demo")
class UC110FuelEntryFormTest extends SpringBrowserlessTest {

    @Autowired TransactionService transactionService;
    @Autowired CategoryService categoryService;
    @Autowired AccountService accountService;
    @Autowired UserService userService;

    private Category fuelCategory;
    private Account account;

    private void seedFuelCategory() {
        User demo = userService.findByUsername("demo");
        account = accountService.getAccountsByUser(demo).get(0);
        fuelCategory = new Category();
        fuelCategory.setName("ZZ Fuel Form Test");
        fuelCategory.setType(Category.CategoryType.EXPENSE);
        fuelCategory = categoryService.saveCategory(fuelCategory);
        Transaction t = new Transaction();
        t.setType(Transaction.TransactionType.EXPENSE);
        t.setFromAccount(account);
        t.setAmount(new BigDecimal("60.00"));
        t.setTransactionDate(LocalDateTime.now().minusDays(10));
        t.setCategory(fuelCategory);
        t.setMemo("d=100000 l=40 full");
        transactionService.saveTransaction(t);
    }

    @Test
    @UseCase(id = "UC-110", scenario = "Fuel section appears for fuel category")
    void fuelSection_appearsWhenFuelCategorySelected() {
        seedFuelCategory();
        navigate(TransactionHistoryView.class);
        fireShortcut(Key.KEY_N, KeyModifier.ALT);

        Div fuelSection = $(Div.class).withId("fuel-section").single();
        assertThat(fuelSection.isVisible()).isFalse();

        ComboBox<Category> categoryCombo = $(ComboBox.class).withId("tx-category").single();
        test(categoryCombo).selectItem(fuelCategory.getFullName());

        assertThat(fuelSection.isVisible()).isTrue();
        IntegerField odometer = $(IntegerField.class).withId("fuel-odometer").single();
        assertThat(odometer.getHelperText()).contains("100000");
    }

    @Test
    @UseCase(id = "UC-110", scenario = "Field edits regenerate the memo string")
    void fieldEdits_writeCanonicalMemo() {
        seedFuelCategory();
        navigate(TransactionHistoryView.class);
        fireShortcut(Key.KEY_N, KeyModifier.ALT);

        ComboBox<Category> categoryCombo = $(ComboBox.class).withId("tx-category").single();
        test(categoryCombo).selectItem(fuelCategory.getFullName());

        test($(IntegerField.class).withId("fuel-odometer").single()).setValue(100650);
        test($(NumberField.class).withId("fuel-liters").single()).setValue(41.3);
        test($(Checkbox.class).withId("fuel-full").single()).click();

        TextArea memo = $(TextArea.class).withId("tx-memo").single();
        assertThat(memo.getValue()).isEqualTo("d=100650 l=41.3 full");
    }

    @Test
    @UseCase(id = "UC-110", scenario = "Editing a fuel transaction populates the fields")
    void editingFuelTransaction_populatesFields() {
        seedFuelCategory();
        Transaction edit = new Transaction();
        edit.setType(Transaction.TransactionType.EXPENSE);
        edit.setFromAccount(account);
        edit.setAmount(new BigDecimal("70.00"));
        edit.setTransactionDate(LocalDateTime.now().minusDays(2));
        edit.setCategory(fuelCategory);
        edit.setMemo("d=100650 l=41.3 full Aral");
        transactionService.saveTransaction(edit);

        TransactionHistoryView view = navigate(TransactionHistoryView.class);
        view.openTransactionDialog(edit);

        Div fuelSection = $(Div.class).withId("fuel-section").single();
        assertThat(fuelSection.isVisible()).isTrue();
        assertThat($(IntegerField.class).withId("fuel-odometer").single().getValue()).isEqualTo(100650);
        assertThat($(NumberField.class).withId("fuel-liters").single().getValue()).isEqualTo(41.3);
        assertThat($(Checkbox.class).withId("fuel-full").single().getValue()).isTrue();
    }

    @Test
    @UseCase(id = "UC-110", scenario = "Manual memo edit reparses into fields")
    void manualMemoEdit_reparsesIntoFields() {
        seedFuelCategory();
        navigate(TransactionHistoryView.class);
        fireShortcut(Key.KEY_N, KeyModifier.ALT);

        ComboBox<Category> categoryCombo = $(ComboBox.class).withId("tx-category").single();
        test(categoryCombo).selectItem(fuelCategory.getFullName());

        TextArea memo = $(TextArea.class).withId("tx-memo").single();
        test(memo).setValue("d=100700 l=30");

        assertThat($(IntegerField.class).withId("fuel-odometer").single().getValue()).isEqualTo(100700);
        assertThat($(NumberField.class).withId("fuel-liters").single().getValue()).isEqualTo(30.0);
        assertThat($(Checkbox.class).withId("fuel-full").single().getValue()).isFalse();
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=UC110FuelEntryFormTest`
Expected: COMPILATION ERROR — `openTransactionDialog` not visible; at runtime ids `fuel-section` etc. would not exist.

- [ ] **Step 4: Inject `VehicleReportService` into the view**

In `TransactionHistoryView.java`:

Field block (after line 76, next to `savedViewService`):

```java
    private final VehicleReportService vehicleReportService;
```

Constructor signature (line 111) — append parameter:

```java
    public TransactionHistoryView(TransactionService transactionService, AccountService accountService,
                                  UserService userService, ExchangeRateService exchangeRateService,
                                  CategoryService categoryService, AssetService assetService,
                                  PayeeService payeeService, TagService tagService, SecurityUtils securityUtils,
                                  com.cuenti.app.service.SavedViewService savedViewService,
                                  VehicleReportService vehicleReportService) {
```

and in the body add `this.vehicleReportService = vehicleReportService;`. Add import `com.cuenti.app.service.VehicleReportService` and `com.cuenti.app.service.VehicleReportService.FuelTokens`.

- [ ] **Step 5: Build the fuel section inside `openTransactionDialog`**

5a. Change method visibility (line 1139):

```java
    void openTransactionDialog(Transaction transaction) { // package-visible for tests
```

5b. Set ids on existing components: after `categoryCombo` creation (line 1302) add `categoryCombo.setId("tx-category");` and after `memoField` creation (line 1397) add `memoField.setId("tx-memo");`.

5c. After the memo field block (after line 1397), add the fuel section. Imports needed: `com.vaadin.flow.component.textfield.IntegerField`, `com.vaadin.flow.component.textfield.NumberField`, `com.vaadin.flow.component.checkbox.Checkbox`.

```java
        // ── Fuel section (structured tanking entry) ───────────────────
        IntegerField fuelOdometerField = new IntegerField(getTranslation("vehicles.form_odometer"));
        fuelOdometerField.setId("fuel-odometer");
        fuelOdometerField.setWidthFull();
        fuelOdometerField.setStepButtonsVisible(false);

        NumberField fuelLitersField = new NumberField(getTranslation("vehicles.form_liters"));
        fuelLitersField.setId("fuel-liters");
        fuelLitersField.setWidthFull();

        Checkbox fuelFullTankBox = new Checkbox(getTranslation("vehicles.form_full_tank"));
        fuelFullTankBox.setId("fuel-full");

        HorizontalLayout fuelRow = new HorizontalLayout(fuelOdometerField, fuelLitersField, fuelFullTankBox);
        fuelRow.setWidthFull(); fuelRow.setSpacing(false);
        fuelRow.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.BASELINE);
        fuelRow.getStyle().set("gap", "var(--vaadin-gap-m)").set("flex-wrap", "wrap");
        fuelOdometerField.getElement().getStyle().set("flex", "1 1 140px").set("min-width", "0");
        fuelLitersField.getElement().getStyle().set("flex", "1 1 140px").set("min-width", "0");

        Div fuelSection = new Div(fuelRow);
        fuelSection.setId("fuel-section");
        fuelSection.setWidthFull();
        fuelSection.setVisible(false);

        String[] fuelRemainder = {""};
        boolean[] fuelSyncing = {false};
        BigDecimal[] fuelLastOdometer = {null};

        Runnable refreshLastOdometerHint = () -> {
            Category cat = categoryCombo.getValue();
            LocalDate refDate = datePicker.getValue() != null ? datePicker.getValue() : LocalDate.now();
            fuelLastOdometer[0] = cat != null && cat.getId() != null
                    ? vehicleReportService.lastOdometer(currentUser, cat.getId(), refDate)
                    : null;
            fuelOdometerField.setHelperText(fuelLastOdometer[0] != null
                    ? getTranslation("vehicles.form_last", fuelLastOdometer[0].toPlainString())
                    : null);
        };

        Runnable syncMemoFromFuelFields = () -> {
            if (fuelSyncing[0]) return;
            fuelSyncing[0] = true;
            BigDecimal od = fuelOdometerField.getValue() != null
                    ? BigDecimal.valueOf(fuelOdometerField.getValue()) : null;
            BigDecimal li = fuelLitersField.getValue() != null
                    ? BigDecimal.valueOf(fuelLitersField.getValue()) : null;
            memoField.setValue(VehicleReportService.buildFuelMemo(
                    od, li, Boolean.TRUE.equals(fuelFullTankBox.getValue()), fuelRemainder[0]));
            fuelSyncing[0] = false;
        };
        fuelOdometerField.addValueChangeListener(e -> syncMemoFromFuelFields.run());
        fuelLitersField.addValueChangeListener(e -> syncMemoFromFuelFields.run());
        fuelFullTankBox.addValueChangeListener(e -> syncMemoFromFuelFields.run());

        Runnable populateFuelFieldsFromMemo = () -> {
            FuelTokens tokens = VehicleReportService.parseFuelTokens(memoField.getValue());
            fuelSyncing[0] = true;
            fuelOdometerField.setValue(tokens.odometer() != null ? tokens.odometer().intValue() : null);
            fuelLitersField.setValue(tokens.liters() != null ? tokens.liters().doubleValue() : null);
            fuelFullTankBox.setValue(tokens.fullTank());
            fuelRemainder[0] = tokens.remainderText();
            fuelSyncing[0] = false;
        };
        memoField.addValueChangeListener(e -> {
            if (fuelSyncing[0] || !e.isFromClient()) return;
            populateFuelFieldsFromMemo.run();
        });

        Runnable updateFuelVisibility = () -> {
            Category cat = categoryCombo.getValue();
            boolean memoParses = VehicleReportService.parseFuelTokens(memoField.getValue()).hasFuelData();
            boolean show = memoParses || (cat != null && cat.getId() != null
                    && vehicleReportService.isFuelCategory(currentUser, cat.getId()));
            fuelSection.setVisible(show);
            if (show) refreshLastOdometerHint.run();
        };
        categoryCombo.addValueChangeListener(e -> updateFuelVisibility.run());
        datePicker.addValueChangeListener(e -> { if (fuelSection.isVisible()) refreshLastOdometerHint.run(); });
```

Placement note: this block must come AFTER `categoryCombo` and `datePicker` exist. `memoField` is created at line 1393 which is after both — put the block directly after the memo field setup lines (1393-1397).

5d. Edit-mode pre-fill: inside the existing `if (currentFormTransaction[0].getId() != null)` block (line 1549-1564), after `categoryCombo.setValue(...)` (line 1558) add:

```java
            if (VehicleReportService.parseFuelTokens(currentFormTransaction[0].getMemo()).hasFuelData()) {
                populateFuelFieldsFromMemo.run();
                updateFuelVisibility.run();
            }
```

(The `categoryCombo.setValue` above it also fires `updateFuelVisibility` via the listener; the explicit call keeps behavior correct when the category itself is not detected as fuel yet.)

5e. Add the section to the layout — modify line 1586:

```java
         coreSection.add(row1, row2, fuelSection, row3, tagsRow, memoField, splitSection, assetSection, hiddenTabs);
```

Add import `java.time.LocalDate` if missing.

- [ ] **Step 6: Run the new test**

Run: `./mvnw -q test -Dtest=UC110FuelEntryFormTest`
Expected: 4 tests PASS. If `withId(...)` does not exist on the query API, use the terminal form `$(IntegerField.class).id("fuel-odometer")` instead (returns the component directly) — adjust all queries consistently.

- [ ] **Step 7: Run full test suite**

Run: `./mvnw -q test`
Expected: all PASS (constructor change is Spring-injected, no other callers construct the view manually — verify with `grep -rn "new TransactionHistoryView(" src/`; expect no hits).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/cuenti/app/views/TransactionHistoryView.java src/main/resources/messages.properties src/main/resources/messages_de.properties src/test/java/com/cuenti/app/views/UC110FuelEntryFormTest.java
git commit -m "feat(vehicles): structured fuel fields in transaction dialog

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Plausibility warnings, info line, empty-save notification

**Files:**
- Modify: `src/main/java/com/cuenti/app/views/TransactionHistoryView.java` (fuel section block from Task 3; save button listeners at lines ~1610-1650)
- Modify: `src/main/resources/messages.properties`, `src/main/resources/messages_de.properties`
- Test: extend `src/test/java/com/cuenti/app/views/UC110FuelEntryFormTest.java`

**Interfaces:**
- Consumes: Task 3 locals `fuelOdometerField`, `fuelLitersField`, `fuelFullTankBox`, `fuelSection`, `fuelLastOdometer`, `refreshLastOdometerHint`, `syncMemoFromFuelFields`, `populateFuelFieldsFromMemo`; `com.cuenti.app.views.components.UiNotifier`.
- Produces: `Span` with id `fuel-info` (info/warning line inside the fuel section); `Runnable updateFuelHints`.

- [ ] **Step 1: Add i18n keys**

`messages.properties`:

```properties
vehicles.form_info={0} km since last fill-up
vehicles.form_info_consumption={0} km since last, ~{1} L/100km
vehicles.warn_odometer_not_increasing=Odometer is not higher than the last reading ({0})
vehicles.warn_odometer_jump=Very large jump since the last reading ({0} km) — typo?
vehicles.warn_liters_implausible=Implausible liters value
vehicles.warn_no_fuel_data=No km/liters entered — this entry will not appear in the vehicle report
```

`messages_de.properties`:

```properties
vehicles.form_info={0} km seit letzter Betankung
vehicles.form_info_consumption={0} km seit letzter Betankung, ~{1} L/100km
vehicles.warn_odometer_not_increasing=Kilometerstand nicht höher als letzter Wert ({0})
vehicles.warn_odometer_jump=Sehr großer Sprung seit letztem Wert ({0} km) — Tippfehler?
vehicles.warn_liters_implausible=Unplausible Literangabe
vehicles.warn_no_fuel_data=Keine km/Liter eingetragen — Eintrag erscheint nicht im Fahrzeugbericht
```

- [ ] **Step 2: Write the failing tests**

Add to `UC110FuelEntryFormTest`. Import `com.vaadin.flow.component.html.Span`.

```java
    @Test
    @UseCase(id = "UC-110", scenario = "Non-increasing odometer shows warning, save not blocked")
    void nonIncreasingOdometer_warnsButDoesNotBlock() {
        seedFuelCategory(); // last odometer = 100000
        navigate(TransactionHistoryView.class);
        fireShortcut(Key.KEY_N, KeyModifier.ALT);

        ComboBox<Category> categoryCombo = $(ComboBox.class).withId("tx-category").single();
        test(categoryCombo).selectItem(fuelCategory.getFullName());

        test($(IntegerField.class).withId("fuel-odometer").single()).setValue(99000);

        Span info = $(Span.class).withId("fuel-info").single();
        assertThat(info.getText()).contains("100000");
        assertThat(info.getElement().getThemeList()).contains("badge", "warning");
    }

    @Test
    @UseCase(id = "UC-110", scenario = "Distance and consumption info line")
    void plausibleEntry_showsDistanceAndConsumption() {
        seedFuelCategory(); // last odometer = 100000
        navigate(TransactionHistoryView.class);
        fireShortcut(Key.KEY_N, KeyModifier.ALT);

        ComboBox<Category> categoryCombo = $(ComboBox.class).withId("tx-category").single();
        test(categoryCombo).selectItem(fuelCategory.getFullName());

        test($(IntegerField.class).withId("fuel-odometer").single()).setValue(100340);
        test($(NumberField.class).withId("fuel-liters").single()).setValue(41.3);
        test($(Checkbox.class).withId("fuel-full").single()).click();

        Span info = $(Span.class).withId("fuel-info").single();
        // 340 km since last, 41.3 L / 340 km = 12.1 L/100km
        assertThat(info.getText()).contains("340").contains("12.1");
    }

    @Test
    @UseCase(id = "UC-110", scenario = "Implausible liters warns")
    void implausibleLiters_warns() {
        seedFuelCategory();
        navigate(TransactionHistoryView.class);
        fireShortcut(Key.KEY_N, KeyModifier.ALT);

        ComboBox<Category> categoryCombo = $(ComboBox.class).withId("tx-category").single();
        test(categoryCombo).selectItem(fuelCategory.getFullName());

        test($(NumberField.class).withId("fuel-liters").single()).setValue(413.0);

        NumberField liters = $(NumberField.class).withId("fuel-liters").single();
        assertThat(liters.getHelperText()).isNotBlank();
    }
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./mvnw -q test -Dtest=UC110FuelEntryFormTest`
Expected: new tests FAIL — no `fuel-info` span exists yet.

- [ ] **Step 4: Implement hints and warnings**

In the Task 3 fuel section block of `openTransactionDialog`, after the `fuelSection` Div creation, add the info span and an `updateFuelHints` runnable, then wire it. Import `com.vaadin.flow.component.html.Span`, `java.math.RoundingMode` (already imported at class level — verify).

```java
        Span fuelInfoLine = new Span();
        fuelInfoLine.setId("fuel-info");
        fuelInfoLine.setVisible(false);
        fuelSection.add(fuelInfoLine);

        Runnable updateFuelHints = () -> {
            // liters plausibility
            Double liters = fuelLitersField.getValue();
            fuelLitersField.setHelperText(liters != null && (liters <= 0 || liters > 200)
                    ? getTranslation("vehicles.warn_liters_implausible") : null);

            // odometer vs last known
            Integer odometer = fuelOdometerField.getValue();
            BigDecimal last = fuelLastOdometer[0];
            fuelInfoLine.setVisible(false);
            fuelInfoLine.getElement().getThemeList().clear();
            if (odometer == null || last == null) return;

            BigDecimal distance = BigDecimal.valueOf(odometer).subtract(last);
            if (distance.compareTo(BigDecimal.ZERO) <= 0) {
                fuelInfoLine.setText(getTranslation("vehicles.warn_odometer_not_increasing", last.toPlainString()));
                fuelInfoLine.getElement().getThemeList().addAll(java.util.List.of("badge", "warning"));
                fuelInfoLine.setVisible(true);
            } else if (distance.compareTo(BigDecimal.valueOf(2000)) > 0) {
                fuelInfoLine.setText(getTranslation("vehicles.warn_odometer_jump", distance.toPlainString()));
                fuelInfoLine.getElement().getThemeList().addAll(java.util.List.of("badge", "warning"));
                fuelInfoLine.setVisible(true);
            } else if (liters != null && liters > 0 && Boolean.TRUE.equals(fuelFullTankBox.getValue())) {
                BigDecimal consumption = BigDecimal.valueOf(liters)
                        .divide(distance, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
                fuelInfoLine.setText(getTranslation("vehicles.form_info_consumption",
                        distance.toPlainString(), consumption.toPlainString()));
                fuelInfoLine.getElement().getThemeList().add("badge");
                fuelInfoLine.setVisible(true);
            } else {
                fuelInfoLine.setText(getTranslation("vehicles.form_info", distance.toPlainString()));
                fuelInfoLine.getElement().getThemeList().add("badge");
                fuelInfoLine.setVisible(true);
            }
        };
```

Wire it — extend the existing listeners from Task 3 (replace the three single-call lambdas):

```java
        fuelOdometerField.addValueChangeListener(e -> { syncMemoFromFuelFields.run(); updateFuelHints.run(); });
        fuelLitersField.addValueChangeListener(e -> { syncMemoFromFuelFields.run(); updateFuelHints.run(); });
        fuelFullTankBox.addValueChangeListener(e -> { syncMemoFromFuelFields.run(); updateFuelHints.run(); });
```

(Define `updateFuelHints` BEFORE the listener registrations; order inside the block: fields → holders → refreshLastOdometerHint → syncMemoFromFuelFields → fuelInfoLine + updateFuelHints → listeners → populateFuelFieldsFromMemo → memo listener → updateFuelVisibility → category/date listeners.)

Also call `updateFuelHints.run()` at the end of `refreshLastOdometerHint` (so hints refresh when category/date changes) and inside the memo reparse listener after `populateFuelFieldsFromMemo.run()`.

- [ ] **Step 5: Empty-save soft notification**

In BOTH save listeners (`saveButton` line ~1613 and `addKeepButton` line ~1632), directly before the `saveFromTabs(...)` call, add:

```java
                    if (fuelSection.isVisible()
                            && fuelOdometerField.getValue() == null && fuelLitersField.getValue() == null) {
                        Notification.show(getTranslation("vehicles.warn_no_fuel_data"), 4000, Notification.Position.MIDDLE);
                    }
```

Save proceeds regardless — this is informational only. (`Notification` is already imported in the class.)

- [ ] **Step 6: Run tests**

Run: `./mvnw -q test -Dtest=UC110FuelEntryFormTest`
Expected: all 7 tests PASS.

- [ ] **Step 7: Run full suite**

Run: `./mvnw -q test`
Expected: all PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/cuenti/app/views/TransactionHistoryView.java src/main/resources/messages.properties src/main/resources/messages_de.properties src/test/java/com/cuenti/app/views/UC110FuelEntryFormTest.java
git commit -m "feat(vehicles): fuel entry plausibility warnings and consumption preview

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Notes for implementers

- `VehicleReportService` is `@RequiredArgsConstructor` over `(TransactionService, ExchangeRateService)` — unit tests construct it directly with Mockito mocks; no Spring context needed for Tasks 1–2.
- The transaction dialog is built entirely with local variables inside `openTransactionDialog` — that is why the fuel section uses component ids (not view fields) for test access, matching no existing pattern conflict.
- `test(...)` wrappers in Browserless tests simulate client-originated events, so `e.isFromClient()` guards behave as in production.
- Same-day previous fill-ups are excluded from `lastOdometer` (strictly-before date filter) — acceptable per spec; the hint is advisory only.
- If `getElement().getThemeList()` assertions prove brittle in Browserless, assert on `info.getText()` content only and drop the theme assertion — the warning text is the contract.
