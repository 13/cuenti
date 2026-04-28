package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.*;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.*;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Route(value = "forecasts", layout = MainLayout.class)
@PageTitle("Forecasts | Cuenti")
@PermitAll
public class ForecastsView extends VerticalLayout {

    private final ScheduledTransactionService scheduledService;
    private final AccountService accountService;
    private final ExchangeRateService exchangeRateService;
    private final User currentUser;

    private final Div contentContainer = new Div();
    private Select<String> yearSelect;
    private int selectedYear;

    public ForecastsView(ScheduledTransactionService scheduledService, AccountService accountService,
                         UserService userService, ExchangeRateService exchangeRateService,
                         SecurityUtils securityUtils) {
        this.scheduledService = scheduledService;
        this.accountService = accountService;
        this.exchangeRateService = exchangeRateService;

        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        this.currentUser = userService.findByUsername(username);

        addClassName("forecasts-view");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("padding", "var(--lumo-space-m)")
                .set("overflow", "hidden");

        selectedYear = LocalDate.now().getYear();

        setupUI();
        loadData();
    }

    private void setupUI() {
        // Page header
        Span title = new Span(getTranslation("forecasts.title"));
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-xxl)")
                .set("font-weight", "700")
                .set("color", "var(--lumo-header-text-color)");

        // Year selector toolbar
        yearSelect = new Select<>();
        yearSelect.setLabel(getTranslation("forecasts.year"));
        int currentYear = LocalDate.now().getYear();
        yearSelect.setItems(
                String.valueOf(currentYear - 1),
                String.valueOf(currentYear),
                String.valueOf(currentYear + 1),
                String.valueOf(currentYear + 2)
        );
        yearSelect.setValue(String.valueOf(currentYear));
        yearSelect.addValueChangeListener(e -> {
            selectedYear = Integer.parseInt(e.getValue());
            loadData();
        });

