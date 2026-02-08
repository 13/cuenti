package com.cuenti.homebanking.views.statistics;

import com.cuenti.homebanking.data.*;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.services.*;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@PageTitle("Statistics")
@Route("statistics")
@Menu(order = 2, icon = LineAwesomeIconUrl.CHART_PIE_SOLID)
@PermitAll
public class StatisticsView extends VerticalLayout {

    private final TransactionService transactionService;
    private final AccountService accountService;
    private final ExchangeRateService exchangeRateService;
    private final User currentUser;

    private final Div contentContainer = new Div();
    private List<Transaction> filteredTransactions;

    private Select<String> timeRangeSelect;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private LocalDate startDate;
    private LocalDate endDate;

    public StatisticsView(TransactionService transactionService, AccountService accountService,
                         UserService userService, ExchangeRateService exchangeRateService,
                         SecurityUtils securityUtils) {
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.exchangeRateService = exchangeRateService;

        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        this.currentUser = userService.findByUsername(username);

        addClassName("statistics-view");
        setSpacing(true);
        setPadding(true);
        setMaxWidth("1200px");
        getStyle().set("margin", "0 auto");

        endDate = LocalDate.now();
        startDate = endDate.withDayOfMonth(1);

        setupUI();
        loadData();
    }

    private void setupUI() {
        H3 title = new H3(getTranslation("statistics.title"));
        title.getStyle().set("margin", "0");

        HorizontalLayout filters = createTimeFilter();

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
        card.add(filters, contentContainer);
        add(title, card);
        expand(card);
    }

    private HorizontalLayout createTimeFilter() {
        HorizontalLayout filterLayout = new HorizontalLayout();
        filterLayout.setWidthFull();
        filterLayout.setAlignItems(Alignment.END);
        filterLayout.getStyle().set("flex-wrap", "wrap").set("gap", "var(--lumo-space-m)");

        timeRangeSelect = new Select<>();
        timeRangeSelect.setLabel(getTranslation("statistics.time_range"));
        timeRangeSelect.setItems("this_month", "this_quarter", "this_year", "last_month", "last_year", "custom");
        timeRangeSelect.setValue("this_month");
        timeRangeSelect.addValueChangeListener(e -> {
            updateDateRange(e.getValue());
            loadData();
        });

        startDatePicker = new DatePicker("From");
        startDatePicker.setValue(startDate);
        startDatePicker.setVisible(false);
        startDatePicker.addValueChangeListener(e -> {
            startDate = e.getValue();
            loadData();
        });

        endDatePicker = new DatePicker("To");
        endDatePicker.setValue(endDate);
        endDatePicker.setVisible(false);
        endDatePicker.addValueChangeListener(e -> {
            endDate = e.getValue();
            loadData();
        });

        filterLayout.add(timeRangeSelect, startDatePicker, endDatePicker);
        return filterLayout;
    }

