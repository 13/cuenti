# REST API Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose budgets, forecasts, vehicle reports, audit log, saved views, transaction splits, and transaction pagination/filtering through the REST API so the Flutter app can reach feature parity with the Vaadin web views.

**Architecture:** Computation logic embedded in Vaadin views (`ForecastsView`, `VehiclesView`) is extracted into services (`ForecastService`, `VehicleReportService`) shared by views and new `@RestController`s. New controllers follow the existing pattern exactly: `@RestController @RequiredArgsConstructor`, `SecurityUtil.getAuthenticatedUsername()` 401 guard, user-scoping through services, DTOs in `api/dto`, static mapping methods in `DtoMapper`, `@PreAuthorize("hasRole('ADMIN')")` for admin endpoints.

**Tech Stack:** Spring Boot 4.x, Spring Security (JWT for `/api/**`), Spring Data JPA (PostgreSQL prod, H2 `test` profile), Lombok, JUnit 6 + MockMvc + spring-security-test, Maven (`./mvnw`).

**Spec:** `docs/superpowers/specs/2026-07-12-rest-api-expansion-design.md`

## Global Constraints

- Backward compatibility: shipped mobile app v1.3.1 must keep working. `GET /api/transactions` without `page`/`size` returns a plain JSON array. `TransactionDTO` without `splits` still parses.
- All new endpoints require JWT auth (the `/api/**` security chain already enforces `authenticated()`); controllers additionally do the `SecurityUtil.getAuthenticatedUsername()` → 401 guard like every existing controller.
- User-scoping: a user must never see or modify another user's data (404 for foreign entity ids, matching existing controllers).
- Error convention: 404 missing/foreign entities, 400 + message body for validation failures, 403 admin gating. No new global exception machinery.
- Tests: `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional` classes; H2 in-memory (`application-test.properties` exists). The `test` profile seeds a `demo` user via `DataInitializer` (existing view tests rely on it with `@WithMockUser(username = "demo")`).
- Run a single test class: `./mvnw test -Dtest=<ClassName>`. Run everything before finishing a task: `./mvnw test`.
- Commit after each task with a `feat:`/`refactor:`/`test:` message.

## File Structure Overview

```
src/main/java/com/cuenti/app/
  api/
    BudgetApiController.java          (new, task 1)
    ForecastApiController.java        (new, task 3)
    VehicleApiController.java         (new, task 5)
    AuditLogApiController.java        (new, task 6)
    SavedViewApiController.java       (new, task 7)
    TransactionApiController.java     (modify, tasks 8, 9)
    UserApiController.java            (modify, task 5)
    dto/
      BudgetDTO.java                  (new, task 1)
      BudgetProgressDTO.java          (new, task 1)
      ForecastDTO.java                (new, task 3)
      VehicleReportDTO.java           (new, task 5)
      AuditLogDTO.java                (new, task 6)
      SavedViewDTO.java               (new, task 7)
      TransactionSplitDTO.java        (new, task 8)
      PagedResponse.java              (new, task 9)
      TransactionDTO.java             (modify, task 8)
      UserProfileDTO.java             (modify, task 5)
      DtoMapper.java                  (modify, tasks 1, 6, 7, 8)
  service/
    ForecastService.java              (new, task 3)
    VehicleReportService.java         (new, task 4)
    ScheduledTransactionService.java  (modify, task 2)
    SavedViewService.java             (modify, task 7)
    TransactionService.java           (modify, task 9)
  repository/
    TransactionRepository.java        (modify, task 9)
  views/
    ForecastsView.java                (modify, tasks 2, 3)
    VehiclesView.java                 (modify, task 4)
```

---

### Task 1: Budget API

**Files:**
- Create: `src/main/java/com/cuenti/app/api/dto/BudgetDTO.java`
- Create: `src/main/java/com/cuenti/app/api/dto/BudgetProgressDTO.java`
- Create: `src/main/java/com/cuenti/app/api/BudgetApiController.java`
- Modify: `src/main/java/com/cuenti/app/api/dto/DtoMapper.java` (add `toBudgetDTO`)
- Test: `src/test/java/com/cuenti/app/api/BudgetApiControllerTest.java`

**Interfaces:**
- Consumes: `BudgetService.getBudgets(User)`, `saveBudget(Budget)`, `deleteBudget(Budget)`, `existsForCategory(User, Long, Long)`, `getSpentThisMonth(User)` — all exist. `CategoryService.findById(Long): Optional<Category>`. `UserService.findByUsername(String): User`.
- Produces: `GET/POST /api/budgets`, `PUT/DELETE /api/budgets/{id}`, `GET /api/budgets/progress`. `DtoMapper.toBudgetDTO(Budget): BudgetDTO`.

- [ ] **Step 1: Write the failing test**

```java
package com.cuenti.app.api;

import com.cuenti.app.model.Category;
import com.cuenti.app.model.User;
import com.cuenti.app.service.CategoryService;
import com.cuenti.app.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "demo")
class BudgetApiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CategoryService categoryService;
    @Autowired UserService userService;

    private Long categoryId;

    @BeforeEach
    void setUp() {
        Category cat = new Category();
        cat.setName("Fuel-" + System.nanoTime());
        cat.setType(Category.CategoryType.EXPENSE);
        categoryId = categoryService.saveCategory(cat).getId();
    }

    private long createBudget(long catId, String limit) throws Exception {
        String body = mockMvc.perform(post("/api/budgets")
                        .contentType("application/json")
                        .content("{\"categoryId\":" + catId + ",\"monthlyLimit\":" + limit + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void createAndListBudget() throws Exception {
        long id = createBudget(categoryId, "250.00");

        String body = mockMvc.perform(get("/api/budgets"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode list = objectMapper.readTree(body);
        JsonNode created = null;
        for (JsonNode n : list) if (n.get("id").asLong() == id) created = n;
        assertThat(created).isNotNull();
        assertThat(created.get("categoryId").asLong()).isEqualTo(categoryId);
        assertThat(created.get("monthlyLimit").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(created.get("active").asBoolean()).isTrue();
    }

    @Test
    void duplicateCategoryRejected() throws Exception {
        createBudget(categoryId, "100");
        mockMvc.perform(post("/api/budgets")
                        .contentType("application/json")
                        .content("{\"categoryId\":" + categoryId + ",\"monthlyLimit\":50}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownCategoryIs404() throws Exception {
        mockMvc.perform(post("/api/budgets")
                        .contentType("application/json")
                        .content("{\"categoryId\":999999,\"monthlyLimit\":50}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAndDeleteBudget() throws Exception {
        long id = createBudget(categoryId, "100");

        mockMvc.perform(put("/api/budgets/" + id)
                        .contentType("application/json")
                        .content("{\"categoryId\":" + categoryId + ",\"monthlyLimit\":175.50,\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyLimit").value(175.50))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/budgets/" + id)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/budgets/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void progressReturnsSpentAndRemaining() throws Exception {
        createBudget(categoryId, "300");
        String body = mockMvc.perform(get("/api/budgets/progress"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode mine = null;
        for (JsonNode n : objectMapper.readTree(body)) {
            if (n.get("categoryId").asLong() == categoryId) mine = n;
        }
        assertThat(mine).isNotNull();
        assertThat(mine.get("monthlyLimit").decimalValue()).isEqualByComparingTo("300");
        assertThat(mine.get("spent").decimalValue()).isEqualByComparingTo("0");
        assertThat(mine.get("remaining").decimalValue()).isEqualByComparingTo("300");
    }

    @Test
    void foreignBudgetInvisibleToOtherUser() throws Exception {
        long id = createBudget(categoryId, "100");
        userService.registerUser("intruder1", "intruder1@x.com", "password123", "In", "Truder");

        mockMvc.perform(put("/api/budgets/" + id).with(user("intruder1"))
                        .contentType("application/json")
                        .content("{\"categoryId\":" + categoryId + ",\"monthlyLimit\":1}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/budgets/" + id).with(user("intruder1")))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=BudgetApiControllerTest`
Expected: FAIL — 404s on `/api/budgets` (no controller yet).

- [ ] **Step 3: Create the DTOs**

`src/main/java/com/cuenti/app/api/dto/BudgetDTO.java`:

```java
package com.cuenti.app.api.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetDTO {
    private Long id;
    private Long categoryId;
    private String categoryName;
    private BigDecimal monthlyLimit;
    private boolean active;
}
```

`src/main/java/com/cuenti/app/api/dto/BudgetProgressDTO.java`:

```java
package com.cuenti.app.api.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetProgressDTO {
    private Long budgetId;
    private Long categoryId;
    private String categoryName;
    private BigDecimal monthlyLimit;
    private BigDecimal spent;
    private BigDecimal remaining;
    private boolean active;
}
```

Add to `DtoMapper.java` (import `com.cuenti.app.model.Budget`):

```java
    public static BudgetDTO toBudgetDTO(Budget b) {
        return BudgetDTO.builder()
                .id(b.getId())
                .categoryId(b.getCategory().getId())
                .categoryName(b.getCategory().getFullName())
                .monthlyLimit(b.getMonthlyLimit())
                .active(b.isActive())
                .build();
    }
```

- [ ] **Step 4: Create the controller**

`src/main/java/com/cuenti/app/api/BudgetApiController.java`:

