package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.*;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.*;
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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Route(value = "statistics", layout = MainLayout.class)
@PageTitle("Statistics | Cuenti")
@PermitAll
public class StatisticsView extends VerticalLayout {

    private final TransactionService transactionService;
    private final AccountService accountService;
    private final ExchangeRateService exchangeRateService;
    private final User currentUser;

    private final Div contentContainer = new Div();
    private List<Transaction> filteredTransactions;
    private List<Account> reportableAccounts;

    private Select<String> timeRangeSelect;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private LocalDate startDate;
    private LocalDate endDate;

    private String currentTab = "overview";
    private String sortCol = "";
    private boolean sortAsc = true;

    public StatisticsView(TransactionService transactionService, AccountService accountService,
                         UserService userService, ExchangeRateService exchangeRateService,
                         SecurityUtils securityUtils) {
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.exchangeRateService = exchangeRateService;

        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        this.currentUser = userService.findByUsername(username);

        addClassName("statistics-view");
        setWidthFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("background-color", "var(--lumo-contrast-5pct)");

        reportableAccounts = accountService.getAccountsByUser(currentUser).stream()
                .filter(a -> !a.isExcludeFromReports())
                .collect(Collectors.toList());

        endDate = LocalDate.now();
        startDate = endDate.withDayOfMonth(1);

        setupUI();
        loadData();
    }

