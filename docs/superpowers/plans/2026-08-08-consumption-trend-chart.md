# Consumption Trend Chart (Verbrauchstrend) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the div-bar Verbrauchstrend chart on the vehicles page with a reusable server-rendered SVG line chart (time-proportional x-axis, padded y-range, dashed average line, tooltips).

**Architecture:** New `ConsumptionTrendChart` component in `views/components/charts/`, following the existing `CashFlowChart` pattern: build one SVG string server-side, wrap in `Html`, style via theme CSS tokens. `VehiclesView.renderConsumptionChart` feeds it points and the attributed average. SVG-building logic is a static package-visible method so it unit-tests without Spring.

**Tech Stack:** Java 21 / Spring Boot / Vaadin Flow, Maven (`./mvnw`), JUnit 5 + AssertJ, Vaadin Browserless tests (`SpringBrowserlessTest`).

**Spec:** `docs/superpowers/specs/2026-08-08-consumption-trend-chart-design.md`

## Global Constraints

- Colors only via theme tokens: new `--cuenti-chart-consumption` plus existing `--cuenti-chart-grid` / `--cuenti-chart-axis-text` (defined in `src/main/frontend/themes/cuenti/styles.css`, `light-dark()` pairs).
- No per-point threshold coloring, no moving average, no trend badge (spec out-of-scope).
- No new translation keys; section title `vehicles.consumption_trend` and empty notice `vehicles.no_consumption_data` stay.
- Average shown in the chart = attributed average (same value as the "Ø Verbrauch" summary card), never the mean of plotted points.
- Commit messages end with the Claude trailer used in this repo.

---

### Task 1: `ConsumptionTrendChart` component + theme token

**Files:**
- Create: `src/main/java/com/cuenti/app/views/components/charts/ConsumptionTrendChart.java`
- Modify: `src/main/frontend/themes/cuenti/styles.css` (after line 65, inside the same token block as `--cuenti-chart-axis-text`)
- Test: `src/test/java/com/cuenti/app/views/components/charts/ConsumptionTrendChartTest.java`