```java
package com.cuenti.app.api;

import com.cuenti.app.api.dto.BudgetDTO;
import com.cuenti.app.api.dto.BudgetProgressDTO;
import com.cuenti.app.api.dto.DtoMapper;
import com.cuenti.app.model.Budget;
import com.cuenti.app.model.Category;
import com.cuenti.app.model.User;
import com.cuenti.app.service.BudgetService;
import com.cuenti.app.service.CategoryService;
import com.cuenti.app.service.SecurityUtil;
import com.cuenti.app.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetApiController {

    private final BudgetService budgetService;
    private final CategoryService categoryService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<BudgetDTO>> getBudgets() {
        User user = currentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(budgetService.getBudgets(user).stream()
                .map(DtoMapper::toBudgetDTO)
                .collect(Collectors.toList()));
    }

    @PostMapping
    public ResponseEntity<?> createBudget(@RequestBody BudgetDTO dto) {
        User user = currentUser();
        if (user == null) return ResponseEntity.status(401).build();

        Category category = categoryService.findById(dto.getCategoryId()).orElse(null);
        if (category == null) return ResponseEntity.notFound().build();
        if (budgetService.existsForCategory(user, dto.getCategoryId(), null)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Budget for this category already exists"));
        }

        Budget saved = budgetService.saveBudget(Budget.builder()
                .user(user)
                .category(category)
                .monthlyLimit(dto.getMonthlyLimit())
                .active(true)
                .build());
        return ResponseEntity.ok(DtoMapper.toBudgetDTO(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBudget(@PathVariable Long id, @RequestBody BudgetDTO dto) {
        User user = currentUser();
        if (user == null) return ResponseEntity.status(401).build();

        Budget budget = findOwn(user, id);
        if (budget == null) return ResponseEntity.notFound().build();

        if (dto.getCategoryId() != null && !dto.getCategoryId().equals(budget.getCategory().getId())) {
            Category category = categoryService.findById(dto.getCategoryId()).orElse(null);
            if (category == null) return ResponseEntity.notFound().build();
            if (budgetService.existsForCategory(user, dto.getCategoryId(), id)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Budget for this category already exists"));
            }
            budget.setCategory(category);
        }
        if (dto.getMonthlyLimit() != null) budget.setMonthlyLimit(dto.getMonthlyLimit());
        budget.setActive(dto.isActive());

        return ResponseEntity.ok(DtoMapper.toBudgetDTO(budgetService.saveBudget(budget)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBudget(@PathVariable Long id) {
        User user = currentUser();
        if (user == null) return ResponseEntity.status(401).build();

        Budget budget = findOwn(user, id);
        if (budget == null) return ResponseEntity.notFound().build();

        budgetService.deleteBudget(budget);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/progress")
    public ResponseEntity<List<BudgetProgressDTO>> getProgress() {
        User user = currentUser();
        if (user == null) return ResponseEntity.status(401).build();

        Map<Long, BigDecimal> spentByCategory = budgetService.getSpentThisMonth(user);
        List<BudgetProgressDTO> progress = budgetService.getBudgets(user).stream()
                .map(b -> {
                    BigDecimal spent = spentByCategory.getOrDefault(b.getCategory().getId(), BigDecimal.ZERO);
                    return BudgetProgressDTO.builder()
                            .budgetId(b.getId())
                            .categoryId(b.getCategory().getId())
                            .categoryName(b.getCategory().getFullName())
                            .monthlyLimit(b.getMonthlyLimit())
                            .spent(spent)
                            .remaining(b.getMonthlyLimit().subtract(spent))
                            .active(b.isActive())
                            .build();
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(progress);
    }

    private User currentUser() {
        return SecurityUtil.getAuthenticatedUsername()
                .map(userService::findByUsername)
                .orElse(null);
    }

    private Budget findOwn(User user, Long id) {
        return budgetService.getBudgets(user).stream()
                .filter(b -> b.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw test -Dtest=BudgetApiControllerTest`