    private void setupUI() {
        H3 title = new H3(getTranslation("statistics.title"));
        title.getStyle().set("margin", "0");

        HorizontalLayout filters = createTimeFilter();
        Tabs tabs = createTabs();

        contentContainer.setWidthFull();

        // Always use card layout
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
        card.add(filters, tabs, contentContainer);
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
        timeRangeSelect.setItems("today", "this_week", "this_month", "this_quarter", "this_year", "last_month", "last_quarter", "last_year", "custom");
        timeRangeSelect.setItemLabelGenerator(item -> getTranslation("statistics.range_" + item));
        timeRangeSelect.setValue("this_month");
        timeRangeSelect.addValueChangeListener(e -> {
            updateDateRange(e.getValue());
            loadData();
        });

        startDatePicker = new DatePicker(getTranslation("statistics.from"));
        startDatePicker.setValue(startDate);
        startDatePicker.setVisible(false);
        startDatePicker.addValueChangeListener(e -> {
            startDate = e.getValue();
            loadData();
        });

        endDatePicker = new DatePicker(getTranslation("statistics.to"));
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
                case "today" -> {
                    startDate = now;
                    endDate = now;
                }
                case "this_week" -> {
                    startDate = now.with(java.time.DayOfWeek.MONDAY);
                    endDate = now;
                }
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
                case "last_quarter" -> {
                    int currentQuarter = (now.getMonthValue() - 1) / 3;
                    int lastQuarter = currentQuarter == 0 ? 3 : currentQuarter - 1;
                    int year = currentQuarter == 0 ? now.getYear() - 1 : now.getYear();
                    startDate = LocalDate.of(year, lastQuarter * 3 + 1, 1);
                    endDate = startDate.plusMonths(2).withDayOfMonth(startDate.plusMonths(2).lengthOfMonth());
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

    private Tabs createTabs() {
        Tabs tabs = new Tabs();
        Tab overviewTab = new Tab(getTranslation("statistics.tab_overview"));
        Tab byAccountTab = new Tab(getTranslation("statistics.tab_by_account"));
        Tab byCategoryTab = new Tab(getTranslation("statistics.tab_by_category"));
        Tab byPayeeTab = new Tab(getTranslation("statistics.tab_by_payee"));
        Tab trendsTab = new Tab(getTranslation("statistics.tab_trends"));

        tabs.add(overviewTab, byAccountTab, byCategoryTab, byPayeeTab, trendsTab);
        tabs.addSelectedChangeListener(e -> {
            Tab selected = e.getSelectedTab();
            if (selected == overviewTab) currentTab = "overview";
            else if (selected == byAccountTab) currentTab = "account";
            else if (selected == byCategoryTab) currentTab = "category";
            else if (selected == byPayeeTab) currentTab = "payee";
            else if (selected == trendsTab) currentTab = "trends";
            sortCol = "";
            sortAsc = true;
            renderContent();
        });

        return tabs;
    }

    private void loadData() {
        List<Transaction> allTransactions = transactionService.getTransactionsByUser(currentUser);

        Set<Long> reportableAccountIds = reportableAccounts.stream()
                .map(Account::getId)
                .collect(Collectors.toSet());

        filteredTransactions = allTransactions.stream()
                .filter(t -> {
                    LocalDate txDate = t.getTransactionDate().toLocalDate();
                    return !txDate.isBefore(startDate) && !txDate.isAfter(endDate);
                })
                .filter(t -> {
                    Account from = t.getFromAccount();
                    Account to = t.getToAccount();
                    if (t.getType() == Transaction.TransactionType.INCOME) {
                        return to != null && reportableAccountIds.contains(to.getId());
                    } else if (t.getType() == Transaction.TransactionType.EXPENSE) {
                        return from != null && reportableAccountIds.contains(from.getId());
                    } else if (t.getType() == Transaction.TransactionType.TRANSFER) {
                        boolean fromOk = from != null && reportableAccountIds.contains(from.getId());
                        boolean toOk = to != null && reportableAccountIds.contains(to.getId());
                        return fromOk || toOk;
                    }
                    return false;
                })
                .collect(Collectors.toList());

        renderContent();
    }

    private void renderContent() {
        contentContainer.removeAll();

        switch (currentTab) {
            case "overview" -> renderOverview();
            case "account" -> renderByAccount();
            case "category" -> renderByCategory();
            case "payee" -> renderByPayee();
            case "trends" -> renderTrends();
        }
    }

    private void renderOverview() {
        FlexLayout layout = new FlexLayout();
        //layout.setWidthFull();
        layout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        layout.getStyle().set("gap", "var(--lumo-space-m)");

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;

        for (Transaction t : filteredTransactions) {
            if (t.getType() == Transaction.TransactionType.INCOME) {
                Account acc = t.getToAccount();
                if (acc != null) {
                    totalIncome = totalIncome.add(
                            exchangeRateService.convert(t.getAmount(), acc.getCurrency(), currentUser.getDefaultCurrency())
                    );
                }
            } else if (t.getType() == Transaction.TransactionType.EXPENSE) {
                Account acc = t.getFromAccount();
                if (acc != null) {
                    totalExpense = totalExpense.add(
                            exchangeRateService.convert(t.getAmount(), acc.getCurrency(), currentUser.getDefaultCurrency())
                    );
                }
            }
        }

        BigDecimal netFlow = totalIncome.subtract(totalExpense);

        layout.add(
                createSummaryCard(getTranslation("statistics.total_income"), totalIncome, "var(--lumo-success-color)"),
                createSummaryCard(getTranslation("statistics.total_expense"), totalExpense, "var(--lumo-error-color)"),
                createSummaryCard(getTranslation("statistics.net_flow"), netFlow,
                        netFlow.compareTo(BigDecimal.ZERO) >= 0 ? "var(--lumo-success-color)" : "var(--lumo-error-color)")
        );

        contentContainer.add(layout);

        Div chartCard = createInnerCard(getTranslation("statistics.income_vs_expense"));
        renderIncomeExpenseChart(chartCard, totalIncome, totalExpense);
        contentContainer.add(chartCard);

        Div topCategoriesCard = createInnerCard(getTranslation("statistics.top_categories"));
        renderTopCategories(topCategoriesCard, 10);
        contentContainer.add(topCategoriesCard);
    }

    private void renderByAccount() {
        Div card = createInnerCard(getTranslation("statistics.by_account"));

        Map<Account, BigDecimal[]> accountData = new LinkedHashMap<>();

        for (Account acc : reportableAccounts) {
            accountData.put(acc, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }

        for (Transaction t : filteredTransactions) {
            if (t.getType() == Transaction.TransactionType.INCOME && t.getToAccount() != null) {
                Account acc = t.getToAccount();
                if (accountData.containsKey(acc)) {
                    BigDecimal converted = exchangeRateService.convert(t.getAmount(), acc.getCurrency(), currentUser.getDefaultCurrency());
                    accountData.get(acc)[0] = accountData.get(acc)[0].add(converted);
                }
            } else if (t.getType() == Transaction.TransactionType.EXPENSE && t.getFromAccount() != null) {
                Account acc = t.getFromAccount();
                if (accountData.containsKey(acc)) {
                    BigDecimal converted = exchangeRateService.convert(t.getAmount(), acc.getCurrency(), currentUser.getDefaultCurrency());
                    accountData.get(acc)[1] = accountData.get(acc)[1].add(converted);
                }
            }
        }

        // Build a String-keyed copy for renderBarCharts
        Map<String, BigDecimal[]> accountDataByName = new LinkedHashMap<>();
        accountData.forEach((acc, vals) -> accountDataByName.put(acc.getAccountName(), vals));

        renderBarCharts(card, accountDataByName,
                getTranslation("statistics.income_by_account"),
                getTranslation("statistics.expense_by_account"));

        HorizontalLayout header = createSortableHeader(
                new String[]{
                        getTranslation("statistics.account"),
                        getTranslation("statistics.income"),
                        getTranslation("statistics.expense"),
                        getTranslation("statistics.net")
                },
                new String[]{"label", "income", "expense", "net"}
        );
        card.add(header);

        Comparator<Map.Entry<Account, BigDecimal[]>> comparator = switch (sortCol) {
            case "income"  -> Comparator.comparing(e -> e.getValue()[0]);
            case "expense" -> Comparator.comparing(e -> e.getValue()[1]);
            case "net"     -> Comparator.comparing(e -> e.getValue()[0].subtract(e.getValue()[1]));
            default        -> Comparator.comparing(e -> e.getKey().getAccountName());
        };
        if (!sortAsc) comparator = comparator.reversed();

        accountData.entrySet().stream()
                .filter(e -> e.getValue()[0].compareTo(BigDecimal.ZERO) > 0 || e.getValue()[1].compareTo(BigDecimal.ZERO) > 0)
                .sorted(comparator)
                .forEach(entry -> {
                    BigDecimal income = entry.getValue()[0];
                    BigDecimal expense = entry.getValue()[1];
                    BigDecimal net = income.subtract(expense);
                    card.add(createDataRow(
                            entry.getKey().getAccountName(),
                            formatCurrency(income),
                            formatCurrency(expense),
                            formatCurrency(net),
                            net.compareTo(BigDecimal.ZERO) >= 0
                    ));
                });

        contentContainer.add(card);
    }

    private void renderByCategory() {
        Div card = createInnerCard(getTranslation("statistics.by_category"));

        // 1. Collect raw data keyed by "parent:child" or "name" for root categories
        Map<String, BigDecimal[]> rawData = new TreeMap<>();

        for (Transaction t : filteredTransactions) {
            if (t.getSplits() != null && !t.getSplits().isEmpty()) {
                for (TransactionSplit split : t.getSplits()) {
                    String key = getCategoryLabel(split.getCategory());
                    rawData.putIfAbsent(key, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    if (t.getType() == Transaction.TransactionType.INCOME && t.getToAccount() != null) {
                        BigDecimal c = exchangeRateService.convert(split.getAmount(), t.getToAccount().getCurrency(), currentUser.getDefaultCurrency());
                        rawData.get(key)[0] = rawData.get(key)[0].add(c);
                    } else if (t.getType() == Transaction.TransactionType.EXPENSE && t.getFromAccount() != null) {
                        BigDecimal c = exchangeRateService.convert(split.getAmount(), t.getFromAccount().getCurrency(), currentUser.getDefaultCurrency());
                        rawData.get(key)[1] = rawData.get(key)[1].add(c);
                    }
                }
            } else {
                String key = getCategoryLabel(t.getCategory());
                rawData.putIfAbsent(key, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                if (t.getType() == Transaction.TransactionType.INCOME && t.getToAccount() != null) {
                    BigDecimal c = exchangeRateService.convert(t.getAmount(), t.getToAccount().getCurrency(), currentUser.getDefaultCurrency());
                    rawData.get(key)[0] = rawData.get(key)[0].add(c);
                } else if (t.getType() == Transaction.TransactionType.EXPENSE && t.getFromAccount() != null) {
                    BigDecimal c = exchangeRateService.convert(t.getAmount(), t.getFromAccount().getCurrency(), currentUser.getDefaultCurrency());
                    rawData.get(key)[1] = rawData.get(key)[1].add(c);
                }
            }
        }

        // 2. Group into parent totals and per-parent children maps
        //    Keys with ":" are "parent:child"; keys without are root categories.
        Map<String, BigDecimal[]> parentTotals = new LinkedHashMap<>();
        Map<String, Map<String, BigDecimal[]>> childrenMap = new LinkedHashMap<>();

        rawData.forEach((label, values) -> {
            if (values[0].compareTo(BigDecimal.ZERO) == 0 && values[1].compareTo(BigDecimal.ZERO) == 0) return;
            int colon = label.indexOf(':');
            if (colon >= 0) {
                String parent = label.substring(0, colon);
                parentTotals.compute(parent, (k, v) -> v == null
                        ? new BigDecimal[]{values[0], values[1]}
                        : new BigDecimal[]{v[0].add(values[0]), v[1].add(values[1])});
                childrenMap.computeIfAbsent(parent, k -> new LinkedHashMap<>()).put(label, values);
            } else {
                parentTotals.compute(label, (k, v) -> v == null
                        ? new BigDecimal[]{values[0], values[1]}
                        : new BigDecimal[]{v[0].add(values[0]), v[1].add(values[1])});
            }
        });

        // 3. Charts — side-by-side income and expense bars per parent category
        renderBarCharts(card, parentTotals,
                getTranslation("statistics.income_by_category"),
                getTranslation("statistics.expense_by_category"));

        // 4. Sortable header
        card.add(createSortableHeader(
                new String[]{getTranslation("statistics.category"), getTranslation("statistics.net")},
                new String[]{"label", "net"}
        ));

        // 5. Sort comparator (applied to both parent groups and children within each group)
        Comparator<Map.Entry<String, BigDecimal[]>> comparator = "label".equals(sortCol)
                ? Comparator.comparing(Map.Entry::getKey)
                : Comparator.comparing(e -> e.getValue()[0].subtract(e.getValue()[1]));
        if (!sortAsc) comparator = comparator.reversed();
        final Comparator<Map.Entry<String, BigDecimal[]>> fc = comparator;

        // 6. Render: parent summary row → indented child rows
        parentTotals.entrySet().stream()
                .sorted(comparator)
                .forEach(parentEntry -> {
                    String parentName = parentEntry.getKey();
                    BigDecimal pIncome  = parentEntry.getValue()[0];
                    BigDecimal pExpense = parentEntry.getValue()[1];
                    BigDecimal pNet     = pIncome.subtract(pExpense);

                    card.add(createCategoryParentRow(parentName, formatCurrency(pNet), pNet.compareTo(BigDecimal.ZERO) >= 0));

                    Map<String, BigDecimal[]> children = childrenMap.get(parentName);
                    if (children != null) {
                        children.entrySet().stream()
                                .sorted(fc)
                                .forEach(childEntry -> {
                                    BigDecimal cNet = childEntry.getValue()[0].subtract(childEntry.getValue()[1]);
                                    card.add(createCategoryChildRow(childEntry.getKey(), formatCurrency(cNet), cNet.compareTo(BigDecimal.ZERO) >= 0));
                                });
                    }
                });

        contentContainer.add(card);
    }

    /**
     * Renders side-by-side income and expense horizontal bar charts from a
     * label → [income, expense] map. Shared by By Category and By Payee tabs.
     */
    private void renderBarCharts(Div container, Map<String, BigDecimal[]> data,
                                  String incomeTitle, String expenseTitle) {
        List<Map.Entry<String, BigDecimal>> incomeEntries = data.entrySet().stream()
                .filter(e -> e.getValue()[0].compareTo(BigDecimal.ZERO) > 0)
                .sorted((a, b) -> b.getValue()[0].compareTo(a.getValue()[0]))
                .limit(10)
                .map(e -> Map.entry(e.getKey(), e.getValue()[0]))
                .collect(Collectors.toList());

        List<Map.Entry<String, BigDecimal>> expenseEntries = data.entrySet().stream()
                .filter(e -> e.getValue()[1].compareTo(BigDecimal.ZERO) > 0)
                .sorted((a, b) -> b.getValue()[1].compareTo(a.getValue()[1]))
                .limit(10)
                .map(e -> Map.entry(e.getKey(), e.getValue()[1]))
                .collect(Collectors.toList());

        if (incomeEntries.isEmpty() && expenseEntries.isEmpty()) return;

        FlexLayout chartsRow = new FlexLayout();
        chartsRow.setWidthFull();
        chartsRow.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        chartsRow.getStyle()
                .set("gap", "var(--lumo-space-l)")
                .set("margin-bottom", "var(--lumo-space-l)")
                .set("padding-bottom", "var(--lumo-space-m)")
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

        if (!incomeEntries.isEmpty()) {
            Div incomeChart = new Div();
            incomeChart.getStyle().set("flex", "1 1 280px").set("min-width", "0");
            renderHorizontalBarChart(incomeChart, incomeTitle, incomeEntries, "var(--lumo-success-color)");
            chartsRow.add(incomeChart);
        }

        if (!expenseEntries.isEmpty()) {
            Div expenseChart = new Div();
            expenseChart.getStyle().set("flex", "1 1 280px").set("min-width", "0");
            renderHorizontalBarChart(expenseChart, expenseTitle, expenseEntries, "var(--lumo-error-color)");
            chartsRow.add(expenseChart);
        }

        container.add(chartsRow);
    }

    /**
     * Renders a single titled horizontal bar chart into the given container.
     * Entries must already be sorted descending; the first entry's value is used as 100%.
     */
    private void renderHorizontalBarChart(Div container, String title,
                                           List<Map.Entry<String, BigDecimal>> entries,
                                           String barColor) {
        if (entries.isEmpty()) return;

        BigDecimal maxValue = entries.get(0).getValue();

        Span chartTitle = new Span(title);
        chartTitle.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("font-weight", "600")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("display", "block")
                .set("margin-bottom", "var(--lumo-space-s)");

        Div chartDiv = new Div();
        chartDiv.setWidthFull();
        chartDiv.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "6px");

        entries.forEach(entry -> {
            double pct = maxValue.compareTo(BigDecimal.ZERO) > 0
                    ? entry.getValue().divide(maxValue, 4, RoundingMode.HALF_UP).doubleValue() * 100
                    : 0;

            Div row = new Div();
            row.setWidthFull();
            row.getStyle().set("display", "flex").set("align-items", "center").set("gap", "var(--lumo-space-s)");

            Span label = new Span(entry.getKey());
            label.getStyle()
                    .set("width", "120px")
                    .set("min-width", "120px")
                    .set("font-size", "var(--lumo-font-size-xs)")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("text-align", "right")
                    .set("overflow", "hidden")
                    .set("text-overflow", "ellipsis")
                    .set("white-space", "nowrap");

            Div barBg = new Div();
            barBg.getStyle()
                    .set("flex", "1")
                    .set("background", "var(--lumo-contrast-10pct)")
                    .set("border-radius", "4px")
                    .set("height", "22px")
                    .set("position", "relative")
                    .set("overflow", "hidden");

            Div bar = new Div();
            bar.getStyle()
                    .set("position", "absolute")
                    .set("left", "0").set("top", "0").set("bottom", "0")
                    .set("width", pct + "%")
                    .set("background", barColor)
                    .set("opacity", "0.65")
                    .set("border-radius", "4px");

            Span amount = new Span(formatCurrency(entry.getValue()));
            amount.getStyle()
                    .set("position", "absolute")
                    .set("right", "var(--lumo-space-xs)")
                    .set("top", "0").set("bottom", "0")
                    .set("display", "flex")
                    .set("align-items", "center")
                    .set("font-size", "var(--lumo-font-size-xs)")
                    .set("font-weight", "600")
                    .set("color", barColor);

            barBg.add(bar, amount);
            row.add(label, barBg);
            chartDiv.add(row);
        });

        container.add(chartTitle, chartDiv);
    }

    private void renderByPayee() {
        Div card = createInnerCard(getTranslation("statistics.by_payee"));

        Map<String, BigDecimal[]> payeeData = new TreeMap<>();

        for (Transaction t : filteredTransactions) {
            String payeeName = t.getPayee() != null && !t.getPayee().isEmpty() ? t.getPayee() : getTranslation("statistics.no_payee");
            payeeData.putIfAbsent(payeeName, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});

            if (t.getType() == Transaction.TransactionType.INCOME && t.getToAccount() != null) {
                BigDecimal converted = exchangeRateService.convert(t.getAmount(), t.getToAccount().getCurrency(), currentUser.getDefaultCurrency());
                payeeData.get(payeeName)[0] = payeeData.get(payeeName)[0].add(converted);
            } else if (t.getType() == Transaction.TransactionType.EXPENSE && t.getFromAccount() != null) {
                BigDecimal converted = exchangeRateService.convert(t.getAmount(), t.getFromAccount().getCurrency(), currentUser.getDefaultCurrency());
                payeeData.get(payeeName)[1] = payeeData.get(payeeName)[1].add(converted);
            }
        }

        renderBarCharts(card, payeeData,
                getTranslation("statistics.income_by_payee"),
                getTranslation("statistics.expense_by_payee"));

        HorizontalLayout header = createSortableHeader(
                new String[]{
                        getTranslation("statistics.payee"),
                        getTranslation("statistics.net")
                },
                new String[]{"label", "net"}
        );
        card.add(header);

        Comparator<Map.Entry<String, BigDecimal[]>> comparator = "label".equals(sortCol)
                ? Comparator.comparing(Map.Entry::getKey)
                : Comparator.comparing(e -> e.getValue()[0].subtract(e.getValue()[1]));
        if (!sortAsc) comparator = comparator.reversed();

        payeeData.entrySet().stream()
                .filter(e -> e.getValue()[0].compareTo(BigDecimal.ZERO) > 0 || e.getValue()[1].compareTo(BigDecimal.ZERO) > 0)
                .sorted(comparator)
                .limit(50)
                .forEach(entry -> {
                    BigDecimal income = entry.getValue()[0];
                    BigDecimal expense = entry.getValue()[1];
                    BigDecimal net = income.subtract(expense);
                    card.add(createNetOnlyRow(entry.getKey(), formatCurrency(net), net.compareTo(BigDecimal.ZERO) >= 0));
                });

        contentContainer.add(card);
    }

    private void renderTrends() {
        Div card = createInnerCard(getTranslation("statistics.monthly_trends"));

        Map<String, BigDecimal[]> monthlyData = new TreeMap<>();
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");

        for (Transaction t : filteredTransactions) {
            String monthKey = t.getTransactionDate().format(monthFormatter);
            monthlyData.putIfAbsent(monthKey, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});

            if (t.getType() == Transaction.TransactionType.INCOME && t.getToAccount() != null) {
                BigDecimal converted = exchangeRateService.convert(t.getAmount(), t.getToAccount().getCurrency(), currentUser.getDefaultCurrency());
                monthlyData.get(monthKey)[0] = monthlyData.get(monthKey)[0].add(converted);
            } else if (t.getType() == Transaction.TransactionType.EXPENSE && t.getFromAccount() != null) {
                BigDecimal converted = exchangeRateService.convert(t.getAmount(), t.getFromAccount().getCurrency(), currentUser.getDefaultCurrency());
                monthlyData.get(monthKey)[1] = monthlyData.get(monthKey)[1].add(converted);
            }
        }

        if (!monthlyData.isEmpty()) {
            renderTrendChart(card, monthlyData);
        }

        HorizontalLayout header = createSortableHeader(
                new String[]{
                        getTranslation("statistics.month"),
                        getTranslation("statistics.income"),
                        getTranslation("statistics.expense"),
                        getTranslation("statistics.net"),
                        getTranslation("statistics.pct_change")
                },
                new String[]{"label", "income", "expense", "net", "pct"}
        );
        card.add(header);

        // Pre-compute % change (net as % of income) for each month so it can also be used as a sort key
        record MonthRow(String month, BigDecimal income, BigDecimal expense, BigDecimal net, BigDecimal pct) {}

        List<MonthRow> rows = monthlyData.entrySet().stream().map(e -> {
            BigDecimal inc = e.getValue()[0];
            BigDecimal exp = e.getValue()[1];
            BigDecimal net = inc.subtract(exp);
            BigDecimal pct = inc.compareTo(BigDecimal.ZERO) > 0
                    ? net.divide(inc, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : null;
            return new MonthRow(e.getKey(), inc, exp, net, pct);
        }).collect(Collectors.toCollection(ArrayList::new));

        Comparator<MonthRow> comparator = switch (sortCol) {
            case "income"  -> Comparator.comparing(MonthRow::income);
            case "expense" -> Comparator.comparing(MonthRow::expense);
            case "net"     -> Comparator.comparing(MonthRow::net);
            case "pct"     -> Comparator.comparing(r -> r.pct() != null ? r.pct() : BigDecimal.valueOf(Long.MIN_VALUE));
            default        -> Comparator.comparing(MonthRow::month);
        };
        if (!sortAsc) comparator = comparator.reversed();
        rows.sort(comparator);

        rows.forEach(row -> card.add(createDataRow(
                row.month(),
                formatCurrency(row.income()),
                formatCurrency(row.expense()),
                formatCurrency(row.net()),
                row.net().compareTo(BigDecimal.ZERO) >= 0,
                formatPctChange(row.pct())
        )));

        contentContainer.add(card);
    }

    private void renderIncomeExpenseChart(Div container, BigDecimal income, BigDecimal expense) {
        BigDecimal total = income.add(expense);
        if (total.compareTo(BigDecimal.ZERO) == 0) return;

        double incomePercent = income.divide(total, 4, RoundingMode.HALF_UP).doubleValue() * 100;
        double expensePercent = 100 - incomePercent;

        Div chartContainer = new Div();
        chartContainer.getStyle()
                .set("display", "flex")
                .set("height", "40px")
                .set("border-radius", "8px")
                .set("overflow", "hidden")
                .set("margin", "var(--lumo-space-m) 0");

        Div incomeBar = new Div();
        incomeBar.setWidth(incomePercent + "%");
        incomeBar.getStyle().set("background", "var(--lumo-success-color)");

        Div expenseBar = new Div();
        expenseBar.setWidth(expensePercent + "%");
        expenseBar.getStyle().set("background", "var(--lumo-error-color)");

        chartContainer.add(incomeBar, expenseBar);

        HorizontalLayout legend = new HorizontalLayout();
        legend.setSpacing(true);
        legend.add(
                createLegendItem(getTranslation("statistics.income") + " (" + String.format("%.1f", incomePercent) + "%)", "var(--lumo-success-color)"),
                createLegendItem(getTranslation("statistics.expense") + " (" + String.format("%.1f", expensePercent) + "%)", "var(--lumo-error-color)")
        );

        container.add(chartContainer, legend);
    }

    private void renderTopCategories(Div container, int limit) {
        Map<String, BigDecimal> categoryExpenses = new HashMap<>();

        for (Transaction t : filteredTransactions) {
            if (t.getType() == Transaction.TransactionType.EXPENSE && t.getFromAccount() != null) {
                if (t.getSplits() != null && !t.getSplits().isEmpty()) {
                    for (TransactionSplit split : t.getSplits()) {
                        String category = getCategoryLabel(split.getCategory());
                        BigDecimal converted = exchangeRateService.convert(split.getAmount(), t.getFromAccount().getCurrency(), currentUser.getDefaultCurrency());
                        categoryExpenses.merge(category, converted, BigDecimal::add);
                    }
                } else {
                    String category = getCategoryLabel(t.getCategory());
                    BigDecimal converted = exchangeRateService.convert(t.getAmount(), t.getFromAccount().getCurrency(), currentUser.getDefaultCurrency());
                    categoryExpenses.merge(category, converted, BigDecimal::add);
                }
            }
        }

        BigDecimal total = categoryExpenses.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        categoryExpenses.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(limit)
                .forEach(entry -> {
                    double percent = total.compareTo(BigDecimal.ZERO) > 0
                            ? entry.getValue().divide(total, 4, RoundingMode.HALF_UP).doubleValue() * 100
                            : 0;
                    container.add(createProgressRow(entry.getKey(), entry.getValue(), percent));
                });
    }

    private void renderTrendChart(Div container, Map<String, BigDecimal[]> monthlyData) {
        Div chartArea = new Div();
        chartArea.getStyle()
                .set("display", "flex")
                .set("align-items", "flex-end")
                .set("justify-content", "space-around")
                .set("height", "200px")
                .set("padding", "var(--lumo-space-m)")
                .set("border-bottom", "1px solid var(--lumo-contrast-20pct)")
                .set("margin-bottom", "var(--lumo-space-m)");

        BigDecimal maxValue = monthlyData.values().stream()
                .flatMap(arr -> Arrays.stream(new BigDecimal[]{arr[0], arr[1]}))
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE);

        for (Map.Entry<String, BigDecimal[]> entry : monthlyData.entrySet()) {
            Div barGroup = new Div();
            barGroup.getStyle()
                    .set("display", "flex")
                    .set("flex-direction", "column")
                    .set("align-items", "center")
                    .set("gap", "4px");

            HorizontalLayout bars = new HorizontalLayout();
            bars.setAlignItems(Alignment.END);
            bars.setSpacing(false);
            bars.getStyle().set("gap", "2px");

            bars.add(createBar(entry.getValue()[0], maxValue, "var(--lumo-success-color)"));
            bars.add(createBar(entry.getValue()[1], maxValue, "var(--lumo-error-color)"));

            Span label = new Span(entry.getKey().substring(5));
            label.getStyle().set("font-size", "10px").set("color", "var(--lumo-secondary-text-color)");

            barGroup.add(bars, label);
            chartArea.add(barGroup);
        }

        HorizontalLayout legend = new HorizontalLayout();
        legend.setSpacing(true);
        legend.add(
                createLegendItem(getTranslation("statistics.income"), "var(--lumo-success-color)"),
                createLegendItem(getTranslation("statistics.expense"), "var(--lumo-error-color)")
        );

        container.add(chartArea, legend);
    }

    private Div createBar(BigDecimal value, BigDecimal max, String color) {
        Div bar = new Div();
        double height = max.compareTo(BigDecimal.ZERO) > 0
                ? value.divide(max, 4, RoundingMode.HALF_UP).doubleValue() * 150
                : 0;
        bar.setWidth("12px");
        bar.setHeight(Math.max(2, height) + "px");
        bar.getStyle()
                .set("background", color)
                .set("border-radius", "2px 2px 0 0");
        return bar;
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

    private HorizontalLayout createTableHeader(String... columns) {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.getStyle()
                .set("padding", "var(--lumo-space-s) 0")
                .set("border-bottom", "2px solid var(--lumo-contrast-10pct)")
                .set("font-weight", "bold")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");

        for (int i = 0; i < columns.length; i++) {
            Span col = new Span(columns[i]);
            col.getStyle().set("flex", i == 0 ? "2" : "1").set("text-align", i == 0 ? "left" : "right");
            header.add(col);
        }

        return header;
    }

    /**
     * Creates a sortable table header. labels[] are display names; keys[] are sort identifiers.
     * Clicking a column header sets sortCol/sortAsc and re-renders the current tab.
     */
    private HorizontalLayout createSortableHeader(String[] labels, String[] keys) {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.getStyle()
                .set("padding", "var(--lumo-space-s) 0")
                .set("border-bottom", "2px solid var(--lumo-contrast-10pct)")
                .set("font-weight", "bold")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");

        for (int i = 0; i < labels.length; i++) {
            final String key = keys[i];
            String indicator = key.equals(sortCol) ? (sortAsc ? " ↑" : " ↓") : "";
            Span col = new Span(labels[i] + indicator);
            col.getStyle()
                    .set("flex", i == 0 ? "2" : "1")
                    .set("text-align", i == 0 ? "left" : "right")
                    .set("cursor", "pointer")
                    .set("user-select", "none");
            if (key.equals(sortCol)) {
                col.getStyle().set("color", "var(--lumo-primary-color)");
            }
            col.addClickListener(e -> {
                if (sortCol.equals(key)) {
                    sortAsc = !sortAsc;
                } else {
                    sortCol = key;
                    sortAsc = true;
                }
                renderContent();
            });
            header.add(col);
        }

        return header;
    }

    private HorizontalLayout createDataRow(String label, String income, String expense, String net, boolean isPositive) {
        return createDataRow(label, income, expense, net, isPositive, null);
    }

    private HorizontalLayout createDataRow(String label, String income, String expense, String net, boolean isPositive, String pctChange) {
        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.getStyle()
                .set("padding", "var(--lumo-space-s) 0")
                .set("border-bottom", "1px solid var(--lumo-contrast-5pct)")
                .set("font-size", "var(--lumo-font-size-s)");

        Span labelSpan = new Span(label);
        labelSpan.getStyle().set("flex", "2");

        Span incomeSpan = new Span(income);
        incomeSpan.getStyle().set("flex", "1").set("text-align", "right").set("color", "var(--lumo-success-color)");

        Span expenseSpan = new Span(expense);
        expenseSpan.getStyle().set("flex", "1").set("text-align", "right").set("color", "var(--lumo-error-color)");

        Span netSpan = new Span(net);
        netSpan.getStyle()
                .set("flex", "1")
                .set("text-align", "right")
                .set("font-weight", "500")
                .set("color", isPositive ? "var(--lumo-success-color)" : "var(--lumo-error-color)");

        row.add(labelSpan, incomeSpan, expenseSpan, netSpan);

        if (pctChange != null) {
            Span pctSpan = new Span(pctChange);
            boolean pctPositive = pctChange.startsWith("+");
            pctSpan.getStyle()
                    .set("flex", "1")
                    .set("text-align", "right")
                    .set("font-size", "var(--lumo-font-size-xs)")
                    .set("color", pctChange.equals("—") ? "var(--lumo-secondary-text-color)"
                            : pctPositive ? "var(--lumo-success-color)" : "var(--lumo-error-color)");
            row.add(pctSpan);
        }

        return row;
    }

    private HorizontalLayout createCategoryParentRow(String label, String net, boolean isPositive) {
        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.getStyle()
                .set("padding", "var(--lumo-space-s) 0 var(--lumo-space-xs) 0")
                .set("border-top", "1px solid var(--lumo-contrast-10pct)")
                .set("margin-top", "var(--lumo-space-xs)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("font-weight", "600");

        Span labelSpan = new Span(label);
        labelSpan.getStyle().set("flex", "2");

        Span netSpan = new Span(net);
        netSpan.getStyle()
                .set("flex", "1")
                .set("text-align", "right")
                .set("color", isPositive ? "var(--lumo-success-color)" : "var(--lumo-error-color)");

        row.add(labelSpan, netSpan);
        return row;
    }

    private HorizontalLayout createCategoryChildRow(String label, String net, boolean isPositive) {
        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.getStyle()
                .set("padding", "var(--lumo-space-xs) 0")
                .set("border-bottom", "1px solid var(--lumo-contrast-5pct)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("padding-left", "var(--lumo-space-l)");

        Span labelSpan = new Span(label);
        labelSpan.getStyle()
                .set("flex", "2")
                .set("color", "var(--lumo-secondary-text-color)");

        Span netSpan = new Span(net);
        netSpan.getStyle()
                .set("flex", "1")
                .set("text-align", "right")
                .set("color", isPositive ? "var(--lumo-success-color)" : "var(--lumo-error-color)");

        row.add(labelSpan, netSpan);
        return row;
    }

    private HorizontalLayout createNetOnlyRow(String label, String net, boolean isPositive) {
        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.getStyle()
                .set("padding", "var(--lumo-space-s) 0")
                .set("border-bottom", "1px solid var(--lumo-contrast-5pct)")
                .set("font-size", "var(--lumo-font-size-s)");

        Span labelSpan = new Span(label);
        labelSpan.getStyle().set("flex", "2");

        Span netSpan = new Span(net);
        netSpan.getStyle()
                .set("flex", "1")
                .set("text-align", "right")
                .set("font-weight", "500")
                .set("color", isPositive ? "var(--lumo-success-color)" : "var(--lumo-error-color)");

        row.add(labelSpan, netSpan);
        return row;
    }

    private String getCategoryLabel(Category cat) {
        if (cat == null) return getTranslation("statistics.uncategorized");
        // Adjust getParent() to match your Category entity's actual parent accessor
        Category parent = cat.getParent();
        if (parent != null) {
            return parent.getName() + ":" + cat.getName();
        }
        return cat.getName();
    }

    private String formatPctChange(BigDecimal pct) {
        if (pct == null) return "—";
        String sign = pct.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        return sign + pct.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private Div createProgressRow(String label, BigDecimal amount, double percent) {
        Div row = new Div();
        row.setWidthFull();
        row.getStyle().set("margin-bottom", "var(--lumo-space-s)");

        HorizontalLayout info = new HorizontalLayout();
        info.setWidthFull();
        info.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        info.add(new Span(label), new Span(formatCurrency(amount)));

        Div progressBg = new Div();
        progressBg.setWidthFull();
        progressBg.getStyle()
                .set("background", "var(--lumo-contrast-10pct)")
                .set("border-radius", "4px")
                .set("height", "6px")
                .set("margin-top", "4px");

        Div progressBar = new Div();
        progressBar.setWidth(percent + "%");
        progressBar.setHeight("100%");
        progressBar.getStyle().set("background", "var(--lumo-primary-color)").set("border-radius", "4px");

        progressBg.add(progressBar);
        row.add(info, progressBg);
        return row;
    }

    private HorizontalLayout createLegendItem(String label, String color) {
        HorizontalLayout item = new HorizontalLayout();
        item.setAlignItems(Alignment.CENTER);
        item.setSpacing(true);

        Div dot = new Div();
        dot.setWidth("10px");
        dot.setHeight("10px");
        dot.getStyle().set("background", color).set("border-radius", "50%");

        Span text = new Span(label);
        text.getStyle().set("font-size", "var(--lumo-font-size-s)");

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