**Interfaces:**
- Consumes: `CashFlowChart.escape(String)` (package-private static, same package).
- Produces (Task 2 relies on these exact signatures):
  - `public record ConsumptionTrendChart.Point(LocalDate date, BigDecimal consumption, String tooltip)`
  - `public ConsumptionTrendChart(List<Point> points, BigDecimal average, Locale locale)`
  - `public String getSvg()`
  - `static String buildSvg(List<Point> points, BigDecimal average, Locale locale)`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/cuenti/app/views/components/charts/ConsumptionTrendChartTest.java`:

```java
package com.cuenti.app.views.components.charts;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ConsumptionTrendChartTest {

    private static ConsumptionTrendChart.Point pt(String date, String value) {
        return new ConsumptionTrendChart.Point(LocalDate.parse(date), new BigDecimal(value), "tip");
    }

    private static int count(String s, String needle) {
        return s.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void multiplePoints_renderPolylineCirclesAndAverageLine() {
        String svg = ConsumptionTrendChart.buildSvg(
                List.of(pt("2026-01-10", "7.50"), pt("2026-02-10", "8.00"), pt("2026-03-10", "6.80")),
                new BigDecimal("7.45"), Locale.GERMAN);

        assertThat(svg).contains("<polyline");
        assertThat(count(svg, "<circle")).isEqualTo(3);
        assertThat(svg).contains("Ø 7.45");
        assertThat(svg).contains("stroke-dasharray");
        assertThat(svg).contains("var(--cuenti-chart-consumption)");
        assertThat(svg).contains("var(--cuenti-chart-grid)");
    }

    @Test
    void singlePoint_rendersDotAndAverageButNoPolyline() {
        String svg = ConsumptionTrendChart.buildSvg(
                List.of(pt("2026-01-10", "7.50")), new BigDecimal("7.50"), Locale.GERMAN);

        assertThat(svg).doesNotContain("<polyline");
        assertThat(count(svg, "<circle")).isEqualTo(1);
        assertThat(svg).contains("Ø 7.50");
    }

    @Test
    void identicalValues_produceFiniteCoordinates() {
        String svg = ConsumptionTrendChart.buildSvg(
                List.of(pt("2026-01-10", "7.00"), pt("2026-02-10", "7.00")),
                new BigDecimal("7.00"), Locale.GERMAN);

        assertThat(svg).doesNotContain("NaN");
        assertThat(svg).doesNotContain("Infinity");
        assertThat(count(svg, "<circle")).isEqualTo(2);
    }

    @Test
    void nullAverage_omitsAverageLine() {
        String svg = ConsumptionTrendChart.buildSvg(
                List.of(pt("2026-01-10", "7.50"), pt("2026-02-10", "8.00")), null, Locale.GERMAN);

        assertThat(svg).doesNotContain("Ø");
        assertThat(svg).doesNotContain("stroke-dasharray");
    }

    @Test
    void tooltipsAreEscaped() {
        String svg = ConsumptionTrendChart.buildSvg(
                List.of(new ConsumptionTrendChart.Point(
                        LocalDate.parse("2026-01-10"), new BigDecimal("7.50"), "<b>&'x")),
                null, Locale.GERMAN);

        assertThat(svg).contains("&lt;b&gt;&amp;&#39;x");
        assertThat(svg).doesNotContain("<b>");
    }

    @Test
    void componentExposesSvgAndWrapsIt() {
        ConsumptionTrendChart chart = new ConsumptionTrendChart(
                List.of(pt("2026-01-10", "7.50")), new BigDecimal("7.50"), Locale.GERMAN);

        assertThat(chart.getSvg()).startsWith("<svg");
        assertThat(chart.getChildren().count()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=ConsumptionTrendChartTest -q`
Expected: COMPILATION ERROR — `ConsumptionTrendChart` does not exist.

- [ ] **Step 3: Write the implementation**

`src/main/java/com/cuenti/app/views/components/charts/ConsumptionTrendChart.java`:

```java
package com.cuenti.app.views.components.charts;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.html.Div;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Server-rendered SVG line chart: fuel consumption (L/100km) per fill-up over
 * time. Time-proportional x-axis, padded y-range so variation stays visible,
 * dashed reference line at the period average. Colors come from the validated
 * chart tokens in the theme (light/dark via CSS light-dark()); native SVG
 * titles provide per-point tooltips.
 */
public class ConsumptionTrendChart extends Div {

    /** One fill-up with a computed consumption value; tooltip is prebuilt by the caller. */
    public record Point(LocalDate date, BigDecimal consumption, String tooltip) {}

    private static final int WIDTH = 720;
    private static final int HEIGHT = 160;
    private static final int TOP_PAD = 10;
    private static final int BOTTOM_PAD = 22;
    private static final int LEFT_PAD = 34;
    private static final int RIGHT_PAD = 48;   // room for the Ø label
    private static final int MAX_DATE_LABELS = 6;

    private final String svg;

    public ConsumptionTrendChart(List<Point> points, BigDecimal average, Locale locale) {
        getStyle().set("width", "100%");
        this.svg = buildSvg(points, average, locale);
        add(new Html("<div>" + svg + "</div>"));
    }

    /** The raw SVG markup; exposed for tests. */
    public String getSvg() {
        return svg;
    }

    static String buildSvg(List<Point> points, BigDecimal average, Locale locale) {
        double left = LEFT_PAD;
        double right = WIDTH - RIGHT_PAD;
        double top = TOP_PAD;
        double bottom = HEIGHT - BOTTOM_PAD;

        double min = points.stream().mapToDouble(p -> p.consumption().doubleValue()).min().orElse(0);
        double max = points.stream().mapToDouble(p -> p.consumption().doubleValue()).max().orElse(0);
        if (average != null) {
            min = Math.min(min, average.doubleValue());
            max = Math.max(max, average.doubleValue());
        }
        double span = max - min;
        double pad = span == 0 ? Math.max(1, Math.abs(max) * 0.1) : span * 0.1;
        double yMin = min - pad;
        double yMax = max + pad;

        long minDay = points.stream().mapToLong(p -> p.date().toEpochDay()).min().orElse(0);
        long maxDay = points.stream().mapToLong(p -> p.date().toEpochDay()).max().orElse(0);
        long daySpan = maxDay - minDay;

        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 ").append(WIDTH).append(' ').append(HEIGHT)
           .append("' role='img' style='display:block;width:100%;height:auto'>");

        // horizontal hairline grid with y tick labels
        for (int i = 0; i <= 3; i++) {
            double value = yMin + (yMax - yMin) * i / 3.0;
            double y = y(value, yMin, yMax, top, bottom);
            svg.append("<line x1='").append(LEFT_PAD).append("' x2='").append(fmt(right))
               .append("' y1='").append(fmt(y)).append("' y2='").append(fmt(y))
               .append("' stroke='var(--cuenti-chart-grid)' stroke-width='1'/>")
               .append("<text x='").append(LEFT_PAD - 6).append("' y='").append(fmt(y + 3))
               .append("' text-anchor='end' font-size='9' fill='var(--cuenti-chart-axis-text)'>")
               .append(String.format(Locale.ROOT, "%.1f", value)).append("</text>");
        }

        // sparse date labels
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern(daySpan <= 120 ? "dd.MM" : "MMM", locale);
        int step = Math.max(1, (int) Math.ceil(points.size() / (double) MAX_DATE_LABELS));
        for (int i = 0; i < points.size(); i += step) {
            Point p = points.get(i);
            svg.append("<text x='").append(fmt(x(p, minDay, daySpan, left, right)))
               .append("' y='").append(HEIGHT - 8)
               .append("' text-anchor='middle' font-size='9' fill='var(--cuenti-chart-axis-text)'>")
               .append(CashFlowChart.escape(p.date().format(dateFmt))).append("</text>");
        }

        // average reference line
        if (average != null) {
            double y = y(average.doubleValue(), yMin, yMax, top, bottom);
            svg.append("<line x1='").append(LEFT_PAD).append("' x2='").append(fmt(right))
               .append("' y1='").append(fmt(y)).append("' y2='").append(fmt(y))
               .append("' stroke='var(--cuenti-chart-consumption)' stroke-width='1' stroke-dasharray='4 3' opacity='0.8'/>")
               .append("<text x='").append(fmt(right + 4)).append("' y='").append(fmt(y + 3))
               .append("' text-anchor='start' font-size='10' font-weight='700' fill='var(--cuenti-chart-consumption)'>")
               .append("Ø ").append(average.toPlainString()).append("</text>");
        }

        // series line
        if (points.size() >= 2) {
            svg.append("<polyline fill='none' stroke='var(--cuenti-chart-consumption)' stroke-width='2' ")
               .append("stroke-linejoin='round' stroke-linecap='round' points='");
            for (Point p : points) {
                svg.append(fmt(x(p, minDay, daySpan, left, right))).append(',')
                   .append(fmt(y(p.consumption().doubleValue(), yMin, yMax, top, bottom))).append(' ');
            }
            svg.append("'/>");
        }

        // data points with tooltips
        for (Point p : points) {
            svg.append("<circle cx='").append(fmt(x(p, minDay, daySpan, left, right)))
               .append("' cy='").append(fmt(y(p.consumption().doubleValue(), yMin, yMax, top, bottom)))
               .append("' r='3.5' fill='var(--cuenti-chart-consumption)'>")
               .append("<title>").append(CashFlowChart.escape(p.tooltip())).append("</title></circle>");
        }

        svg.append("</svg>");
        return svg.toString();
    }

    private static double x(Point p, long minDay, long daySpan, double left, double right) {
        if (daySpan == 0) return (left + right) / 2;
        return left + (p.date().toEpochDay() - minDay) / (double) daySpan * (right - left);
    }

    private static double y(double value, double yMin, double yMax, double top, double bottom) {
        return bottom - (value - yMin) / (yMax - yMin) * (bottom - top);
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.1f", d);
    }
}
```

Then add the token in `src/main/frontend/themes/cuenti/styles.css`, directly after the `--cuenti-chart-axis-text` line (line 65):

```css
  --cuenti-chart-consumption: light-dark(#2a78d6, #3987e5);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=ConsumptionTrendChartTest -q`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cuenti/app/views/components/charts/ConsumptionTrendChart.java \
        src/test/java/com/cuenti/app/views/components/charts/ConsumptionTrendChartTest.java \
        src/main/frontend/themes/cuenti/styles.css
git commit -m "feat(vehicles): SVG consumption trend line chart component

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Sbj91k9xKknsSx9w8w9tzF"
```

---

### Task 2: Wire chart into `VehiclesView` + Browserless test

**Files:**
- Modify: `src/main/java/com/cuenti/app/views/VehiclesView.java` (methods `renderSummary` and `renderConsumptionChart`, currently around lines 505–580)
- Test: `src/test/java/com/cuenti/app/views/UC109VehicleConsumptionChartTest.java`

**Interfaces:**
- Consumes (from Task 1): `ConsumptionTrendChart.Point(LocalDate, BigDecimal, String)`, `new ConsumptionTrendChart(List<Point>, BigDecimal average, Locale)`, `chart.getSvg()`.
- Produces: nothing new — view-internal change.

**Domain notes for the test (how fuel entries work):**
- A fuel entry is an EXPENSE transaction in the selected category whose memo encodes liters (`v=40`) and odometer (`d=100500`) — parsed by `VehicleReportService.parseFuelEntry`.
- With no entry flagged full-tank, every entry with an odometer is a measure point (fill-to-fill fallback in `VehicleReportService.computeDerivedValues`). The first entry gets no consumption (nothing to attribute); each later entry gets `liters / distance * 100`.
- Test data below: 3 fill-ups same day (times 08/12/18 keep ordering deterministic; same-day always inside the view's default "this_year" range): 40 L over 500 km → 8.00, then 42 L over 600 km → 7.00. Attributed average = 82/1100*100 = **7.45** → chart must show `Ø 7.45` and exactly 2 circles.
- Test creates its own category (name `ZZ Fuel Test`) so demo seed data can never add extra fuel entries to the plotted set.

- [ ] **Step 1: Write the failing Browserless test**

`src/test/java/com/cuenti/app/views/UC109VehicleConsumptionChartTest.java`:

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
import com.cuenti.app.views.components.charts.ConsumptionTrendChart;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.combobox.ComboBox;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verbrauchstrend: SVG line chart with per-fill-up points and average line.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "demo")
class UC109VehicleConsumptionChartTest extends SpringBrowserlessTest {

    @Autowired TransactionService transactionService;
    @Autowired CategoryService categoryService;
    @Autowired AccountService accountService;
    @Autowired UserService userService;

    @Test
    @UseCase(id = "UC-109", scenario = "Consumption trend line chart with average")
    void fuelEntries_renderTrendChartWithAverage() {
        User demo = userService.findByUsername("demo");
        Account account = accountService.getAccountsByUser(demo).get(0);

        Category fuel = new Category();
        fuel.setName("ZZ Fuel Test");
        fuel.setType(Category.CategoryType.EXPENSE);
        fuel = categoryService.saveCategory(fuel);

        LocalDate today = LocalDate.now();
        createFuelTx(fuel, account, today.atTime(8, 0),  "60.00", "v=40 d=100000");
        createFuelTx(fuel, account, today.atTime(12, 0), "60.00", "v=40 d=100500");
        createFuelTx(fuel, account, today.atTime(18, 0), "63.00", "v=42 d=101100");

        navigate(VehiclesView.class);
        ComboBox<Category> categorySelect = $(ComboBox.class).single();
        test(categorySelect).selectItem(fuel.getFullName());

        ConsumptionTrendChart chart = $(ConsumptionTrendChart.class).single();
        String svg = chart.getSvg();
        // first fill-up carries no consumption -> 2 plotted points
        assertThat(svg.split("<circle", -1).length - 1).isEqualTo(2);
        assertThat(svg).contains("<polyline");
        assertThat(svg).contains("Ø 7.45");
    }

    private void createFuelTx(Category cat, Account from, LocalDateTime date, String amount, String memo) {
        Transaction t = new Transaction();
        t.setType(Transaction.TransactionType.EXPENSE);
        t.setFromAccount(from);
        t.setAmount(new BigDecimal(amount));
        t.setCategory(cat);
        t.setMemo(memo);
        t.setPayee("Shell");
        t.setTransactionDate(date);
        t.setStatus(Transaction.TransactionStatus.COMPLETED);
        transactionService.saveTransaction(t);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=UC109VehicleConsumptionChartTest -q`
Expected: FAIL — `$(ConsumptionTrendChart.class).single()` finds no component (view still renders div bars).

- [ ] **Step 3: Replace the div-bar rendering**

In `src/main/java/com/cuenti/app/views/VehiclesView.java`:

3a. In `renderSummary()`, pass the already-computed average to the chart renderer — change the call

```java
        renderConsumptionChart(trendSection);
```

to

```java
        renderConsumptionChart(trendSection, avgConsumption);
```

3b. Replace the whole `renderConsumptionChart(Div container)` method (the version that builds `chartArea` div bars) with:

```java
    private void renderConsumptionChart(Div container, BigDecimal average) {
        List<FuelEntry> withConsumption = fuelEntries.stream()
                .filter(e -> e.getConsumption() != null)
                .sorted(Comparator.comparing(FuelEntry::getDate))
                .toList();
        if (withConsumption.isEmpty()) {
            Span none = new Span(getTranslation("vehicles.no_consumption_data"));
            none.getStyle().set("font-size", "var(--aura-font-size-s)").set("color", "var(--vaadin-text-color-secondary)");
            container.add(none);
            return;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        List<ConsumptionTrendChart.Point> points = withConsumption.stream()
                .map(e -> {
                    StringBuilder tip = new StringBuilder(e.getDate().format(fmt))
                            .append(" · ").append(e.getConsumption().toPlainString()).append(" L/100km");
                    if (e.getLiters() != null) {
                        tip.append(" · ").append(e.getLiters().setScale(1, RoundingMode.HALF_UP)).append(" L");
                    }
                    tip.append(" · ").append(formatCurrency(
                            exchangeRateService.convert(e.getAmount(), e.getCurrency(), currentUser.getDefaultCurrency())));
                    return new ConsumptionTrendChart.Point(e.getDate(), e.getConsumption(), tip.toString());
                })
                .toList();

        container.add(new ConsumptionTrendChart(points, average,
                Locale.forLanguageTag(currentUser.getLocale())));
    }
```

3c. Add the import (with the other `com.cuenti.app.views.components` imports):

```java
import com.cuenti.app.views.components.charts.ConsumptionTrendChart;
```

3d. Delete now-dead code: the old method body's bar-building loop is gone with 3b; keep `consumptionColor` (still used by the `avg_consumption` summary card and the grid), keep the `DateTimeFormatter`/other imports (still used elsewhere in the class).

- [ ] **Step 4: Run the new test and the existing suite**

Run: `./mvnw test -Dtest=UC109VehicleConsumptionChartTest -q`
Expected: PASS.

Run: `./mvnw test -q`
Expected: PASS — full suite green (smoke test `UC100NavigationSmokeTest` still navigates `VehiclesView`).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cuenti/app/views/VehiclesView.java \
        src/test/java/com/cuenti/app/views/UC109VehicleConsumptionChartTest.java
git commit -m "feat(vehicles): replace div-bar consumption trend with SVG line chart

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Sbj91k9xKknsSx9w8w9tzF"
```

---

### Task 3: Visual verification (dev server)

**Files:** none (verification only).

- [ ] **Step 1: Run the app and inspect the vehicles page**

Start the dev server (`./mvnw spring-boot:run`, demo login), open `/vehicles`, select a category with fuel entries. Verify:

- Chart fills the card width, no horizontal scrollbar.
- Line + dots visible; hovering a dot shows date · consumption · liters · cost.
- Dashed Ø line matches the "Ø Verbrauch" summary-card value.
- Dark and light theme both legible (token uses `light-dark()`).
- Empty category still shows the `vehicles.no_consumption_data` notice.

Note (from project memory): theme fixes must be verified against a production jar — prod ships legacy Lumo needing `html[theme=dark]`. The new token uses the same `light-dark()` mechanism as the existing chart tokens in the same block, so it inherits whatever those already do; if in doubt, spot-check the prod build.

- [ ] **Step 2: Fix anything found, amend or follow-up commit**

If visual issues surface (label collisions, padding), adjust constants in `ConsumptionTrendChart` only; re-run `./mvnw test -Dtest=ConsumptionTrendChartTest -q` after any change.
```
git commit -m "fix(vehicles): consumption trend chart polish"
```
(only if changes were needed; same trailer as above)