Expected: PASS (all 6 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/cuenti/app/api/BudgetApiController.java \
        src/main/java/com/cuenti/app/api/dto/BudgetDTO.java \
        src/main/java/com/cuenti/app/api/dto/BudgetProgressDTO.java \
        src/main/java/com/cuenti/app/api/dto/DtoMapper.java \
        src/test/java/com/cuenti/app/api/BudgetApiControllerTest.java
git commit -m "feat(api): add budget CRUD and progress endpoints"
```

---

### Task 2: Shared recurrence helper

Recurrence-advance logic is duplicated in `ScheduledTransactionService.updateToNextOccurrence` (private) and `ForecastsView.getNextOccurrence` (private). Consolidate into one public static method so `ForecastService` (task 3) can reuse it.

**Files:**
- Modify: `src/main/java/com/cuenti/app/service/ScheduledTransactionService.java`
- Modify: `src/main/java/com/cuenti/app/views/ForecastsView.java` (delete `getNextOccurrence`, lines ~213-236; call the service method)
- Test: `src/test/java/com/cuenti/app/service/RecurrenceAdvanceTest.java`

**Interfaces:**
- Produces: `public static LocalDateTime advanceOccurrence(LocalDateTime current, ScheduledTransaction scheduled)` on `ScheduledTransactionService`. Behavior identical to the existing private switch (DAILY/WEEKLY/BI_WEEKLY/MONTHLY/MONTHLY_LAST_DAY/YEARLY/EVERY_FRIDAY/EVERY_SATURDAY/EVERY_WEEKDAY; `recurrenceValue` defaults to 1 when null/≤0).

- [ ] **Step 1: Write the failing test**

`src/test/java/com/cuenti/app/service/RecurrenceAdvanceTest.java` (plain JUnit, no Spring):

```java
package com.cuenti.app.service;

import com.cuenti.app.model.ScheduledTransaction;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RecurrenceAdvanceTest {

    private ScheduledTransaction scheduled(ScheduledTransaction.RecurrencePattern pattern, Integer value) {
        ScheduledTransaction st = new ScheduledTransaction();
        st.setRecurrencePattern(pattern);
        st.setRecurrenceValue(value);
        return st;
    }

    @Test
    void monthlyAdvancesByValue() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 15, 12, 0);
        LocalDateTime next = ScheduledTransactionService.advanceOccurrence(
                start, scheduled(ScheduledTransaction.RecurrencePattern.MONTHLY, 2));
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 3, 15, 12, 0));
    }

    @Test
    void nullRecurrenceValueDefaultsToOne() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime next = ScheduledTransactionService.advanceOccurrence(
                start, scheduled(ScheduledTransaction.RecurrencePattern.DAILY, null));
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 1, 2, 0, 0));
    }

    @Test
    void monthlyLastDaySnapsToEndOfMonth() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 31, 8, 0);
        LocalDateTime next = ScheduledTransactionService.advanceOccurrence(
                start, scheduled(ScheduledTransaction.RecurrencePattern.MONTHLY_LAST_DAY, 1));
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 2, 28, 8, 0));
    }

    @Test
    void everyWeekdaySkipsWeekend() {
        LocalDateTime friday = LocalDateTime.of(2026, 7, 10, 9, 0); // a Friday
        LocalDateTime next = ScheduledTransactionService.advanceOccurrence(
                friday, scheduled(ScheduledTransaction.RecurrencePattern.EVERY_WEEKDAY, 1));
        assertThat(next.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=RecurrenceAdvanceTest`
Expected: COMPILE FAILURE — `advanceOccurrence` not defined.

- [ ] **Step 3: Add the public static method and refactor callers**

In `ScheduledTransactionService`, add (imports `java.time.DayOfWeek`, `java.time.temporal.TemporalAdjusters` already present):

```java
    /** Advance one occurrence according to the schedule's recurrence pattern. */
    public static LocalDateTime advanceOccurrence(LocalDateTime current, ScheduledTransaction scheduled) {
        LocalDateTime next = current;
        int value = (scheduled.getRecurrenceValue() != null && scheduled.getRecurrenceValue() > 0)
                    ? scheduled.getRecurrenceValue() : 1;

        switch (scheduled.getRecurrencePattern()) {
            case DAILY -> next = next.plusDays(value);
            case WEEKLY -> next = next.plusWeeks(value);
            case BI_WEEKLY -> next = next.plusWeeks(2);
            case MONTHLY -> next = next.plusMonths(value);
            case MONTHLY_LAST_DAY -> next = next.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
            case YEARLY -> next = next.plusYears(value);
            case EVERY_FRIDAY -> next = next.with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
            case EVERY_SATURDAY -> next = next.with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
            case EVERY_WEEKDAY -> {
                next = next.plusDays(1);
                while (next.getDayOfWeek() == DayOfWeek.SATURDAY || next.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    next = next.plusDays(1);
                }
            }
        }
        return next;
    }
```

Then shrink the private `updateToNextOccurrence` to:

```java
    private void updateToNextOccurrence(ScheduledTransaction scheduled) {
        scheduled.setNextOccurrence(advanceOccurrence(scheduled.getNextOccurrence(), scheduled));
        repository.save(scheduled);
    }
```

In `ForecastsView`: delete the private `getNextOccurrence` method entirely and replace its two call sites (`loadData`, around lines 151 and 180) with `ScheduledTransactionService.advanceOccurrence(occurrence, st)` (add the import if the class isn't already imported).

- [ ] **Step 4: Run tests to verify everything passes**

Run: `./mvnw test -Dtest=RecurrenceAdvanceTest` — Expected: PASS.
Run: `./mvnw test` — Expected: PASS (view tests still green; forecast behavior unchanged).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cuenti/app/service/ScheduledTransactionService.java \
        src/main/java/com/cuenti/app/views/ForecastsView.java \
        src/test/java/com/cuenti/app/service/RecurrenceAdvanceTest.java
git commit -m "refactor: extract shared recurrence advance into ScheduledTransactionService"
```

---

### Task 3: ForecastService + Forecast API

**Files:**
- Create: `src/main/java/com/cuenti/app/service/ForecastService.java`
- Create: `src/main/java/com/cuenti/app/api/dto/ForecastDTO.java`
- Create: `src/main/java/com/cuenti/app/api/ForecastApiController.java`
- Modify: `src/main/java/com/cuenti/app/views/ForecastsView.java` (replace the computation block in `loadData` with a service call; keep all rendering)
- Test: `src/test/java/com/cuenti/app/service/ForecastServiceTest.java`
- Test: `src/test/java/com/cuenti/app/api/ForecastApiControllerTest.java`

**Interfaces:**
- Consumes: `ScheduledTransactionService.getByUser(User)`, `ScheduledTransactionService.advanceOccurrence(...)` (task 2), `AccountService.getAccountsByUser(User)`, `ExchangeRateService.convert(BigDecimal, String, String)`.
- Produces: `ForecastService.getForecast(User user, int year): ForecastDTO`. `GET /api/forecasts?year=YYYY` (year optional, defaults to current year). `ForecastDTO` fields: `year`, `months` (list of `MonthForecast{month, income, expense, net}` — all 12 months present, zeros where nothing scheduled), `totalIncome`, `totalExpense`, `netForecast`, `currency`.

- [ ] **Step 1: Write the failing service unit test**

`src/test/java/com/cuenti/app/service/ForecastServiceTest.java` (Mockito, no Spring context):

```java
package com.cuenti.app.service;

import com.cuenti.app.api.dto.ForecastDTO;
import com.cuenti.app.model.Account;
import com.cuenti.app.model.ScheduledTransaction;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ForecastServiceTest {

    private ScheduledTransactionService scheduledService;
    private AccountService accountService;
    private ExchangeRateService exchangeRateService;
    private ForecastService forecastService;

    private final User user = new User();
    private final Account account = new Account();

    @BeforeEach
    void setUp() {
        scheduledService = mock(ScheduledTransactionService.class);
        accountService = mock(AccountService.class);
        exchangeRateService = mock(ExchangeRateService.class);
        forecastService = new ForecastService(scheduledService, accountService, exchangeRateService);

        user.setId(1L);
        user.setDefaultCurrency("EUR");
        account.setId(10L);
        account.setCurrency("EUR");
        account.setExcludeFromReports(false);
        // identity conversion
        when(exchangeRateService.convert(any(BigDecimal.class), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(accountService.getAccountsByUser(user)).thenReturn(List.of(account));
    }

    private ScheduledTransaction monthlyExpense(BigDecimal amount, LocalDateTime next) {
        ScheduledTransaction st = new ScheduledTransaction();
        st.setEnabled(true);
        st.setType(Transaction.TransactionType.EXPENSE);
        st.setFromAccount(account);
        st.setAmount(amount);
        st.setRecurrencePattern(ScheduledTransaction.RecurrencePattern.MONTHLY);
        st.setRecurrenceValue(1);
        st.setNextOccurrence(next);
        return st;
    }

    @Test
    void monthlyExpenseAppearsInEveryMonthFromItsStart() {
        when(scheduledService.getByUser(user)).thenReturn(List.of(
                monthlyExpense(new BigDecimal("100"), LocalDateTime.of(2026, 3, 5, 0, 0))));

        ForecastDTO forecast = forecastService.getForecast(user, 2026);

        assertThat(forecast.getYear()).isEqualTo(2026);
        assertThat(forecast.getMonths()).hasSize(12);
        assertThat(forecast.getMonths().get(0).getExpense()).isEqualByComparingTo("0"); // January
        assertThat(forecast.getMonths().get(2).getExpense()).isEqualByComparingTo("100"); // March
        assertThat(forecast.getMonths().get(11).getExpense()).isEqualByComparingTo("100"); // December
        assertThat(forecast.getTotalExpense()).isEqualByComparingTo("1000"); // Mar..Dec = 10 months
        assertThat(forecast.getNetForecast()).isEqualByComparingTo("-1000");
        assertThat(forecast.getCurrency()).isEqualTo("EUR");
    }

    @Test
    void disabledAndTransferSchedulesIgnored() {
        ScheduledTransaction disabled = monthlyExpense(new BigDecimal("50"), LocalDateTime.of(2026, 1, 1, 0, 0));
        disabled.setEnabled(false);
        ScheduledTransaction transfer = monthlyExpense(new BigDecimal("50"), LocalDateTime.of(2026, 1, 1, 0, 0));
        transfer.setType(Transaction.TransactionType.TRANSFER);
        when(scheduledService.getByUser(user)).thenReturn(List.of(disabled, transfer));

        ForecastDTO forecast = forecastService.getForecast(user, 2026);
        assertThat(forecast.getTotalExpense()).isEqualByComparingTo("0");
        assertThat(forecast.getTotalIncome()).isEqualByComparingTo("0");
    }

    @Test
    void excludedAccountIgnored() {
        account.setExcludeFromReports(true);
        when(scheduledService.getByUser(user)).thenReturn(List.of(
                monthlyExpense(new BigDecimal("100"), LocalDateTime.of(2026, 1, 5, 0, 0))));

        ForecastDTO forecast = forecastService.getForecast(user, 2026);
        assertThat(forecast.getTotalExpense()).isEqualByComparingTo("0");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=ForecastServiceTest`
Expected: COMPILE FAILURE — `ForecastService`, `ForecastDTO` not defined.

- [ ] **Step 3: Create DTO and service**

`src/main/java/com/cuenti/app/api/dto/ForecastDTO.java`:

```java
package com.cuenti.app.api.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForecastDTO {
    private int year;
    private List<MonthForecast> months;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netForecast;
    private String currency;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthForecast {
        private String month; // "YYYY-MM"
        private BigDecimal income;
        private BigDecimal expense;
        private BigDecimal net;
    }
}
```

`src/main/java/com/cuenti/app/service/ForecastService.java` — the algorithm is a verbatim extraction of `ForecastsView.loadData`'s computation half:

```java
package com.cuenti.app.service;

import com.cuenti.app.api.dto.ForecastDTO;
import com.cuenti.app.model.Account;
import com.cuenti.app.model.ScheduledTransaction;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Projects scheduled transactions across a calendar year: per-month income
 * and expense in the user's default currency. Transfers are ignored, as are
 * schedules on accounts excluded from reports.
 */
@Service
@RequiredArgsConstructor
public class ForecastService {

    private final ScheduledTransactionService scheduledService;
    private final AccountService accountService;
    private final ExchangeRateService exchangeRateService;

    @Transactional(readOnly = true)
    public ForecastDTO getForecast(User user, int year) {
        Set<Long> reportableAccountIds = accountService.getAccountsByUser(user).stream()
                .filter(a -> !a.isExcludeFromReports())
                .map(Account::getId)
                .collect(Collectors.toSet());

        Map<String, BigDecimal> monthlyIncomes = new TreeMap<>();
        Map<String, BigDecimal> monthlyExpenses = new TreeMap<>();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;

        for (ScheduledTransaction st : scheduledService.getByUser(user)) {
            if (!st.isEnabled()) continue;

            Account fromAccount = st.getFromAccount();
            Account toAccount = st.getToAccount();

            if (st.getType() == Transaction.TransactionType.INCOME) {
                if (toAccount == null || !reportableAccountIds.contains(toAccount.getId())) continue;
            } else if (st.getType() == Transaction.TransactionType.EXPENSE) {
                if (fromAccount == null || !reportableAccountIds.contains(fromAccount.getId())) continue;
            } else {
                continue; // transfers ignored, matching the web view
            }

            LocalDateTime occurrence = st.getNextOccurrence();
            LocalDate occurrenceDate = occurrence.toLocalDate();

            while (occurrenceDate.getYear() < year) {
                occurrence = ScheduledTransactionService.advanceOccurrence(occurrence, st);
                occurrenceDate = occurrence.toLocalDate();
            }

            while (occurrenceDate.getYear() == year) {
                String monthKey = String.format("%d-%02d", occurrenceDate.getYear(), occurrenceDate.getMonthValue());

                if (st.getType() == Transaction.TransactionType.INCOME) {
                    String currency = toAccount != null ? toAccount.getCurrency() : user.getDefaultCurrency();
                    BigDecimal converted = exchangeRateService.convert(st.getAmount(), currency, user.getDefaultCurrency());
                    monthlyIncomes.merge(monthKey, converted, BigDecimal::add);
                    totalIncome = totalIncome.add(converted);
                } else {
                    String currency = fromAccount != null ? fromAccount.getCurrency() : user.getDefaultCurrency();
                    BigDecimal converted = exchangeRateService.convert(st.getAmount(), currency, user.getDefaultCurrency());
                    monthlyExpenses.merge(monthKey, converted, BigDecimal::add);
                    totalExpense = totalExpense.add(converted);
                }

                occurrence = ScheduledTransactionService.advanceOccurrence(occurrence, st);
                occurrenceDate = occurrence.toLocalDate();
            }
        }

        List<ForecastDTO.MonthForecast> months = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            String key = String.format("%d-%02d", year, m);
            BigDecimal income = monthlyIncomes.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal expense = monthlyExpenses.getOrDefault(key, BigDecimal.ZERO);
            months.add(ForecastDTO.MonthForecast.builder()
                    .month(key)
                    .income(income)
                    .expense(expense)
                    .net(income.subtract(expense))
                    .build());
        }

        return ForecastDTO.builder()
                .year(year)
                .months(months)
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netForecast(totalIncome.subtract(totalExpense))
                .currency(user.getDefaultCurrency())
                .build();
    }
}
```

- [ ] **Step 4: Run service test to verify it passes**

Run: `./mvnw test -Dtest=ForecastServiceTest`
Expected: PASS.

- [ ] **Step 5: Write the failing controller test**

`src/test/java/com/cuenti/app/api/ForecastApiControllerTest.java`:

```java
package com.cuenti.app.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "demo")
class ForecastApiControllerTest {

    @Autowired MockMvc mockMvc;

    @Test
    void defaultsToCurrentYearWithTwelveMonths() throws Exception {
        mockMvc.perform(get("/api/forecasts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(Year.now().getValue()))
                .andExpect(jsonPath("$.months.length()").value(12))
                .andExpect(jsonPath("$.currency").isNotEmpty());
    }

    @Test
    void explicitYearRespected() throws Exception {
        mockMvc.perform(get("/api/forecasts").param("year", "2030"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2030))
                .andExpect(jsonPath("$.months[0].month").value("2030-01"));
    }
}
```

- [ ] **Step 6: Run controller test to verify it fails**

Run: `./mvnw test -Dtest=ForecastApiControllerTest`
Expected: FAIL — 404 (no controller).

- [ ] **Step 7: Create the controller**

`src/main/java/com/cuenti/app/api/ForecastApiController.java`:

```java
package com.cuenti.app.api;

import com.cuenti.app.api.dto.ForecastDTO;
import com.cuenti.app.model.User;
import com.cuenti.app.service.ForecastService;
import com.cuenti.app.service.SecurityUtil;
import com.cuenti.app.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Year;

@RestController
@RequestMapping("/api/forecasts")
@RequiredArgsConstructor
public class ForecastApiController {

    private final ForecastService forecastService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<ForecastDTO> getForecast(@RequestParam(required = false) Integer year) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();
        User user = userService.findByUsername(username);

        int forecastYear = year != null ? year : Year.now().getValue();
        return ResponseEntity.ok(forecastService.getForecast(user, forecastYear));
    }
}
```

- [ ] **Step 8: Refactor ForecastsView to use the service**

In `ForecastsView.loadData()`: delete the local computation (the scheduled-transaction loop, `monthlyIncomes`/`monthlyExpenses` maps, totals — everything up to `BigDecimal netForecast = ...`) and replace with:

```java
        ForecastDTO forecast = forecastService.getForecast(currentUser, selectedYear);
        BigDecimal totalIncome = forecast.getTotalIncome();
        BigDecimal totalExpense = forecast.getTotalExpense();
        BigDecimal netForecast = forecast.getNetForecast();

        Map<String, BigDecimal> monthlyIncomes = new TreeMap<>();
        Map<String, BigDecimal> monthlyExpenses = new TreeMap<>();
        for (ForecastDTO.MonthForecast m : forecast.getMonths()) {
            if (m.getIncome().signum() != 0) monthlyIncomes.put(m.getMonth(), m.getIncome());
            if (m.getExpense().signum() != 0) monthlyExpenses.put(m.getMonth(), m.getExpense());
        }
```

Constructor: inject `ForecastService forecastService` (add field + constructor parameter; Spring autowires Vaadin view constructors). Remove now-unused injected services from the view if nothing else uses them (`scheduledService`, `exchangeRateService` — check remaining usages before removing; `accountService` may still be used elsewhere in the view). Add import `com.cuenti.app.api.dto.ForecastDTO`.

- [ ] **Step 9: Run all tests**

Run: `./mvnw test`
Expected: PASS — including existing forecast-related view tests.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/cuenti/app/service/ForecastService.java \
        src/main/java/com/cuenti/app/api/dto/ForecastDTO.java \
        src/main/java/com/cuenti/app/api/ForecastApiController.java \
        src/main/java/com/cuenti/app/views/ForecastsView.java \
        src/test/java/com/cuenti/app/service/ForecastServiceTest.java \
        src/test/java/com/cuenti/app/api/ForecastApiControllerTest.java
git commit -m "feat(api): add forecast endpoint backed by extracted ForecastService"
```

---

### Task 4: VehicleReportService extraction

Move fuel parsing + derived-value computation out of `VehiclesView` into a service. Pure refactor plus service-level report assembly; no REST endpoint yet (task 5).

**Files:**
- Create: `src/main/java/com/cuenti/app/service/VehicleReportService.java`
- Modify: `src/main/java/com/cuenti/app/views/VehiclesView.java` (delete `parseFuelEntry`, `extractValue`, `extractFullTank`, `computeDerivedValues`, the regex `Pattern` constants, and the `FuelEntry` inner class; delegate to the service)
- Modify: `src/test/java/com/cuenti/app/views/VehicleConsumptionCalcTest.java` (repoint to `VehicleReportService`; move to `src/test/java/com/cuenti/app/service/` package)
- Test: `src/test/java/com/cuenti/app/service/VehicleReportServiceTest.java`

**Interfaces:**
- Consumes: `TransactionService.getTransactionsByUser(User)`, `ExchangeRateService.convert(...)`, `CategoryService.findById(Long)`.
- Produces on `VehicleReportService`:
  - `public static class FuelEntry` — moved verbatim from `VehiclesView` (fields: `date`, `odometer`, `liters`, `amount`, `currency`, `station`, `memo`, `fullTank`, `distance`, `pricePerLiter`, `consumption`; keep the existing getters used by the view's grid, make fields/getters public as needed).
  - `public static FuelEntry parseFuelEntry(Transaction t, String defaultCurrency)` — verbatim move (regex patterns move with it: `ODOMETER_PATTERN = "d[=:]\\s*(\\d+(?:[.,]\\d+)?)"`, `LITERS_PATTERN = "[vl][~=:]\\s*(\\d+(?:[.,]\\d+)?)"`, `FULL_TANK_PATTERN = "\\b(full)\\b"` case-insensitive, plus the secondary-regex fallbacks).
  - `public static BigDecimal[] computeDerivedValues(List<FuelEntry> entries)` — verbatim move; returns `{attributedLiters, attributedDistance}`.
  - `public VehicleReport getReport(User user, Long categoryId, LocalDate start, LocalDate end)` where `VehicleReport` is a nested record-style holder: `entries` (date-descending), `totalCost` (converted to the user's default currency), `totalLiters`, `totalDistance`, `avgConsumption` (attributedLiters/attributedDistance×100, scale 2, null when distance is zero), `avgPricePerLiter` (totalCost/totalLiters, scale 3, null when liters zero), `currency`.

- [ ] **Step 1: Write the failing service test**

`src/test/java/com/cuenti/app/service/VehicleReportServiceTest.java` (plain JUnit for the static parts):

```java
package com.cuenti.app.service;

import com.cuenti.app.model.Transaction;
import com.cuenti.app.service.VehicleReportService.FuelEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleReportServiceTest {

    private Transaction fuelTx(String memo, String payee, String amount) {
        return Transaction.builder()
                .type(Transaction.TransactionType.EXPENSE)
                .amount(new BigDecimal(amount))
                .transactionDate(LocalDateTime.of(2026, 3, 1, 10, 0))
                .payee(payee)
                .memo(memo)
                .build();
    }

    @Test
    void parsesOdometerLitersAndFullTank() {
        FuelEntry entry = VehicleReportService.parseFuelEntry(
                fuelTx("d=45210 l=41.3 full", "Aral", "72.50"), "EUR");
        assertThat(entry.getOdometer()).isEqualByComparingTo("45210");
        assertThat(entry.getLiters()).isEqualByComparingTo("41.3");
        assertThat(entry.isFullTank()).isTrue();
        assertThat(entry.getStation()).isEqualTo("Aral");
    }

    @Test
    void parsesSecondaryKmNotation() {
        FuelEntry entry = VehicleReportService.parseFuelEntry(
                fuelTx("45210 km, 40 l", "Shell", "70.00"), "EUR");
        assertThat(entry.getOdometer()).isEqualByComparingTo("45210");
        assertThat(entry.getLiters()).isEqualByComparingTo("40");
    }

    @Test
    void consumptionComputedBetweenFullTanks() {
        FuelEntry first = VehicleReportService.parseFuelEntry(
                fuelTx("d=1000 l=40 full", "A", "60"), "EUR");
        first.setDate(LocalDate.of(2026, 1, 1));
        FuelEntry second = VehicleReportService.parseFuelEntry(
                fuelTx("d=1500 l=35 full", "A", "55"), "EUR");
        second.setDate(LocalDate.of(2026, 2, 1));

        BigDecimal[] attributed = VehicleReportService.computeDerivedValues(List.of(first, second));

        // 35 liters over 500 km => 7.00 l/100km
        assertThat(second.getConsumption()).isEqualByComparingTo("7.00");
        assertThat(attributed[0]).isEqualByComparingTo("35"); // liters
        assertThat(attributed[1]).isEqualByComparingTo("500"); // distance
    }

    @Test
    void fallbackTreatsEveryFillAsFullWhenNoneFlagged() {
        FuelEntry first = VehicleReportService.parseFuelEntry(fuelTx("d=1000 l=40", "A", "60"), "EUR");
        FuelEntry second = VehicleReportService.parseFuelEntry(fuelTx("d=1400 l=28", "A", "45"), "EUR");

        VehicleReportService.computeDerivedValues(List.of(first, second));
        assertThat(second.getConsumption()).isEqualByComparingTo("7.00");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=VehicleReportServiceTest`
Expected: COMPILE FAILURE — `VehicleReportService` not defined.

- [ ] **Step 3: Create the service by moving code from VehiclesView**

`src/main/java/com/cuenti/app/service/VehicleReportService.java`:

```java
package com.cuenti.app.service;

import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Fuel/vehicle cost report computed from expense transactions in a chosen
 * category. Memo syntax: "d=45210" (odometer km), "l=41.3" or "v=41.3"
 * (liters), the word "full" marks a full tank. Consumption is measured
 * full-tank to full-tank; when no entry is flagged, every fill counts.
 * Logic moved verbatim from VehiclesView so the REST API can reuse it.
 */
@Service
@RequiredArgsConstructor
public class VehicleReportService {

    private static final Pattern ODOMETER_PATTERN = Pattern.compile("d[=:]\\s*(\\d+(?:[.,]\\d+)?)");
    private static final Pattern LITERS_PATTERN = Pattern.compile("[vl][~=:]\\s*(\\d+(?:[.,]\\d+)?)");
    private static final Pattern FULL_TANK_PATTERN = Pattern.compile("\\b(full)\\b", Pattern.CASE_INSENSITIVE);

    private final TransactionService transactionService;
    private final ExchangeRateService exchangeRateService;

    @Getter
    @Setter
    public static class FuelEntry {
        private LocalDate date;
        private BigDecimal odometer;
        private BigDecimal liters;
        private BigDecimal amount;
        private String currency;
        private String station;
        private String memo;
        private boolean fullTank;
        private BigDecimal distance;
        private BigDecimal pricePerLiter;
        private BigDecimal consumption;

        public FuelEntry(LocalDate date, BigDecimal odometer, BigDecimal liters, BigDecimal amount,
                         String currency, String station, String memo) {
            this.date = date; this.odometer = odometer; this.liters = liters; this.amount = amount;
            this.currency = currency; this.station = station; this.memo = memo;
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class VehicleReport {
        private final List<FuelEntry> entries;       // date descending
        private final BigDecimal totalCost;          // in user's default currency
        private final BigDecimal totalLiters;
        private final BigDecimal totalDistance;
        private final BigDecimal avgConsumption;     // l/100km, null if unmeasurable
        private final BigDecimal avgPricePerLiter;   // null if no liters recorded
        private final String currency;
    }

    @Transactional(readOnly = true)
    public VehicleReport getReport(User user, Long categoryId, LocalDate start, LocalDate end) {
        List<Transaction> transactions = transactionService.getTransactionsByUser(user).stream()
                .filter(t -> t.getCategory() != null && t.getCategory().getId().equals(categoryId))
                .filter(t -> t.getType() == Transaction.TransactionType.EXPENSE)
                .filter(t -> {
                    LocalDate txDate = t.getTransactionDate().toLocalDate();
                    return !txDate.isBefore(start) && !txDate.isAfter(end);
                })
                .sorted(Comparator.comparing(Transaction::getTransactionDate))
                .collect(Collectors.toList());

        List<FuelEntry> entries = new ArrayList<>();
        for (Transaction t : transactions) {
            FuelEntry entry = parseFuelEntry(t, user.getDefaultCurrency());
            if (entry != null) entries.add(entry);
        }

        BigDecimal[] attributed = computeDerivedValues(entries);
        BigDecimal attributedLiters = attributed[0];
        BigDecimal attributedDistance = attributed[1];

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalLiters = BigDecimal.ZERO;
        for (FuelEntry e : entries) {
            totalCost = totalCost.add(exchangeRateService.convert(e.getAmount(), e.getCurrency(), user.getDefaultCurrency()));
            if (e.getLiters() != null) totalLiters = totalLiters.add(e.getLiters());
        }

        BigDecimal avgConsumption = attributedDistance.compareTo(BigDecimal.ZERO) > 0
                ? attributedLiters.divide(attributedDistance, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : null;
        BigDecimal avgPricePerLiter = totalLiters.compareTo(BigDecimal.ZERO) > 0
                ? totalCost.divide(totalLiters, 3, RoundingMode.HALF_UP)
                : null;

        List<FuelEntry> descending = entries.stream()
                .sorted(Comparator.comparing(FuelEntry::getDate).reversed())
                .collect(Collectors.toList());

        return new VehicleReport(descending, totalCost, totalLiters, attributedDistance,
                avgConsumption, avgPricePerLiter, user.getDefaultCurrency());
    }

    public static FuelEntry parseFuelEntry(Transaction t, String defaultCurrency) {
        BigDecimal odometer = extractValue(t.getMemo(), ODOMETER_PATTERN, "(\\d{4,})\\s*km");
        BigDecimal liters = extractValue(t.getMemo(), LITERS_PATTERN, "(\\d+(?:[.,]\\d+)?)\\s*[Ll](?:\\s|$|\\))");
        FuelEntry entry = new FuelEntry(
                t.getTransactionDate().toLocalDate(),
                odometer,
                liters,
                t.getAmount(),
                t.getFromAccount() != null ? t.getFromAccount().getCurrency() : defaultCurrency,
                t.getPayee(),
                t.getMemo()
        );
        entry.setFullTank(extractFullTank(t.getMemo()));
        return entry;
    }

    private static BigDecimal extractValue(String memo, Pattern primary, String secondaryRegex) {
        if (memo == null || memo.isEmpty()) return null;
        Matcher m = primary.matcher(memo);
        if (m.find()) return new BigDecimal(m.group(1).replace(",", "."));
        Matcher m2 = Pattern.compile(secondaryRegex).matcher(memo);
        if (m2.find()) return new BigDecimal(m2.group(1).replace(",", "."));
        return null;
    }

    private static boolean extractFullTank(String memo) {
        if (memo == null || memo.isEmpty()) return false;
        return FULL_TANK_PATTERN.matcher(memo).find();
    }

    /**
     * Computes distance/price/consumption per entry (full-tank to full-tank;
     * fill-to-fill fallback when no entry is flagged). Returns
     * {attributedLiters, attributedDistance} for the averages.
     */
    public static BigDecimal[] computeDerivedValues(List<FuelEntry> fuelEntries) {
        BigDecimal attributedLiters = BigDecimal.ZERO;
        BigDecimal attributedDistance = BigDecimal.ZERO;

        boolean anyFullTank = fuelEntries.stream().anyMatch(FuelEntry::isFullTank);

        FuelEntry previous = null;
        FuelEntry lastFull = null;
        BigDecimal litersSinceFull = BigDecimal.ZERO;

        for (FuelEntry entry : fuelEntries) {
            if (previous != null && entry.getOdometer() != null && previous.getOdometer() != null) {
                entry.setDistance(entry.getOdometer().subtract(previous.getOdometer()));
            }
            if (entry.getLiters() != null && entry.getLiters().compareTo(BigDecimal.ZERO) > 0 && entry.getAmount() != null) {
                entry.setPricePerLiter(entry.getAmount().divide(entry.getLiters(), 3, RoundingMode.HALF_UP));
            }

            if (entry.getLiters() != null) {
                litersSinceFull = litersSinceFull.add(entry.getLiters());
            }
            boolean measurePoint = anyFullTank ? entry.isFullTank() : true;
            if (measurePoint && entry.getOdometer() != null) {
                if (lastFull != null) {
                    BigDecimal dist = entry.getOdometer().subtract(lastFull.getOdometer());
                    if (dist.compareTo(BigDecimal.ZERO) > 0 && litersSinceFull.compareTo(BigDecimal.ZERO) > 0) {
                        entry.setConsumption(litersSinceFull
                                .divide(dist, 6, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP));
                        attributedLiters = attributedLiters.add(litersSinceFull);
                        attributedDistance = attributedDistance.add(dist);
                    }
                }
                lastFull = entry;
                litersSinceFull = BigDecimal.ZERO;
            }
            previous = entry;
        }
        return new BigDecimal[]{attributedLiters, attributedDistance};
    }
}
```

- [ ] **Step 4: Refactor VehiclesView to delegate**

In `VehiclesView`:
- Delete: `ODOMETER_PATTERN`, `LITERS_PATTERN`, `FULL_TANK_PATTERN` constants; `parseFuelEntry`, `extractValue`, `extractFullTank`, `computeDerivedValues` methods; the `FuelEntry` inner class.
- Add import: `com.cuenti.app.service.VehicleReportService` and `com.cuenti.app.service.VehicleReportService.FuelEntry`.
- Change the `fuelEntries` field type to `List<VehicleReportService.FuelEntry>`.
- In `loadVehicleData()`: replace the parse loop with `FuelEntry entry = VehicleReportService.parseFuelEntry(t, currentUser.getDefaultCurrency());` and `calculateDerivedValues()` body with `BigDecimal[] attributed = VehicleReportService.computeDerivedValues(fuelEntries); attributedLiters = attributed[0]; attributedDistance = attributed[1];`.
- Grid/summary/chart code: field accesses on `FuelEntry` (`entry.odometer` etc.) become getter calls (`entry.getOdometer()`) if they were direct field reads — do this mechanically wherever the compiler complains.

- [ ] **Step 5: Update the existing calculation test**

`git mv src/test/java/com/cuenti/app/views/VehicleConsumptionCalcTest.java src/test/java/com/cuenti/app/service/VehicleConsumptionCalcTest.java`, change its package to `com.cuenti.app.service`, and repoint every `VehiclesView.computeDerivedValues`/`VehiclesView.FuelEntry` reference to `VehicleReportService`. Constructor/field usage maps 1:1 (the class moved verbatim; only field access may need getters/setters).

- [ ] **Step 6: Run tests**

Run: `./mvnw test -Dtest='VehicleReportServiceTest,VehicleConsumptionCalcTest'` — Expected: PASS.
Run: `./mvnw test` — Expected: PASS (vehicle view tests included).

- [ ] **Step 7: Commit**

```bash
git add -A src/main/java/com/cuenti/app/service/VehicleReportService.java \
        src/main/java/com/cuenti/app/views/VehiclesView.java \
        src/test/java/com/cuenti/app/service/ \
        src/test/java/com/cuenti/app/views/
git commit -m "refactor: extract vehicle fuel report logic into VehicleReportService"
```

---

### Task 5: Vehicle API + defaultVehicleCategoryId preference

**Files:**
- Create: `src/main/java/com/cuenti/app/api/dto/VehicleReportDTO.java`
- Create: `src/main/java/com/cuenti/app/api/VehicleApiController.java`
- Modify: `src/main/java/com/cuenti/app/api/dto/UserProfileDTO.java` (add `defaultVehicleCategoryId`)
- Modify: `src/main/java/com/cuenti/app/api/dto/DtoMapper.java` (`toUserProfileDTO` maps the new field)
- Modify: `src/main/java/com/cuenti/app/api/UserApiController.java` (`updatePreferences` accepts `defaultVehicleCategoryId`)
- Test: `src/test/java/com/cuenti/app/api/VehicleApiControllerTest.java`

**Interfaces:**
- Consumes: `VehicleReportService.getReport(User, Long, LocalDate, LocalDate)` (task 4), `UserService.updateDefaultVehicleCategory(User, Long)` (exists), `User.getDefaultVehicleCategoryId()`.
- Produces: `GET /api/vehicles/report?categoryId=&start=&end=` — `categoryId` defaults to the user's `defaultVehicleCategoryId` (400 if neither present); `start`/`end` are ISO dates defaulting to Jan 1 / Dec 31 of the current year (mirrors the web view's "this_year" default). `PUT /api/user/preferences` accepts `defaultVehicleCategoryId` (number or null).

- [ ] **Step 1: Write the failing test**

`src/test/java/com/cuenti/app/api/VehicleApiControllerTest.java`:

```java
package com.cuenti.app.api;

import com.cuenti.app.model.Category;
import com.cuenti.app.service.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "demo")
class VehicleApiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CategoryService categoryService;

    private Long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        Category cat = new Category();
        cat.setName("Car-" + System.nanoTime());
        cat.setType(Category.CategoryType.EXPENSE);
        categoryId = categoryService.saveCategory(cat).getId();

        // an account to spend from
        String acct = mockMvc.perform(post("/api/accounts")
                        .contentType("application/json")
                        .content("{\"accountName\":\"Fuel Card\",\"accountType\":\"BANK\",\"currency\":\"EUR\",\"startBalance\":1000}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long accountId = objectMapper.readTree(acct).get("id").asLong();

        mockMvc.perform(post("/api/transactions")
                        .contentType("application/json")
                        .content("{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                                + ",\"amount\":60,\"transactionDate\":\"2026-02-01T10:00:00\""
                                + ",\"categoryId\":" + categoryId
                                + ",\"payee\":\"Aral\",\"memo\":\"d=1000 l=40 full\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/transactions")
                        .contentType("application/json")
                        .content("{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                                + ",\"amount\":55,\"transactionDate\":\"2026-03-01T10:00:00\""
                                + ",\"categoryId\":" + categoryId
                                + ",\"payee\":\"Aral\",\"memo\":\"d=1500 l=35 full\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void reportComputesConsumption() throws Exception {
        String body = mockMvc.perform(get("/api/vehicles/report")
                        .param("categoryId", categoryId.toString())
                        .param("start", "2026-01-01")
                        .param("end", "2026-12-31"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode report = objectMapper.readTree(body);
        org.assertj.core.api.Assertions.assertThat(report.get("entries").size()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(report.get("totalLiters").decimalValue()).isEqualByComparingTo("75");
        org.assertj.core.api.Assertions.assertThat(report.get("totalDistance").decimalValue()).isEqualByComparingTo("500");
        org.assertj.core.api.Assertions.assertThat(report.get("avgConsumption").decimalValue()).isEqualByComparingTo("7.00");
        // newest first
        org.assertj.core.api.Assertions.assertThat(report.get("entries").get(0).get("odometer").decimalValue()).isEqualByComparingTo("1500");
    }

    @Test
    void missingCategoryWithoutDefaultIs400() throws Exception {
        mockMvc.perform(get("/api/vehicles/report"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void defaultVehicleCategoryPreferenceUsed() throws Exception {
        mockMvc.perform(put("/api/user/preferences")
                        .contentType("application/json")
                        .content("{\"defaultVehicleCategoryId\":" + categoryId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultVehicleCategoryId").value(categoryId.intValue()));

        mockMvc.perform(get("/api/vehicles/report")
                        .param("start", "2026-01-01").param("end", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=VehicleApiControllerTest`
Expected: FAIL — 404 on `/api/vehicles/report`, missing `defaultVehicleCategoryId` in profile response.

- [ ] **Step 3: DTO + controller + preference plumbing**

`src/main/java/com/cuenti/app/api/dto/VehicleReportDTO.java`:

```java
package com.cuenti.app.api.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleReportDTO {
    private List<FuelEntryDTO> entries;
    private BigDecimal totalCost;
    private BigDecimal totalLiters;
    private BigDecimal totalDistance;
    private BigDecimal avgConsumption;
    private BigDecimal avgPricePerLiter;
    private String currency;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FuelEntryDTO {
        private LocalDate date;
        private BigDecimal odometer;
        private BigDecimal liters;
        private BigDecimal amount;
        private String currency;
        private String station;
        private String memo;
        private boolean fullTank;
        private BigDecimal distance;
        private BigDecimal pricePerLiter;
        private BigDecimal consumption;
    }
}
```

`src/main/java/com/cuenti/app/api/VehicleApiController.java`:

```java
package com.cuenti.app.api;

import com.cuenti.app.api.dto.VehicleReportDTO;
import com.cuenti.app.model.User;
import com.cuenti.app.service.SecurityUtil;
import com.cuenti.app.service.UserService;
import com.cuenti.app.service.VehicleReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleApiController {

    private final VehicleReportService vehicleReportService;
    private final UserService userService;

    @GetMapping("/report")
    public ResponseEntity<?> getReport(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();
        User user = userService.findByUsername(username);

        Long effectiveCategoryId = categoryId != null ? categoryId : user.getDefaultVehicleCategoryId();
        if (effectiveCategoryId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "categoryId required (no default vehicle category set)"));
        }

        LocalDate now = LocalDate.now();
        LocalDate effectiveStart = start != null ? start : now.withDayOfYear(1);
        LocalDate effectiveEnd = end != null ? end : now.with(TemporalAdjusters.lastDayOfYear());

        VehicleReportService.VehicleReport report =
                vehicleReportService.getReport(user, effectiveCategoryId, effectiveStart, effectiveEnd);
        return ResponseEntity.ok(toDTO(report));
    }

    private VehicleReportDTO toDTO(VehicleReportService.VehicleReport r) {
        return VehicleReportDTO.builder()
                .entries(r.getEntries().stream().map(e -> VehicleReportDTO.FuelEntryDTO.builder()
                        .date(e.getDate())
                        .odometer(e.getOdometer())
                        .liters(e.getLiters())
                        .amount(e.getAmount())
                        .currency(e.getCurrency())
                        .station(e.getStation())
                        .memo(e.getMemo())
                        .fullTank(e.isFullTank())
                        .distance(e.getDistance())
                        .pricePerLiter(e.getPricePerLiter())
                        .consumption(e.getConsumption())
                        .build()).collect(Collectors.toList()))
                .totalCost(r.getTotalCost())
                .totalLiters(r.getTotalLiters())
                .totalDistance(r.getTotalDistance())
                .avgConsumption(r.getAvgConsumption())
                .avgPricePerLiter(r.getAvgPricePerLiter())
                .currency(r.getCurrency())
                .build();
    }
}
```

`UserProfileDTO`: add field `private Long defaultVehicleCategoryId;`.
`DtoMapper.toUserProfileDTO`: add `.defaultVehicleCategoryId(u.getDefaultVehicleCategoryId())` to the builder chain.
`UserApiController.updatePreferences`: after the existing `apiEnabled` block, add:

```java
        if (preferences.containsKey("defaultVehicleCategoryId")) {
            Object raw = preferences.get("defaultVehicleCategoryId");
            Long catId = raw == null ? null : ((Number) raw).longValue();
            userService.updateDefaultVehicleCategory(user, catId);
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=VehicleApiControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cuenti/app/api/VehicleApiController.java \
        src/main/java/com/cuenti/app/api/dto/VehicleReportDTO.java \
        src/main/java/com/cuenti/app/api/dto/UserProfileDTO.java \
        src/main/java/com/cuenti/app/api/dto/DtoMapper.java \
        src/main/java/com/cuenti/app/api/UserApiController.java \
        src/test/java/com/cuenti/app/api/VehicleApiControllerTest.java
git commit -m "feat(api): add vehicle fuel report endpoint and default vehicle category preference"
```

---

### Task 6: Audit log API (admin)

**Files:**
- Create: `src/main/java/com/cuenti/app/api/dto/AuditLogDTO.java`
- Create: `src/main/java/com/cuenti/app/api/AuditLogApiController.java`
- Modify: `src/main/java/com/cuenti/app/api/dto/DtoMapper.java` (add `toAuditLogDTO`)
- Test: `src/test/java/com/cuenti/app/api/AuditLogApiControllerTest.java`

**Interfaces:**
- Consumes: `AuditService.latest(String filter, int page, int size): Page<AuditLog>`.
- Produces: `GET /api/audit-log?page=0&size=50&filter=` (admin only) returning `{content: [AuditLogDTO...], page, size, totalElements, totalPages}`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/cuenti/app/api/AuditLogApiControllerTest.java`:

```java
package com.cuenti.app.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuditLogApiControllerTest {

    @Autowired MockMvc mockMvc;

    @Test
    @WithMockUser(username = "demo", roles = {"ADMIN"})
    void adminGetsPagedAuditLog() throws Exception {
        mockMvc.perform(get("/api/audit-log").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    @WithMockUser(username = "demo", roles = {"USER"})
    void nonAdminGets403() throws Exception {
        mockMvc.perform(get("/api/audit-log"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=AuditLogApiControllerTest`
Expected: FAIL — 404 (no controller).

- [ ] **Step 3: DTO, mapper, controller**

`src/main/java/com/cuenti/app/api/dto/AuditLogDTO.java`:

```java
package com.cuenti.app.api.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDTO {
    private Long id;
    private Long userId;
    private String username;
    private LocalDateTime timestamp;
    private String entityType;
    private Long entityId;
    private String action;
    private String details;
}
```

Add to `DtoMapper.java` (import `com.cuenti.app.model.AuditLog`):

```java
    public static AuditLogDTO toAuditLogDTO(AuditLog a) {
        return AuditLogDTO.builder()
                .id(a.getId())
                .userId(a.getUserId())
                .username(a.getUsername())
                .timestamp(a.getTimestamp())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .action(a.getAction())
                .details(a.getDetails())
                .build();
    }
```

`src/main/java/com/cuenti/app/api/AuditLogApiController.java`:

```java
package com.cuenti.app.api;

import com.cuenti.app.api.dto.AuditLogDTO;
import com.cuenti.app.api.dto.DtoMapper;
import com.cuenti.app.model.AuditLog;
import com.cuenti.app.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/audit-log")
@RequiredArgsConstructor
public class AuditLogApiController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAuditLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String filter) {
        Page<AuditLog> result = auditService.latest(filter, page, Math.min(size, 200));
        return ResponseEntity.ok(Map.of(
                "content", result.getContent().stream().map(DtoMapper::toAuditLogDTO).collect(Collectors.toList()),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        ));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=AuditLogApiControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cuenti/app/api/AuditLogApiController.java \
        src/main/java/com/cuenti/app/api/dto/AuditLogDTO.java \
        src/main/java/com/cuenti/app/api/dto/DtoMapper.java \
        src/test/java/com/cuenti/app/api/AuditLogApiControllerTest.java
git commit -m "feat(api): add admin audit-log endpoint"
```

---

### Task 7: Saved views API

**Files:**
- Create: `src/main/java/com/cuenti/app/api/dto/SavedViewDTO.java`
- Create: `src/main/java/com/cuenti/app/api/SavedViewApiController.java`
- Modify: `src/main/java/com/cuenti/app/service/SavedViewService.java` (add `update` by id)
- Modify: `src/main/java/com/cuenti/app/api/dto/DtoMapper.java` (add `toSavedViewDTO`)
- Test: `src/test/java/com/cuenti/app/api/SavedViewApiControllerTest.java`

**Interfaces:**
- Consumes: `SavedViewService.getViews(User)`, `save(User, String name, String params)` (upsert by name), `delete(User, SavedView)`.
- Produces: `GET/POST /api/saved-views`, `PUT/DELETE /api/saved-views/{id}`. `SavedViewDTO{id, name, params, createdAt}` (`params` opaque string). New service method: `SavedViewService.update(User user, Long id, String name, String params): SavedView` — throws `IllegalArgumentException` when the id is missing or foreign.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/cuenti/app/api/SavedViewApiControllerTest.java`:

```java
package com.cuenti.app.api;

import com.cuenti.app.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "demo")
class SavedViewApiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService;

    private long createView(String name) throws Exception {
        String body = mockMvc.perform(post("/api/saved-views")
                        .contentType("application/json")
                        .content("{\"name\":\"" + name + "\",\"params\":\"type=EXPENSE&tag=food\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void createListUpdateDelete() throws Exception {
        long id = createView("Food expenses");

        mockMvc.perform(get("/api/saved-views"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].name").value("Food expenses"));

        mockMvc.perform(put("/api/saved-views/" + id)
                        .contentType("application/json")
                        .content("{\"name\":\"Food 2026\",\"params\":\"type=EXPENSE&tag=food&year=2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Food 2026"))
                .andExpect(jsonPath("$.params").value("type=EXPENSE&tag=food&year=2026"));

        mockMvc.perform(delete("/api/saved-views/" + id)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/saved-views/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void sameNameUpserts() throws Exception {
        long first = createView("Dupe");
        long second = createView("Dupe");
        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
    }

    @Test
    void foreignViewInvisible() throws Exception {
        long id = createView("Mine");
        userService.registerUser("intruder2", "intruder2@x.com", "password123", "In", "Truder");

        mockMvc.perform(put("/api/saved-views/" + id).with(user("intruder2"))
                        .contentType("application/json")
                        .content("{\"params\":\"x\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/saved-views/" + id).with(user("intruder2")))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=SavedViewApiControllerTest`
Expected: FAIL — 404 (no controller).

- [ ] **Step 3: Service method, DTO, mapper, controller**

Add to `SavedViewService`:

```java
    @Transactional
    public SavedView update(User user, Long id, String name, String params) {
        SavedView view = repository.findById(id)
                .filter(v -> v.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Saved view not found"));
        if (name != null && !name.isBlank()) view.setName(name);
        if (params != null) view.setParams(params);
        return repository.save(view);
    }
```

`src/main/java/com/cuenti/app/api/dto/SavedViewDTO.java`:

```java
package com.cuenti.app.api.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedViewDTO {
    private Long id;
    private String name;
    private String params;
    private LocalDateTime createdAt;
}
```

Add to `DtoMapper.java` (import `com.cuenti.app.model.SavedView`):

```java
    public static SavedViewDTO toSavedViewDTO(SavedView v) {
        return SavedViewDTO.builder()
                .id(v.getId())
                .name(v.getName())
                .params(v.getParams())
                .createdAt(v.getCreatedAt())
                .build();
    }
```

`src/main/java/com/cuenti/app/api/SavedViewApiController.java`:

```java
package com.cuenti.app.api;

import com.cuenti.app.api.dto.DtoMapper;
import com.cuenti.app.api.dto.SavedViewDTO;
import com.cuenti.app.model.SavedView;
import com.cuenti.app.model.User;
import com.cuenti.app.service.SavedViewService;
import com.cuenti.app.service.SecurityUtil;
import com.cuenti.app.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/saved-views")
@RequiredArgsConstructor
public class SavedViewApiController {

    private final SavedViewService savedViewService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<SavedViewDTO>> getViews() {
        User user = currentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(savedViewService.getViews(user).stream()
                .map(DtoMapper::toSavedViewDTO)
                .collect(Collectors.toList()));
    }

    @PostMapping
    public ResponseEntity<SavedViewDTO> createView(@RequestBody SavedViewDTO dto) {
        User user = currentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (dto.getName() == null || dto.getName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        SavedView saved = savedViewService.save(user, dto.getName(), dto.getParams());
        return ResponseEntity.ok(DtoMapper.toSavedViewDTO(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SavedViewDTO> updateView(@PathVariable Long id, @RequestBody SavedViewDTO dto) {
        User user = currentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            SavedView updated = savedViewService.update(user, id, dto.getName(), dto.getParams());
            return ResponseEntity.ok(DtoMapper.toSavedViewDTO(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteView(@PathVariable Long id) {
        User user = currentUser();
        if (user == null) return ResponseEntity.status(401).build();
        SavedView view = savedViewService.getViews(user).stream()
                .filter(v -> v.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (view == null) return ResponseEntity.notFound().build();
        savedViewService.delete(user, view);
        return ResponseEntity.ok().build();
    }

    private User currentUser() {
        return SecurityUtil.getAuthenticatedUsername()
                .map(userService::findByUsername)
                .orElse(null);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=SavedViewApiControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cuenti/app/api/SavedViewApiController.java \
        src/main/java/com/cuenti/app/api/dto/SavedViewDTO.java \
        src/main/java/com/cuenti/app/api/dto/DtoMapper.java \
        src/main/java/com/cuenti/app/service/SavedViewService.java \
        src/test/java/com/cuenti/app/api/SavedViewApiControllerTest.java
git commit -m "feat(api): add saved views endpoints"
```

---

### Task 8: Transaction splits over REST

**Files:**
- Create: `src/main/java/com/cuenti/app/api/dto/TransactionSplitDTO.java`
- Modify: `src/main/java/com/cuenti/app/api/dto/TransactionDTO.java` (add `splits`)
- Modify: `src/main/java/com/cuenti/app/api/dto/DtoMapper.java` (`toTransactionDTO` maps splits)
- Modify: `src/main/java/com/cuenti/app/api/TransactionApiController.java` (`mapFromDTO` builds splits; create/update validate sum)
- Test: `src/test/java/com/cuenti/app/api/TransactionSplitApiTest.java`

**Interfaces:**
- Consumes: `Transaction.addSplit(TransactionSplit)` (cascade ALL + orphanRemoval already configured on `Transaction.splits`), `TransactionSplit.builder()` (`category`, `amount`, `memo`), `CategoryService.findById(Long)`.
- Produces: `TransactionDTO.splits: List<TransactionSplitDTO>`; `TransactionSplitDTO{id, categoryId, categoryName, amount, memo}`. POST/PUT with non-empty `splits` requires split amounts to sum to the transaction `amount` (else 400 `{"error": ...}`). Omitted/null `splits` = unchanged behavior; PUT with non-null splits replaces the whole set.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/cuenti/app/api/TransactionSplitApiTest.java`:

```java
package com.cuenti.app.api;

import com.cuenti.app.model.Category;
import com.cuenti.app.service.CategoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "demo")
class TransactionSplitApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CategoryService categoryService;

    private long accountId;
    private Long groceriesId;
    private Long householdId;

    @BeforeEach
    void setUp() throws Exception {
        Category groceries = new Category();
        groceries.setName("Groceries-" + System.nanoTime());
        groceries.setType(Category.CategoryType.EXPENSE);
        groceriesId = categoryService.saveCategory(groceries).getId();

        Category household = new Category();
        household.setName("Household-" + System.nanoTime());
        household.setType(Category.CategoryType.EXPENSE);
        householdId = categoryService.saveCategory(household).getId();

        String acct = mockMvc.perform(post("/api/accounts")
                        .contentType("application/json")
                        .content("{\"accountName\":\"Split test\",\"accountType\":\"BANK\",\"currency\":\"EUR\",\"startBalance\":1000}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        accountId = objectMapper.readTree(acct).get("id").asLong();
    }

    private String splitTxJson(String total, String split1, String split2) {
        return "{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                + ",\"amount\":" + total
                + ",\"transactionDate\":\"2026-05-01T12:00:00\",\"payee\":\"Supermarket\""
                + ",\"splits\":["
                + "{\"categoryId\":" + groceriesId + ",\"amount\":" + split1 + ",\"memo\":\"food\"},"
                + "{\"categoryId\":" + householdId + ",\"amount\":" + split2 + ",\"memo\":\"cleaning\"}"
                + "]}";
    }

    @Test
    void createWithSplitsRoundTrips() throws Exception {
        String body = mockMvc.perform(post("/api/transactions")
                        .contentType("application/json")
                        .content(splitTxJson("50.00", "30.00", "20.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splits.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        JsonNode tx = objectMapper.readTree(body);
        assertThat(tx.get("splits").get(0).get("categoryName").asText()).isNotEmpty();

        // GET returns splits too
        mockMvc.perform(get("/api/transactions").param("accountId", String.valueOf(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].splits.length()").value(2));
    }

    @Test
    void mismatchedSplitSumIs400() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType("application/json")
                        .content(splitTxJson("50.00", "30.00", "10.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void updateReplacesSplits() throws Exception {
        String body = mockMvc.perform(post("/api/transactions")
                        .contentType("application/json")
                        .content(splitTxJson("50.00", "30.00", "20.00")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();

        String updated = "{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                + ",\"amount\":50.00,\"transactionDate\":\"2026-05-01T12:00:00\",\"payee\":\"Supermarket\""
                + ",\"splits\":[{\"categoryId\":" + groceriesId + ",\"amount\":50.00}]}";
        mockMvc.perform(put("/api/transactions/" + id)
                        .contentType("application/json")
                        .content(updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splits.length()").value(1))
                .andExpect(jsonPath("$.splits[0].amount").value(50.00));
    }

    @Test
    void noSplitsFieldStillWorks() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType("application/json")
                        .content("{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                                + ",\"amount\":10,\"transactionDate\":\"2026-05-01T12:00:00\",\"payee\":\"Kiosk\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splits").isArray())
                .andExpect(jsonPath("$.splits.length()").value(0));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=TransactionSplitApiTest`
Expected: FAIL — no `splits` in response JSON.

- [ ] **Step 3: DTO + mapper + controller changes**

`src/main/java/com/cuenti/app/api/dto/TransactionSplitDTO.java`:

```java
package com.cuenti.app.api.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionSplitDTO {
    private Long id;
    private Long categoryId;
    private String categoryName;
    private BigDecimal amount;
    private String memo;
}
```

`TransactionDTO`: add field (import `java.util.List`):

```java
    private List<TransactionSplitDTO> splits;
```

`DtoMapper.toTransactionDTO`: add to the builder chain (import `java.util.stream.Collectors`):

```java
                .splits(t.getSplits().stream()
                        .map(s -> TransactionSplitDTO.builder()
                                .id(s.getId())
                                .categoryId(s.getCategory() != null ? s.getCategory().getId() : null)
                                .categoryName(s.getCategory() != null ? s.getCategory().getFullName() : null)
                                .amount(s.getAmount())
                                .memo(s.getMemo())
                                .build())
                        .collect(Collectors.toList()))
```

`TransactionApiController`:

1. Add a validation + mapping helper (imports: `com.cuenti.app.api.dto.TransactionSplitDTO`, `java.math.BigDecimal`, `java.util.Map`):

```java
    /** @return error message, or null when valid */
    private String applySplits(Transaction transaction, TransactionDTO dto) {
        if (dto.getSplits() == null || dto.getSplits().isEmpty()) return null;

        BigDecimal sum = dto.getSplits().stream()
                .map(TransactionSplitDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(dto.getAmount()) != 0) {
            return "Split amounts must sum to the transaction amount";
        }

        for (TransactionSplitDTO s : dto.getSplits()) {
            TransactionSplit split = TransactionSplit.builder()
                    .amount(s.getAmount())
                    .memo(s.getMemo())
                    .build();
            if (s.getCategoryId() != null) {
                categoryService.findById(s.getCategoryId()).ifPresent(split::setCategory);
            }
            transaction.addSplit(split);
        }
        return null;
    }
```

2. In `createTransaction` and `updateTransaction`, after `Transaction transaction = mapFromDTO(dto);` (and before `saveTransaction`), add:

```java
        String splitError = applySplits(transaction, dto);
        if (splitError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", splitError));
        }
```

Change both method signatures from `ResponseEntity<TransactionDTO>` to `ResponseEntity<?>` so the error body compiles.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=TransactionSplitApiTest`
Expected: PASS. If `updateReplacesSplits` fails with stale splits, the merge is not orphan-removing the old rows — in that case load the existing entity in `updateTransaction` (it already loads user transactions for the delete path; reuse that lookup), call `existing.getSplits().clear()`, copy the scalar fields onto `existing`, apply splits to `existing`, and save `existing` instead of the detached instance.

- [ ] **Step 5: Run the whole suite**

Run: `./mvnw test`
Expected: PASS — no regression in web transaction tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/cuenti/app/api/dto/TransactionSplitDTO.java \
        src/main/java/com/cuenti/app/api/dto/TransactionDTO.java \
        src/main/java/com/cuenti/app/api/dto/DtoMapper.java \
        src/main/java/com/cuenti/app/api/TransactionApiController.java \
        src/test/java/com/cuenti/app/api/TransactionSplitApiTest.java
git commit -m "feat(api): expose transaction splits with sum validation"
```

---

### Task 9: Transaction pagination, filtering, sorting

**Files:**
- Create: `src/main/java/com/cuenti/app/api/dto/PagedResponse.java`
- Modify: `src/main/java/com/cuenti/app/repository/TransactionRepository.java` (add paged search query)
- Modify: `src/main/java/com/cuenti/app/service/TransactionService.java` (add `search`)
- Modify: `src/main/java/com/cuenti/app/api/TransactionApiController.java` (new params on GET)
- Test: `src/test/java/com/cuenti/app/api/TransactionSearchApiTest.java`

**Interfaces:**
- Produces:
  - `TransactionRepository.searchByUser(User user, Long accountId, Transaction.TransactionType type, Long categoryId, LocalDateTime from, LocalDateTime to, String payee, String tag, String search, Pageable pageable): Page<Transaction>`
  - `TransactionService.search(User user, Long accountId, Transaction.TransactionType type, Long categoryId, LocalDateTime from, LocalDateTime to, String payee, String tag, String search, Pageable pageable): Page<Transaction>`
  - `GET /api/transactions` params: `accountId` (existing), `type`, `categoryId`, `start`, `end` (ISO date), `payee`, `tag`, `search`, `sort` (`field,asc|desc`; whitelist: `transactionDate`, `amount`, `payee`; default `transactionDate,desc`), `page`, `size` (max 200).
  - Response: plain `TransactionDTO[]` when `page` AND `size` are both absent (back-compat, even with filters); `PagedResponse<TransactionDTO>{content, page, size, totalElements, totalPages}` when either is present.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/cuenti/app/api/TransactionSearchApiTest.java`:

```java
package com.cuenti.app.api;

import com.cuenti.app.model.Category;
import com.cuenti.app.service.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "demo")
class TransactionSearchApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CategoryService categoryService;

    private long accountId;
    private Long foodCategoryId;

    @BeforeEach
    void setUp() throws Exception {
        Category food = new Category();
        food.setName("Food-" + System.nanoTime());
        food.setType(Category.CategoryType.EXPENSE);
        foodCategoryId = categoryService.saveCategory(food).getId();

        String acct = mockMvc.perform(post("/api/accounts")
                        .contentType("application/json")
                        .content("{\"accountName\":\"Search test\",\"accountType\":\"BANK\",\"currency\":\"EUR\",\"startBalance\":1000}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        accountId = objectMapper.readTree(acct).get("id").asLong();

        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/transactions")
                            .contentType("application/json")
                            .content("{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                                    + ",\"amount\":" + (i * 10)
                                    + ",\"transactionDate\":\"2026-0" + i + "-15T12:00:00\""
                                    + ",\"categoryId\":" + foodCategoryId
                                    + ",\"payee\":\"Rewe " + i + "\",\"memo\":\"weekly shop\",\"tags\":\"food\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void noParamsReturnsPlainArray() throws Exception {
        mockMvc.perform(get("/api/transactions").param("accountId", String.valueOf(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void paginationWrapsResponse() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("accountId", String.valueOf(accountId))
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                // default sort: newest first
                .andExpect(jsonPath("$.content[0].payee").value("Rewe 5"));
    }

    @Test
    void dateRangeFilterWorks() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("accountId", String.valueOf(accountId))
                        .param("start", "2026-02-01").param("end", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)); // Feb + Mar
    }

    @Test
    void payeeAndSearchFiltersWork() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("accountId", String.valueOf(accountId))
                        .param("payee", "rewe 3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/transactions")
                        .param("accountId", String.valueOf(accountId))
                        .param("search", "weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void categoryTypeAndTagFiltersWork() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("categoryId", foodCategoryId.toString())
                        .param("type", "EXPENSE")
                        .param("tag", "food"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void sortByAmountAscending() throws Exception {
        // smallest amount (10) belongs to "Rewe 1"
        mockMvc.perform(get("/api/transactions")
                        .param("accountId", String.valueOf(accountId))
                        .param("sort", "amount,asc")
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].payee").value("Rewe 1"));
    }

    @Test
    void invalidSortFieldIs400() throws Exception {
        mockMvc.perform(get("/api/transactions").param("sort", "payee;DROP TABLE,asc")
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=TransactionSearchApiTest`
Expected: FAIL — pagination/filter params ignored, `$.content` missing.

- [ ] **Step 3: Repository query**

Add to `TransactionRepository` (imports: `org.springframework.data.domain.Page`, `org.springframework.data.domain.Pageable`):

```java
    /**
     * Paged, filtered search for the REST API. LEFT JOINs keep transactions
     * with null category/accounts visible; countQuery avoids the fetch-join
     * count problem. Sorting comes from the Pageable.
     */
    @Query(value = "SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN t.fromAccount fa " +
           "LEFT JOIN t.toAccount ta " +
           "LEFT JOIN t.category c " +
           "WHERE (fa.user = :user OR ta.user = :user) " +
           "AND (:accountId IS NULL OR fa.id = :accountId OR ta.id = :accountId) " +
           "AND (:type IS NULL OR t.type = :type) " +
           "AND (:categoryId IS NULL OR c.id = :categoryId) " +
           "AND (CAST(:from AS timestamp) IS NULL OR t.transactionDate >= :from) " +
           "AND (CAST(:to AS timestamp) IS NULL OR t.transactionDate <= :to) " +
           "AND (:payee IS NULL OR LOWER(t.payee) LIKE LOWER(CONCAT('%', CAST(:payee AS string), '%'))) " +
           "AND (:tag IS NULL OR LOWER(t.tags) LIKE LOWER(CONCAT('%', CAST(:tag AS string), '%'))) " +
           "AND (:search IS NULL " +
           "     OR LOWER(t.payee) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(t.memo) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(t.number) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))",
           countQuery = "SELECT COUNT(DISTINCT t) FROM Transaction t " +
           "LEFT JOIN t.fromAccount fa " +
           "LEFT JOIN t.toAccount ta " +
           "LEFT JOIN t.category c " +
           "WHERE (fa.user = :user OR ta.user = :user) " +
           "AND (:accountId IS NULL OR fa.id = :accountId OR ta.id = :accountId) " +
           "AND (:type IS NULL OR t.type = :type) " +
           "AND (:categoryId IS NULL OR c.id = :categoryId) " +
           "AND (CAST(:from AS timestamp) IS NULL OR t.transactionDate >= :from) " +
           "AND (CAST(:to AS timestamp) IS NULL OR t.transactionDate <= :to) " +
           "AND (:payee IS NULL OR LOWER(t.payee) LIKE LOWER(CONCAT('%', CAST(:payee AS string), '%'))) " +
           "AND (:tag IS NULL OR LOWER(t.tags) LIKE LOWER(CONCAT('%', CAST(:tag AS string), '%'))) " +
           "AND (:search IS NULL " +
           "     OR LOWER(t.payee) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(t.memo) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(t.number) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<Transaction> searchByUser(@Param("user") User user,
                                   @Param("accountId") Long accountId,
                                   @Param("type") Transaction.TransactionType type,
                                   @Param("categoryId") Long categoryId,
                                   @Param("from") java.time.LocalDateTime from,
                                   @Param("to") java.time.LocalDateTime to,
                                   @Param("payee") String payee,
                                   @Param("tag") String tag,
                                   @Param("search") String search,
                                   Pageable pageable);
```

Note: if H2/Hibernate rejects the `CAST(:param AS string)` forms, simplify to plain `:payee` inside CONCAT — verify against the test suite before adjusting further. Splits are `FetchType.EAGER` on the entity, and from/to accounts + category are re-read through `DtoMapper` within the transaction, so no fetch joins are needed here.

- [ ] **Step 4: Service method**

Add to `TransactionService` (imports: `org.springframework.data.domain.Page`, `org.springframework.data.domain.Pageable`):

```java
    @Transactional(readOnly = true)
    public Page<Transaction> search(User user, Long accountId, Transaction.TransactionType type,
                                    Long categoryId, java.time.LocalDateTime from, java.time.LocalDateTime to,
                                    String payee, String tag, String search, Pageable pageable) {
        return transactionRepository.searchByUser(user, accountId, type, categoryId,
                from, to, payee, tag, search, pageable);
    }
```

(Match the class's existing transactional annotation style — if other read methods carry no `@Transactional`, drop it here too.)

- [ ] **Step 5: PagedResponse DTO and controller GET rewrite**

`src/main/java/com/cuenti/app/api/dto/PagedResponse.java`:

```java
package com.cuenti.app.api.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagedResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
```

Replace `TransactionApiController.getTransactions` with (imports: `com.cuenti.app.api.dto.PagedResponse`, `org.springframework.data.domain.*`, `org.springframework.format.annotation.DateTimeFormat`, `java.time.LocalDate`, `java.util.Set`):

```java
    private static final Set<String> SORT_WHITELIST = Set.of("transactionDate", "amount", "payee");

    @GetMapping
    public ResponseEntity<?> getTransactions(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Transaction.TransactionType type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) String payee,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();
        User user = userService.findByUsername(username);

        String sortField = "transactionDate";
        Sort.Direction sortDirection = Sort.Direction.DESC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            if (!SORT_WHITELIST.contains(parts[0])) {
                return ResponseEntity.badRequest().body(java.util.Map.of(
                        "error", "sort field must be one of " + SORT_WHITELIST));
            }
            sortField = parts[0];
            if (parts.length > 1 && "asc".equalsIgnoreCase(parts[1])) sortDirection = Sort.Direction.ASC;
        }
        Sort sortSpec = Sort.by(sortDirection, sortField).and(Sort.by(Sort.Direction.DESC, "sortOrder"));

        boolean paged = page != null || size != null;
        int effectivePage = page != null ? Math.max(page, 0) : 0;
        int effectiveSize = size != null ? Math.min(Math.max(size, 1), 200) : 50;
        Pageable pageable = paged
                ? PageRequest.of(effectivePage, effectiveSize, sortSpec)
                : Pageable.unpaged(sortSpec);

        Page<Transaction> result = transactionService.search(user, accountId, type, categoryId,
                start != null ? start.atStartOfDay() : null,
                end != null ? end.atTime(23, 59, 59) : null,
                emptyToNull(payee), emptyToNull(tag), emptyToNull(search), pageable);

        List<TransactionDTO> dtos = result.getContent().stream()
                .map(DtoMapper::toTransactionDTO)
                .collect(Collectors.toList());

        if (!paged) return ResponseEntity.ok(dtos); // back-compat: plain array

        return ResponseEntity.ok(PagedResponse.<TransactionDTO>builder()
                .content(dtos)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build());
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
```

The old `accountId`-only branches (`getTransactionsByAccount`/`getTransactionsByUser`) are removed — the search query covers both, and the DTO output is identical.

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw test -Dtest=TransactionSearchApiTest`
Expected: PASS. Also run `./mvnw test -Dtest='TransactionSplitApiTest,VehicleApiControllerTest'` — they POST/GET through the same controller and must stay green.

- [ ] **Step 7: Run the whole suite**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/cuenti/app/api/dto/PagedResponse.java \
        src/main/java/com/cuenti/app/repository/TransactionRepository.java \
        src/main/java/com/cuenti/app/service/TransactionService.java \
        src/main/java/com/cuenti/app/api/TransactionApiController.java \
        src/test/java/com/cuenti/app/api/TransactionSearchApiTest.java
git commit -m "feat(api): transaction pagination, filtering and sorting with back-compat plain array"
```

---

### Task 10: Final verification

- [ ] **Step 1: Full suite**

Run: `./mvnw test`
Expected: PASS, zero failures.

- [ ] **Step 2: Back-compat smoke check**

Confirm with focused greps that nothing broke the v1.3.1 contract:

Run: `grep -n "public ResponseEntity<?> getTransactions" src/main/java/com/cuenti/app/api/TransactionApiController.java`
Expected: single GET handler; plain-array path exists (`if (!paged) return ResponseEntity.ok(dtos)`).

Run: `./mvnw spring-boot:run` briefly (needs local PostgreSQL) OR rely on `TransactionSearchApiTest.noParamsReturnsPlainArray` — the test already pins the contract.

- [ ] **Step 3: Commit any stragglers and report**

```bash
git status
```

Expected: clean tree. Report completion; Phase 4 (mobile screens) consumes these endpoints.