        Div toolbar = new Div(yearSelect);
        toolbar.setWidthFull();
        toolbar.getStyle()
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "12px")
                .set("box-sizing", "border-box");

        contentContainer.setWidthFull();
        contentContainer.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "var(--lumo-space-m)");

        Div card = new Div();
        card.setSizeFull();
        card.getStyle()
                .set("background-color", "var(--lumo-base-color)")
                .set("border-radius", "20px")
                .set("padding", "var(--lumo-space-l)")
                .set("box-shadow", "0 2px 12px rgba(0,0,0,0.06)")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("box-sizing", "border-box")
                .set("gap", "var(--lumo-space-m)")
                .set("overflow-y", "auto");

        card.add(toolbar, contentContainer);
        add(title, card);
        expand(card);
    }

    private void loadData() {
        contentContainer.removeAll();

        List<ScheduledTransaction> allScheduled = scheduledService.getByUser(currentUser);
        List<Account> reportableAccounts = accountService.getAccountsByUser(currentUser).stream()
                .filter(a -> !a.isExcludeFromReports())
                .collect(Collectors.toList());
        
        Set<Long> reportableAccountIds = reportableAccounts.stream()
                .map(Account::getId)
                .collect(Collectors.toSet());

        // Filter scheduled transactions that are enabled and will occur in the selected year
        LocalDate yearStart = LocalDate.of(selectedYear, 1, 1);
        LocalDate yearEnd = LocalDate.of(selectedYear, 12, 31);

        Map<String, BigDecimal> monthlyIncomes = new TreeMap<>();
        Map<String, BigDecimal> monthlyExpenses = new TreeMap<>();
        
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;

        for (ScheduledTransaction st : allScheduled) {
            if (!st.isEnabled()) continue;
            
            // Check if the transaction's account is reportable
            Account fromAccount = st.getFromAccount();
            Account toAccount = st.getToAccount();

            if (st.getType() == Transaction.TransactionType.INCOME) {
                if (toAccount == null || !reportableAccountIds.contains(toAccount.getId())) continue;
            } else if (st.getType() == Transaction.TransactionType.EXPENSE) {
                if (fromAccount == null || !reportableAccountIds.contains(fromAccount.getId())) continue;
            } else {
                continue; // Ignore transfers for now as they aren't handled in forecasts
            }

            // Calculate all occurrences in the selected year
            LocalDateTime occurrence = st.getNextOccurrence();
            LocalDate occurrenceDate = occurrence.toLocalDate();
            
            while (occurrenceDate.getYear() < selectedYear) {
                occurrence = getNextOccurrence(occurrence, st);
                occurrenceDate = occurrence.toLocalDate();
            }

            while (occurrenceDate.getYear() == selectedYear) {
                String monthKey = String.format("%d-%02d", occurrenceDate.getYear(), occurrenceDate.getMonthValue());
                
                BigDecimal convertedAmount = st.getAmount();
                
                if (st.getType() == Transaction.TransactionType.INCOME) {
                    String currency = toAccount != null ? toAccount.getCurrency() : currentUser.getDefaultCurrency();
                    convertedAmount = exchangeRateService.convert(
                        st.getAmount(), 
                        currency, 
                        currentUser.getDefaultCurrency()
                    );
                    monthlyIncomes.merge(monthKey, convertedAmount, BigDecimal::add);
                    totalIncome = totalIncome.add(convertedAmount);
                } else if (st.getType() == Transaction.TransactionType.EXPENSE) {
                    String currency = fromAccount != null ? fromAccount.getCurrency() : currentUser.getDefaultCurrency();
                    convertedAmount = exchangeRateService.convert(
                        st.getAmount(), 
                        currency,
                        currentUser.getDefaultCurrency()
                    );
                    monthlyExpenses.merge(monthKey, convertedAmount, BigDecimal::add);
                    totalExpense = totalExpense.add(convertedAmount);
                }

                occurrence = getNextOccurrence(occurrence, st);
                occurrenceDate = occurrence.toLocalDate();
            }
        }

        BigDecimal netForecast = totalIncome.subtract(totalExpense);

        // ── Summary cards ────────────────────────────────────────────────
        FlexLayout summaryLayout = new FlexLayout();
        summaryLayout.setWidthFull();
        summaryLayout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        summaryLayout.getStyle().set("gap", "var(--lumo-space-m)");

        summaryLayout.add(
                createSummaryCard(getTranslation("forecasts.total_income"),  totalIncome,  VaadinIcon.ARROW_DOWN, "var(--lumo-success-color)"),
                createSummaryCard(getTranslation("forecasts.total_expense"), totalExpense, VaadinIcon.ARROW_UP,   "var(--lumo-error-color)"),
                createSummaryCard(getTranslation("forecasts.net_forecast"),  netForecast,  VaadinIcon.SCALE,
                        netForecast.compareTo(BigDecimal.ZERO) >= 0 ? "var(--lumo-success-color)" : "var(--lumo-error-color)")
        );
        contentContainer.add(summaryLayout);

        // ── Year overview chart ───────────────────────────────────────
        Div chartCard = createInnerCard(getTranslation("forecasts.yearly_overview"), VaadinIcon.BAR_CHART);
        chartCard.setWidthFull();
        renderYearChart(chartCard, monthlyIncomes, monthlyExpenses);
        contentContainer.add(chartCard);

        // ── Monthly breakdown table ───────────────────────────────────
        Div monthlyCard = createInnerCard(getTranslation("forecasts.monthly_breakdown"), VaadinIcon.LIST);
        renderMonthlyBreakdown(monthlyCard, monthlyIncomes, monthlyExpenses);
        contentContainer.add(monthlyCard);
    }

    private LocalDateTime getNextOccurrence(LocalDateTime current, ScheduledTransaction st) {
        LocalDateTime next = current;
        int value = (st.getRecurrenceValue() != null && st.getRecurrenceValue() > 0) 
                    ? st.getRecurrenceValue() : 1;

        switch (st.getRecurrencePattern()) {
            case DAILY -> next = next.plusDays(value);
            case WEEKLY -> next = next.plusWeeks(value);
            case BI_WEEKLY -> next = next.plusWeeks(2);
            case MONTHLY -> next = next.plusMonths(value);
            case MONTHLY_LAST_DAY -> next = next.plusMonths(1).with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
            case YEARLY -> next = next.plusYears(value);
            case EVERY_FRIDAY -> next = next.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.FRIDAY));
            case EVERY_SATURDAY -> next = next.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SATURDAY));
            case EVERY_WEEKDAY -> {
                next = next.plusDays(1);
                while (next.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || 
                       next.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                    next = next.plusDays(1);
                }
            }
        }
        return next;
    }

    private void renderMonthlyBreakdown(Div card, Map<String, BigDecimal> incomes, Map<String, BigDecimal> expenses) {
        boolean any = false;
        LocalDate today = LocalDate.now();

        // Table header
        Div header = new Div();
        header.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr repeat(3, minmax(120px,auto))")
                .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
                .set("margin-bottom", "2px");

        for (String col : new String[]{
                getTranslation("forecasts.month"),
                getTranslation("statistics.income"),
                getTranslation("statistics.expense"),
                getTranslation("statistics.net")}) {
            Span h = new Span(col.toUpperCase());
            h.getStyle()
                    .set("font-size", "10px").set("font-weight", "700").set("letter-spacing", "0.07em")
                    .set("color", "var(--lumo-secondary-text-color)").set("text-align", "right");
            header.add(h);
        }
        // Override first col to left-align
        ((Span) header.getChildren().findFirst().orElseThrow()).getStyle().set("text-align", "left");
        card.add(header);

        for (int month = 1; month <= 12; month++) {
            String monthKey = String.format("%d-%02d", selectedYear, month);
            BigDecimal income  = incomes.getOrDefault(monthKey, BigDecimal.ZERO);
            BigDecimal expense = expenses.getOrDefault(monthKey, BigDecimal.ZERO);
            BigDecimal net     = income.subtract(expense);

            if (income.compareTo(BigDecimal.ZERO) == 0 && expense.compareTo(BigDecimal.ZERO) == 0) continue;
            any = true;

            boolean isCurrent = (today.getYear() == selectedYear && today.getMonthValue() == month);
            boolean isPast    = selectedYear < today.getYear() ||
                                (selectedYear == today.getYear() && month < today.getMonthValue());

            String monthName = LocalDate.of(selectedYear, month, 1)
                    .format(java.time.format.DateTimeFormatter.ofPattern("MMMM", Locale.forLanguageTag(currentUser.getLocale())));

            Div row = new Div();
            row.getStyle()
                    .set("display", "grid")
                    .set("grid-template-columns", "1fr repeat(3, minmax(120px,auto))")
                    .set("padding", "var(--lumo-space-s) var(--lumo-space-s)")
                    .set("border-radius", "8px")
                    .set("border-bottom", "1px solid var(--lumo-contrast-5pct)")
                    .set("align-items", "center")
                    .set("transition", "background 0.12s");
            if (isCurrent) {
                row.getStyle().set("background", "var(--lumo-primary-color-10pct, rgba(var(--lumo-primary-color-rgb,26,119,242),0.08))");
            }
            row.getElement().executeJs(
                "const orig = '" + (isCurrent ? "var(--lumo-primary-color-10pct, rgba(26,119,242,0.08))" : "") + "';" +
                "this.addEventListener('mouseenter', () => this.style.background='var(--lumo-contrast-5pct)');" +
                "this.addEventListener('mouseleave', () => this.style.background=orig);"
            );

            // Month label + badges
            Div labelCell = new Div();
            labelCell.getStyle().set("display", "flex").set("align-items", "center").set("gap", "var(--lumo-space-s)");

            Span monthSpan = new Span(monthName);
            monthSpan.getStyle()
                    .set("font-weight", isCurrent ? "700" : "500")
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("color", isPast ? "var(--lumo-secondary-text-color)" : "var(--lumo-body-text-color)");
            labelCell.add(monthSpan);

            if (isCurrent) {
                Span nowBadge = new Span(getTranslation("scheduled.today"));
                nowBadge.getStyle()
                        .set("font-size", "9px").set("font-weight", "700").set("letter-spacing", "0.05em")
                        .set("padding", "1px 6px").set("border-radius", "99px")
                        .set("background", "var(--lumo-primary-color)").set("color", "white");
                labelCell.add(nowBadge);
            }

            Span incomeSpan  = createAmountCell(income, "var(--lumo-success-color)");
            Span expenseSpan = createAmountCell(expense, "var(--lumo-error-color)");
            Span netSpan     = createAmountCell(net,
                    net.compareTo(BigDecimal.ZERO) >= 0 ? "var(--lumo-success-color)" : "var(--lumo-error-color)");
            netSpan.getStyle().set("font-weight", "700");

            row.add(labelCell, incomeSpan, expenseSpan, netSpan);
            card.add(row);
        }

        if (!any) {
            Div empty = new Div();
            empty.getStyle()
                    .set("padding", "var(--lumo-space-xl)").set("text-align", "center")
                    .set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");
            empty.add(new Span(getTranslation("forecasts.no_scheduled")));
            card.add(empty);
        }
    }

    private Span createAmountCell(BigDecimal amount, String color) {
        Span s = new Span(amount.compareTo(BigDecimal.ZERO) == 0 ? "—" : formatCurrency(amount));
        s.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("font-weight", "600")
                .set("text-align", "right")
                .set("color", amount.compareTo(BigDecimal.ZERO) == 0 ? "var(--lumo-disabled-text-color)" : color);
        return s;
    }

    private Div createSummaryCard(String title, BigDecimal amount, VaadinIcon iconType, String color) {
        Div card = new Div();
        card.getStyle()
                .set("flex", "1 1 200px").set("min-width", "180px")
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "16px")
                .set("padding", "var(--lumo-space-m) var(--lumo-space-l)")
                .set("box-shadow", "0 1px 6px rgba(0,0,0,0.07)")
                .set("border-left", "4px solid " + color)
                .set("display", "flex").set("flex-direction", "column").set("gap", "var(--lumo-space-xs)");

        Span titleSpan = new Span(title.toUpperCase());
        titleSpan.getStyle()
                .set("font-size", "10px").set("font-weight", "700").set("letter-spacing", "0.08em")
                .set("color", "var(--lumo-secondary-text-color)");

        Span valueSpan = new Span(formatCurrency(amount));
        valueSpan.getStyle()
                .set("font-size", "var(--lumo-font-size-xxl)").set("font-weight", "700")
                .set("color", color).set("line-height", "1.1");

        card.add(titleSpan, valueSpan);
        return card;
    }

    private Div createInnerCard(String title, VaadinIcon iconType) {
        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "16px")
                .set("padding", "var(--lumo-space-m) var(--lumo-space-l)")
                .set("box-sizing", "border-box");

        Icon ico = iconType.create();
        ico.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-m)").set("flex-shrink", "0");

        Span titleSpan = new Span(title);
        titleSpan.getStyle()
                .set("font-size", "var(--lumo-font-size-m)").set("font-weight", "700")
                .set("color", "var(--lumo-header-text-color)");

        HorizontalLayout header = new HorizontalLayout(ico, titleSpan);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setSpacing(false);
        header.getStyle().set("gap", "var(--lumo-space-s)").set("margin-bottom", "var(--lumo-space-m)");
        card.add(header);
        return card;
    }

    /** Yearly bar chart — income (green) and expense (red) bars per month. */
    private void renderYearChart(Div card, Map<String, BigDecimal> incomes, Map<String, BigDecimal> expenses) {
        BigDecimal maxVal = BigDecimal.ONE;
        for (int m = 1; m <= 12; m++) {
            String key = String.format("%d-%02d", selectedYear, m);
            maxVal = maxVal.max(incomes.getOrDefault(key, BigDecimal.ZERO));
            maxVal = maxVal.max(expenses.getOrDefault(key, BigDecimal.ZERO));
        }

        boolean anyData = false;
        for (int m = 1; m <= 12; m++) {
            String key = String.format("%d-%02d", selectedYear, m);
            if (incomes.getOrDefault(key, BigDecimal.ZERO).compareTo(BigDecimal.ZERO) > 0
                    || expenses.getOrDefault(key, BigDecimal.ZERO).compareTo(BigDecimal.ZERO) > 0) {
                anyData = true; break;
            }
        }

        if (!anyData) {
            Span none = new Span(getTranslation("forecasts.no_scheduled"));
            none.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)");
            card.add(none);
            return;
        }

        Div chartScroll = new Div();
        chartScroll.getStyle().set("overflow-x", "auto").set("padding-bottom", "var(--lumo-space-xs)");

        Div chartArea = new Div();
        chartArea.getStyle()
                .set("display", "flex").set("align-items", "flex-end")
                .set("gap", "8px").set("height", "140px").set("padding", "0 var(--lumo-space-s)")
                .set("border-bottom", "2px solid var(--lumo-contrast-10pct)")
                .set("min-width", "min-content");

        java.time.format.DateTimeFormatter monthFmt = java.time.format.DateTimeFormatter.ofPattern("MMM",
                Locale.forLanguageTag(currentUser.getLocale()));
        LocalDate today = LocalDate.now();

        for (int m = 1; m <= 12; m++) {
            String key = String.format("%d-%02d", selectedYear, m);
            BigDecimal inc = incomes.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal exp = expenses.getOrDefault(key, BigDecimal.ZERO);

            boolean isCurrent = (today.getYear() == selectedYear && today.getMonthValue() == m);
            String monthLabel = LocalDate.of(selectedYear, m, 1).format(monthFmt);

            Div group = new Div();
            group.getStyle()
                    .set("display", "flex").set("flex-direction", "column")
                    .set("align-items", "center").set("gap", "4px").set("min-width", "44px");

            Div bars = new Div();
            bars.getStyle().set("display", "flex").set("align-items", "flex-end")
                    .set("gap", "3px").set("height", "110px");

            bars.add(createChartBar(inc, maxVal, "var(--lumo-success-color)", "#b7f5c8"));
            bars.add(createChartBar(exp, maxVal, "var(--lumo-error-color)", "#ffb3b3"));

            Span lbl = new Span(monthLabel);
            lbl.getStyle()
                    .set("font-size", "10px").set("font-weight", isCurrent ? "700" : "500")
                    .set("color", isCurrent ? "var(--lumo-primary-color)" : "var(--lumo-secondary-text-color)")
                    .set("white-space", "nowrap");

            if (isCurrent) {
                Div dot = new Div();
                dot.getStyle()
                        .set("width", "5px").set("height", "5px").set("border-radius", "50%")
                        .set("background", "var(--lumo-primary-color)").set("margin", "0 auto");
                group.add(bars, dot, lbl);
            } else {
                group.add(bars, lbl);
            }
            chartArea.add(group);
        }
        chartScroll.add(chartArea);

        // Legend
        HorizontalLayout legend = new HorizontalLayout();
        legend.setSpacing(false);
        legend.getStyle().set("gap", "var(--lumo-space-m)").set("margin-top", "var(--lumo-space-s)");
        legend.add(createLegendItem(getTranslation("statistics.income"), "var(--lumo-success-color)"));
        legend.add(createLegendItem(getTranslation("statistics.expense"), "var(--lumo-error-color)"));
        card.add(chartScroll, legend);
    }

    private Div createChartBar(BigDecimal value, BigDecimal max, String colorTop, String colorBottom) {
        double h = max.compareTo(BigDecimal.ZERO) > 0
                ? value.divide(max, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100
                : 0;
        Div bar = new Div();
        bar.getStyle()
                .set("width", "16px").set("height", Math.max(2, h) + "px")
                .set("background", "linear-gradient(to top, " + colorBottom + ", " + colorTop + ")")
                .set("border-radius", "3px 3px 0 0");
        return bar;
    }

    private HorizontalLayout createLegendItem(String label, String color) {
        HorizontalLayout item = new HorizontalLayout();
        item.setAlignItems(FlexComponent.Alignment.CENTER);
        item.setSpacing(false);
        item.getStyle().set("gap", "6px");
        Div dot = new Div();
        dot.getStyle().set("width", "10px").set("height", "10px")
                .set("background", color).set("border-radius", "3px").set("flex-shrink", "0");
        Span text = new Span(label);
        text.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("font-weight", "500")
                .set("color", "var(--lumo-secondary-text-color)");
        item.add(dot, text);
        return item;
    }

    private String formatCurrency(BigDecimal amount) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag(currentUser.getLocale()));
        try {
            formatter.setCurrency(java.util.Currency.getInstance(currentUser.getDefaultCurrency()));
        } catch (Exception ignored) {}
        return formatter.format(amount);
    }
}