    private void updateDateRange(String range) {
        LocalDate now = LocalDate.now();
        boolean showCustom = "custom".equals(range);
        startDatePicker.setVisible(showCustom);
        endDatePicker.setVisible(showCustom);

        if (!showCustom) {
            switch (range) {
                case "this_month" -> {
                    startDate = now.withDayOfMonth(1);
                    endDate = now;
                }
                case "this_quarter" -> {
                    int quarter = (now.getMonthValue() - 1) / 3;
                    startDate = now.withMonth(quarter * 3 + 1).withDayOfMonth(1);
                    endDate = now;
                }
                case "this_year" -> {
                    startDate = now.withDayOfYear(1);
                    endDate = now;
                }
                case "last_month" -> {
                    LocalDate lastMonth = now.minusMonths(1);
                    startDate = lastMonth.withDayOfMonth(1);
                    endDate = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth());
                }
                case "last_year" -> {
                    startDate = now.minusYears(1).withDayOfYear(1);
                    endDate = now.minusYears(1).withDayOfYear(now.minusYears(1).lengthOfYear());
                }
            }
            startDatePicker.setValue(startDate);
            endDatePicker.setValue(endDate);
        }
    }

    private void loadData() {
        List<Transaction> all = transactionService.getTransactionsByUser(currentUser);
        filteredTransactions = all.stream()
                .filter(t -> {
                    LocalDate txDate = t.getTransactionDate().toLocalDate();
                    return !txDate.isBefore(startDate) && !txDate.isAfter(endDate);
                })
                .collect(Collectors.toList());
        renderContent();
    }

    private void renderContent() {
        contentContainer.removeAll();

        // Calculate totals
        BigDecimal totalIncome = filteredTransactions.stream()
                .filter(t -> t.getType() == Transaction.TransactionType.INCOME)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpense = filteredTransactions.stream()
                .filter(t -> t.getType() == Transaction.TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netFlow = totalIncome.subtract(totalExpense);

        // Summary cards
        FlexLayout summaryLayout = new FlexLayout();
        summaryLayout.setWidthFull();
        summaryLayout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        summaryLayout.getStyle().set("gap", "var(--lumo-space-m)");

        summaryLayout.add(
                createSummaryCard("Total Income", totalIncome, "var(--lumo-success-color)"),
                createSummaryCard("Total Expenses", totalExpense, "var(--lumo-error-color)"),
                createSummaryCard("Net Cash Flow", netFlow, netFlow.compareTo(BigDecimal.ZERO) >= 0 ? "var(--lumo-success-color)" : "var(--lumo-error-color)")
        );

        contentContainer.add(summaryLayout);

        // Expense by category breakdown
        H4 categoryTitle = new H4("Expenses by Category");
        categoryTitle.getStyle().set("margin-top", "var(--lumo-space-l)");
        contentContainer.add(categoryTitle);

        Map<String, BigDecimal> expensesByCategory = filteredTransactions.stream()
                .filter(t -> t.getType() == Transaction.TransactionType.EXPENSE && t.getCategory() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getCategory().getName(),
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)
                ));

        BigDecimal totalCategorized = expensesByCategory.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Div categoriesContainer = new Div();
        categoriesContainer.setWidthFull();
        categoriesContainer.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "8px");

        expensesByCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> {
                    double percentage = totalCategorized.compareTo(BigDecimal.ZERO) > 0
                            ? entry.getValue().divide(totalCategorized, 4, RoundingMode.HALF_UP).doubleValue() * 100
                            : 0;
                    categoriesContainer.add(createProgressBar(entry.getKey(), formatCurrency(entry.getValue()), percentage));
                });

        contentContainer.add(categoriesContainer);
    }

    private Div createSummaryCard(String title, BigDecimal amount, String color) {
        Div card = new Div();
        card.getStyle()
                .set("flex", "1 1 200px")
                .set("min-width", "150px")
                .set("background-color", "var(--lumo-contrast-5pct)")
                .set("border-radius", "12px")
                .set("padding", "var(--lumo-space-m)")
                .set("border-left", "4px solid " + color);

        Span titleSpan = new Span(title);
        titleSpan.getStyle().set("font-size", "12px").set("color", "var(--lumo-secondary-text-color)").set("display", "block");

        H4 amountSpan = new H4(formatCurrency(amount));
        amountSpan.getStyle().set("margin", "var(--lumo-space-xs) 0 0 0").set("color", color);

        card.add(titleSpan, amountSpan);
        return card;
    }

    private Div createProgressBar(String label, String value, double percentage) {
        Div row = new Div();
        row.setWidthFull();
        row.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "4px");

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.add(new Span(label), new Span(value));

        Div progressBg = new Div();
        progressBg.setWidthFull();
        progressBg.getStyle()
                .set("background", "var(--lumo-contrast-10pct)")
                .set("border-radius", "4px")
                .set("height", "8px")
                .set("overflow", "hidden");

        Div progressBar = new Div();
        progressBar.setWidth(percentage + "%");
        progressBar.setHeight("100%");
        progressBar.getStyle().set("background", "var(--lumo-primary-color)");
        progressBg.add(progressBar);

        row.add(header, progressBg);
        return row;
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "";
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag(currentUser.getLocale()));
        try {
            java.util.Currency currency = java.util.Currency.getInstance(currentUser.getDefaultCurrency());
            formatter.setCurrency(currency);
        } catch (Exception e) {
            // Use default
        }
        return formatter.format(amount);
    }
}
