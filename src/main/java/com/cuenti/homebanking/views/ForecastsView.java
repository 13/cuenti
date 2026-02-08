package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.*;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.*;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
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
        setWidthFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("background-color", "var(--lumo-contrast-5pct)");

        selectedYear = LocalDate.now().getYear();

        setupUI();
        loadData();
    }

    private void setupUI() {
        H3 title = new H3(getTranslation("forecasts.title"));
        title.getStyle().set("margin", "0");

        yearSelect = new Select<>();
        yearSelect.setLabel(getTranslation("forecasts.year"));
        int currentYear = LocalDate.now().getYear();
        yearSelect.setItems(String.valueOf(currentYear), String.valueOf(currentYear + 1));
        yearSelect.setValue(String.valueOf(currentYear));
        yearSelect.addValueChangeListener(e -> {
            selectedYear = Integer.parseInt(e.getValue());
            loadData();
        });

        contentContainer.setWidthFull();

        Div card = new Div();
        card.setSizeFull();
        card.getStyle()
                .set("background-color", "var(--lumo-base-color)")
                .set("border-radius", "16px")
                .set("padding", "var(--lumo-space-l)")
                .set("box-shadow", "var(--lumo-box-shadow-m)")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("box-sizing", "border-box")
                .set("gap", "var(--lumo-space-m)");

        card.add(yearSelect, contentContainer);
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
            boolean fromReportable = fromAccount == null || reportableAccountIds.contains(fromAccount.getId());
            boolean toReportable = toAccount == null || reportableAccountIds.contains(toAccount.getId());
            
            if (!fromReportable && !toReportable) continue;

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

        FlexLayout summaryLayout = new FlexLayout();
        summaryLayout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        summaryLayout.getStyle().set("gap", "var(--lumo-space-m)");

        summaryLayout.add(
            createSummaryCard(getTranslation("forecasts.total_income"), totalIncome, "var(--lumo-success-color)"),
            createSummaryCard(getTranslation("forecasts.total_expense"), totalExpense, "var(--lumo-error-color)"),
            createSummaryCard(getTranslation("forecasts.net_forecast"), netForecast,
                netForecast.compareTo(BigDecimal.ZERO) >= 0 ? "var(--lumo-success-color)" : "var(--lumo-error-color)")
        );

        contentContainer.add(summaryLayout);

        // Monthly breakdown
        Div monthlyCard = createInnerCard(getTranslation("forecasts.monthly_breakdown"));
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
        // Get all months in the year
        for (int month = 1; month <= 12; month++) {
            String monthKey = String.format("%d-%02d", selectedYear, month);
            BigDecimal income = incomes.getOrDefault(monthKey, BigDecimal.ZERO);
            BigDecimal expense = expenses.getOrDefault(monthKey, BigDecimal.ZERO);
            BigDecimal net = income.subtract(expense);

            if (income.compareTo(BigDecimal.ZERO) > 0 || expense.compareTo(BigDecimal.ZERO) > 0) {
                Div monthRow = new Div();
                monthRow.getStyle()
                        .set("display", "flex")
                        .set("justify-content", "space-between")
                        .set("padding", "var(--lumo-space-s)")
                        .set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

                String monthName = LocalDate.of(selectedYear, month, 1)
                        .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", 
                            Locale.forLanguageTag(currentUser.getLocale())));

                Span monthLabel = new Span(monthName);
                monthLabel.getStyle().set("font-weight", "500");

                Div amounts = new Div();
                amounts.getStyle().set("display", "flex").set("gap", "var(--lumo-space-l)");

                Span incomeSpan = new Span(formatCurrency(income));
                incomeSpan.getStyle()
                        .set("color", "var(--lumo-success-text-color)")
                        .set("font-weight", "500");

                Span expenseSpan = new Span(formatCurrency(expense));
                expenseSpan.getStyle()
                        .set("color", "var(--lumo-error-text-color)")
                        .set("font-weight", "500");

                Span netSpan = new Span(formatCurrency(net));
                netSpan.getStyle()
                        .set("color", net.compareTo(BigDecimal.ZERO) >= 0 ? 
                            "var(--lumo-success-text-color)" : "var(--lumo-error-text-color)")
                        .set("font-weight", "bold");

                amounts.add(incomeSpan, expenseSpan, netSpan);
                monthRow.add(monthLabel, amounts);
                card.add(monthRow);
            }
        }
    }

    private Div createSummaryCard(String title, BigDecimal amount, String color) {
        Div card = new Div();
        card.getStyle()
                .set("flex", "1 1 200px")
                .set("min-width", "180px")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "12px")
                .set("padding", "var(--lumo-space-m)")
                .set("box-shadow", "none");

        Span titleSpan = new Span(title);
        titleSpan.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("display", "block");

        H4 valueSpan = new H4(formatCurrency(amount));
        valueSpan.getStyle()
                .set("margin", "var(--lumo-space-xs) 0 0 0")
                .set("color", color);

        card.add(titleSpan, valueSpan);
        return card;
    }

    private Div createInnerCard(String title) {
        Div card = new Div();
        card.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "12px")
                .set("padding", "var(--lumo-space-m)")
                .set("margin-top", "var(--lumo-space-m)")
                .set("box-shadow", "none");

        H4 titleEl = new H4(title);
        titleEl.getStyle().set("margin", "0 0 var(--lumo-space-m) 0");
        card.add(titleEl);

        return card;
    }

    private String formatCurrency(BigDecimal amount) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag(currentUser.getLocale()));
        try {
            formatter.setCurrency(java.util.Currency.getInstance(currentUser.getDefaultCurrency()));
        } catch (Exception ignored) {}
        return formatter.format(amount);
    }
}
