package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.*;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.AccountService;
import com.cuenti.homebanking.service.AssetService;
import com.cuenti.homebanking.service.ExchangeRateService;
import com.cuenti.homebanking.service.TransactionService;
import com.cuenti.homebanking.service.UserService;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
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
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced Dashboard with metrics, asset performance, accounts overview, and charts.
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Dashboard | Cuenti")
@PermitAll
public class DashboardView extends VerticalLayout {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final AssetService assetService;
    private final ExchangeRateService exchangeRateService;
    private final User currentUser;

    private final FlexLayout metricsLayout = new FlexLayout();
    private final Div assetPerformanceLayout = new Div();
    private final Div accountsLayout = new Div();
    private final FlexLayout chartsLayout = new FlexLayout();
    private final Div distributionContainer = new Div();
    private final Div timeChartContainer = new Div();

    // Cached data for reuse
    private Map<Asset, AssetPerformanceData> assetPerformanceMap;

    public DashboardView(AccountService accountService, UserService userService,
                         TransactionService transactionService, AssetService assetService,
                         ExchangeRateService exchangeRateService, SecurityUtils securityUtils) {
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.assetService = assetService;
        this.exchangeRateService = exchangeRateService;

        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        this.currentUser = userService.findByUsername(username);

        addClassName("dashboard-view");
        setWidthFull();
        setPadding(false);
        setSpacing(false);
        setAlignItems(Alignment.CENTER);
        getStyle().set("background-color", "var(--lumo-contrast-5pct)");

        setupUI();
    }

    private void setupUI() {
        List<Account> allAccounts = accountService.getAccountsByUser(currentUser);
        // Filter out accounts excluded from summary for dashboard display
        List<Account> accounts = allAccounts.stream()
                .filter(a -> !a.isExcludeFromSummary())
                .collect(java.util.stream.Collectors.toList());
        List<Transaction> userTransactions = transactionService.getTransactionsByUser(currentUser);

        // Calculate asset performance data first (used by metrics and asset list)
        calculateAssetPerformance(userTransactions);

        Div container = new Div();
        container.setWidthFull();
        container.setMaxWidth("1200px");
        container.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-l)")
                .set("padding", "var(--lumo-space-m)")
                .set("box-sizing", "border-box");

        createMetrics(accounts);
        createAssetPerformanceSection();
        createAccountList(accounts);
        createCharts(userTransactions);

        container.add(metricsLayout, assetPerformanceLayout, accountsLayout, chartsLayout);
        add(container);
    }

    /**
     * Calculate performance data for each asset based on transactions
     */
    private void calculateAssetPerformance(List<Transaction> transactions) {
        assetPerformanceMap = new LinkedHashMap<>();

        // Group transactions by asset - sum units and total cost
        Map<Asset, List<Transaction>> assetTransactions = transactions.stream()
                .filter(t -> t.getAsset() != null && t.getUnits() != null)
                .filter(t -> t.getToAccount() != null && t.getToAccount().getAccountType() == Account.AccountType.ASSET)
                .collect(Collectors.groupingBy(Transaction::getAsset));

        for (Map.Entry<Asset, List<Transaction>> entry : assetTransactions.entrySet()) {
            Asset asset = entry.getKey();
            List<Transaction> assetTxs = entry.getValue();

            BigDecimal totalUnits = BigDecimal.ZERO;
            BigDecimal totalCost = BigDecimal.ZERO;

            for (Transaction t : assetTxs) {
                totalUnits = totalUnits.add(t.getUnits());
                totalCost = totalCost.add(t.getAmount());
            }

            BigDecimal currentPrice = asset.getCurrentPrice() != null ? asset.getCurrentPrice() : BigDecimal.ZERO;
            BigDecimal currentValue = totalUnits.multiply(currentPrice);

            // Convert to user's default currency
            BigDecimal currentValueConverted = exchangeRateService.convert(currentValue, asset.getCurrency(), currentUser.getDefaultCurrency());
            BigDecimal totalCostConverted = totalCost; // Already in user's currency from transaction

            BigDecimal gainLoss = currentValueConverted.subtract(totalCostConverted);
            BigDecimal gainLossPercent = totalCostConverted.compareTo(BigDecimal.ZERO) > 0
                    ? gainLoss.divide(totalCostConverted, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            assetPerformanceMap.put(asset, new AssetPerformanceData(
                    totalUnits, totalCostConverted, currentValueConverted, currentPrice, gainLoss, gainLossPercent
            ));
        }
    }

    private void createMetrics(List<Account> accounts) {
        metricsLayout.setWidthFull();
        metricsLayout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        metricsLayout.getStyle().set("gap", "var(--lumo-space-m)");

        // Available Cash: Sum of all NON-ASSET accounts
        BigDecimal availableCash = accounts.stream()
                .filter(a -> a.getAccountType() != Account.AccountType.ASSET)
                .map(a -> exchangeRateService.convert(a.getBalance(), a.getCurrency(), currentUser.getDefaultCurrency()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Portfolio Value: Sum of current market values from asset performance
        BigDecimal portfolioValue = assetPerformanceMap.values().stream()
                .map(AssetPerformanceData::currentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // If no asset transactions, fallback to asset account balances
        if (portfolioValue.compareTo(BigDecimal.ZERO) == 0) {
            portfolioValue = accounts.stream()
                    .filter(a -> a.getAccountType() == Account.AccountType.ASSET)
                    .map(a -> exchangeRateService.convert(a.getBalance(), a.getCurrency(), currentUser.getDefaultCurrency()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // Total Net Worth
        BigDecimal netWorth = availableCash.add(portfolioValue);

        metricsLayout.add(
                createMetricCard(getTranslation("dashboard.available_cash"), availableCash, "linear-gradient(135deg, #2ecc71 0%, #27ae60 100%)"),
                createMetricCard(getTranslation("dashboard.portfolio_value"), portfolioValue, "linear-gradient(135deg, #3498db 0%, #2980b9 100%)"),
                createMetricCard(getTranslation("dashboard.net_worth"), netWorth, "linear-gradient(135deg, #9b59b6 0%, #8e44ad 100%)")
        );
    }

    /**
     * Create the asset performance section showing each asset with gain/loss
     */
    private void createAssetPerformanceSection() {
        assetPerformanceLayout.setWidthFull();

        if (assetPerformanceMap.isEmpty()) {
            return; // No assets to display
        }

        Div card = createCardContainer();
        Div content = new Div();
        content.getStyle().set("padding", "var(--lumo-space-m)");
        content.add(new H4(getTranslation("dashboard.asset_performance")));

        // Header row
        HorizontalLayout headerRow = new HorizontalLayout();
        headerRow.setWidthFull();
        headerRow.getStyle()
                .set("padding", "10px 0")
                .set("border-bottom", "2px solid var(--lumo-contrast-10pct)")
                .set("font-weight", "bold")
                .set("font-size", "12px")
                .set("color", "var(--lumo-secondary-text-color)");

        Span assetHeader = new Span(getTranslation("dashboard.asset"));
        assetHeader.getStyle().set("flex", "2");
        Span unitsHeader = new Span(getTranslation("dashboard.units"));
        unitsHeader.getStyle().set("flex", "1").set("text-align", "right");
        Span costHeader = new Span(getTranslation("dashboard.cost_basis"));
        costHeader.getStyle().set("flex", "1.5").set("text-align", "right");
        Span valueHeader = new Span(getTranslation("dashboard.current_value"));
        valueHeader.getStyle().set("flex", "1.5").set("text-align", "right");
        Span gainHeader = new Span(getTranslation("dashboard.gain_loss"));
        gainHeader.getStyle().set("flex", "1.5").set("text-align", "right");

        headerRow.add(assetHeader, unitsHeader, costHeader, valueHeader, gainHeader);
        content.add(headerRow);

        // Total gain/loss for summary
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;

        // Asset rows
        for (Map.Entry<Asset, AssetPerformanceData> entry : assetPerformanceMap.entrySet()) {
            Asset asset = entry.getKey();
            AssetPerformanceData data = entry.getValue();

            totalCost = totalCost.add(data.totalCost());
            totalValue = totalValue.add(data.currentValue());

            HorizontalLayout row = new HorizontalLayout();
            row.setWidthFull();
            row.setAlignItems(Alignment.CENTER);
            row.getStyle()
                    .set("padding", "12px 0")
                    .set("border-bottom", "1px solid var(--lumo-contrast-5pct)");

            // Asset name and symbol
            VerticalLayout assetInfo = new VerticalLayout();
            assetInfo.setPadding(false);
            assetInfo.setSpacing(false);
            assetInfo.getStyle().set("flex", "2");
            Span assetName = new Span(asset.getName());
            assetName.getStyle().set("font-weight", "500");
            Span assetSymbol = new Span(asset.getSymbol());
            assetSymbol.getStyle().set("font-size", "12px").set("color", "var(--lumo-secondary-text-color)");
            assetInfo.add(assetName, assetSymbol);

            // Units
            Span unitsSpan = new Span(data.totalUnits().stripTrailingZeros().toPlainString());

            unitsSpan.getStyle().set("flex", "1").set("text-align", "right").set("font-size", "14px");

            // Cost basis
            Span costSpan = new Span(formatCurrency(data.totalCost(), currentUser.getDefaultCurrency()));
            costSpan.getStyle().set("flex", "1.5").set("text-align", "right").set("font-size", "14px");

            // Current value
            Span valueSpan = new Span(formatCurrency(data.currentValue(), currentUser.getDefaultCurrency()));
            valueSpan.getStyle().set("flex", "1.5").set("text-align", "right").set("font-size", "14px");

            // Gain/Loss with color and percentage
            Div gainLossDiv = createGainLossDisplay(data.gainLoss(), data.gainLossPercent());
            gainLossDiv.getStyle().set("flex", "1.5").set("text-align", "right");

            row.add(assetInfo, unitsSpan, costSpan, valueSpan, gainLossDiv);
            content.add(row);
        }

        // Total row
        BigDecimal totalGainLoss = totalValue.subtract(totalCost);
        BigDecimal totalGainLossPercent = totalCost.compareTo(BigDecimal.ZERO) > 0
                ? totalGainLoss.divide(totalCost, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        HorizontalLayout totalRow = new HorizontalLayout();
        totalRow.setWidthFull();
        totalRow.setAlignItems(Alignment.CENTER);
        totalRow.getStyle()
                .set("padding", "15px 0")
                .set("margin-top", "10px")
                .set("border-top", "2px solid var(--lumo-contrast-10pct)")
                .set("font-weight", "bold");

        Span totalLabel = new Span(getTranslation("dashboard.total_portfolio"));
        totalLabel.getStyle().set("flex", "2");
        Span emptyUnits = new Span("");
        emptyUnits.getStyle().set("flex", "1");
        Span totalCostSpan = new Span(formatCurrency(totalCost, currentUser.getDefaultCurrency()));
        totalCostSpan.getStyle().set("flex", "1.5").set("text-align", "right");
        Span totalValueSpan = new Span(formatCurrency(totalValue, currentUser.getDefaultCurrency()));
        totalValueSpan.getStyle().set("flex", "1.5").set("text-align", "right");
        Div totalGainLossDiv = createGainLossDisplay(totalGainLoss, totalGainLossPercent);
        totalGainLossDiv.getStyle().set("flex", "1.5").set("text-align", "right");

        totalRow.add(totalLabel, emptyUnits, totalCostSpan, totalValueSpan, totalGainLossDiv);
        content.add(totalRow);

        card.add(content);
        assetPerformanceLayout.add(card);
    }

    /**
     * Create gain/loss display with color coding and icon
     */
    private Div createGainLossDisplay(BigDecimal gainLoss, BigDecimal percent) {
        Div container = new Div();
        container.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", "flex-end");

        boolean isPositive = gainLoss.compareTo(BigDecimal.ZERO) >= 0;
        String color = isPositive ? "var(--lumo-success-color)" : "var(--lumo-error-color)";

        HorizontalLayout mainLine = new HorizontalLayout();
        mainLine.setSpacing(false);
        mainLine.setAlignItems(Alignment.CENTER);
        mainLine.getStyle().set("gap", "4px");

        Icon icon = isPositive ? VaadinIcon.ARROW_UP.create() : VaadinIcon.ARROW_DOWN.create();
        icon.setSize("14px");
        icon.getStyle().set("color", color);

        Span amountSpan = new Span(formatCurrency(gainLoss.abs(), currentUser.getDefaultCurrency()));
        amountSpan.getStyle().set("color", color).set("font-weight", "500");

        mainLine.add(icon, amountSpan);

        Span percentSpan = new Span((isPositive ? "+" : "") + percent.setScale(2, RoundingMode.HALF_UP) + "%");
        percentSpan.getStyle()
                .set("font-size", "12px")
                .set("color", color);

        container.add(mainLine, percentSpan);
        return container;
    }

    private void createCharts(List<Transaction> transactions) {
        chartsLayout.setWidthFull();
        chartsLayout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        chartsLayout.getStyle().set("gap", "var(--lumo-space-m)");

        // --- Time Chart Section ---
        Div timeChartCard = createCardContainer();
        timeChartCard.getStyle().set("flex", "1 1 450px").set("min-width", "0");
        Div timeChartContent = new Div();
        timeChartContent.getStyle().set("padding", "var(--lumo-space-m)");

        HorizontalLayout timeHeader = new HorizontalLayout(new H4(getTranslation("dashboard.cash_flow", currentUser.getDefaultCurrency())));
        timeHeader.setWidthFull();
        timeHeader.setAlignItems(Alignment.CENTER);

        Select<String> timeRange = new Select<>();
        timeRange.setItems("Daily", "Weekly", "Monthly", "Yearly");
        timeRange.setValue("Monthly");
        timeRange.getStyle().set("margin-left", "auto");
        timeHeader.add(timeRange);
        timeChartContent.add(timeHeader);

        HorizontalLayout legend = new HorizontalLayout();
        legend.setSpacing(true);
        legend.add(createLegendItem(getTranslation("dashboard.revenue"), "var(--lumo-success-color)"),
                createLegendItem(getTranslation("dashboard.spending"), "var(--lumo-error-color)"));
        timeChartContent.add(legend);

        timeChartContainer.setWidthFull();
        timeChartContainer.setHeight("250px");
        timeChartContainer.getStyle()
                .set("overflow-x", "auto")
                .set("display", "flex")
                .set("align-items", "flex-end")
                .set("padding-bottom", "var(--lumo-space-s)");

        renderTimeChart(timeChartContainer, transactions, timeRange.getValue());
        timeRange.addValueChangeListener(e -> {
            renderTimeChart(timeChartContainer, transactions, e.getValue());
            renderDistributionChart(distributionContainer, transactions, e.getValue());
        });

        timeChartContent.add(timeChartContainer);
        timeChartCard.add(timeChartContent);

        // --- Distribution Section ---
        Div distCard = createCardContainer();
        distCard.getStyle().set("flex", "1 1 350px").set("min-width", "0");
        Div totalChartContent = new Div();
        totalChartContent.getStyle().set("padding", "var(--lumo-space-m)");
        totalChartContent.add(new H4(getTranslation("dashboard.top_spending", currentUser.getDefaultCurrency())));

        distributionContainer.setWidthFull();
        distributionContainer.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "12px").set("margin-top", "20px");

        renderDistributionChart(distributionContainer, transactions, timeRange.getValue());

        totalChartContent.add(distributionContainer);
        distCard.add(totalChartContent);

        chartsLayout.add(timeChartCard, distCard);
    }

    private void renderTimeChart(Div container, List<Transaction> transactions, String range) {
        container.removeAll();
        FlexLayout bars = new FlexLayout();
        bars.setWidthFull();
        bars.setHeightFull();
        bars.setAlignItems(Alignment.BASELINE);
        bars.setJustifyContentMode(FlexComponent.JustifyContentMode.AROUND);
        bars.getStyle().set("border-bottom", "1px solid var(--lumo-contrast-20pct)");

        Map<String, BigDecimal[]> chartData = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        Locale userLocale = Locale.forLanguageTag(currentUser.getLocale());

        // We show the current unit as the primary bar
        if ("Daily".equals(range)) {
            chartData.put(today.format(DateTimeFormatter.ofPattern("dd.MM")), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        } else if ("Weekly".equals(range)) {
            String weekLabel = "W" + today.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            chartData.put(weekLabel, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        } else if ("Monthly".equals(range)) {
            String monthName = today.getMonth().getDisplayName(TextStyle.SHORT, userLocale);
            chartData.put(monthName, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        } else {
            chartData.put(String.valueOf(today.getYear()), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }

        for (Transaction t : transactions) {
            String label = null;
            LocalDate td = t.getTransactionDate().toLocalDate();

            if ("Daily".equals(range) && td.isEqual(today)) {
                label = td.format(DateTimeFormatter.ofPattern("dd.MM"));
            } else if ("Weekly".equals(range) &&
                    td.getYear() == today.getYear() &&
                    td.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR) == today.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)) {
                label = "W" + td.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            } else if ("Monthly".equals(range) && td.getYear() == today.getYear() && td.getMonth() == today.getMonth()) {
                label = td.getMonth().getDisplayName(TextStyle.SHORT, userLocale);
            } else if ("Yearly".equals(range) && td.getYear() == today.getYear()) {
                label = String.valueOf(today.getYear());
            }

            if (label != null && chartData.containsKey(label)) {
                BigDecimal[] values = chartData.get(label);
                Account acc = t.getType() == Transaction.TransactionType.INCOME ? t.getToAccount() : t.getFromAccount();
                if (acc != null) {
                    BigDecimal converted = exchangeRateService.convert(t.getAmount(), acc.getCurrency(), currentUser.getDefaultCurrency());
                    if (t.getType() == Transaction.TransactionType.INCOME)
                        values[0] = values[0].add(converted);
                    else if (t.getType() == Transaction.TransactionType.EXPENSE)
                        values[1] = values[1].add(converted);
                }
            }
        }

        BigDecimal max = BigDecimal.valueOf(1);
        for (BigDecimal[] v : chartData.values()) max = max.max(v[0]).max(v[1]);

        for (Map.Entry<String, BigDecimal[]> entry : chartData.entrySet()) {
            Div barStack = new Div();
            barStack.getStyle().set("display", "flex").set("flex-direction", "column").set("align-items", "center").set("flex", "1 1 0");
            HorizontalLayout doubleBar = new HorizontalLayout();
            doubleBar.setAlignItems(Alignment.BASELINE);
            doubleBar.getStyle().set("gap", "2px");
            doubleBar.add(createAnimatedBar(entry.getValue()[0], max, "var(--lumo-success-color)"));
            doubleBar.add(createAnimatedBar(entry.getValue()[1], max, "var(--lumo-error-color)"));
            barStack.add(doubleBar);
            Span l = new Span(entry.getKey());
            l.getStyle().set("font-size", "10px").set("color", "var(--lumo-secondary-text-color)");
            barStack.add(l);
            bars.add(barStack);
        }
        container.add(bars);
    }

    private Div createAnimatedBar(BigDecimal value, BigDecimal max, String color) {
        Div bar = new Div();
        bar.setWidth("8px");
        double height = value.multiply(BigDecimal.valueOf(180)).divide(max, 2, RoundingMode.HALF_UP).doubleValue();
        bar.setHeight(Math.max(1, height) + "px");
        bar.getStyle().set("background-color", color).set("border-radius", "2px 2px 0 0");
        return bar;
    }

    private void renderDistributionChart(Div container, List<Transaction> transactions, String range) {
        container.removeAll();
        LocalDate today = LocalDate.now();

        Map<String, BigDecimal> data = transactions.stream()
                .filter(t -> t.getCategory() != null && t.getType() == Transaction.TransactionType.EXPENSE && t.getFromAccount() != null)
                .filter(t -> {
                    LocalDate td = t.getTransactionDate().toLocalDate();
                    if ("Daily".equals(range)) return td.isEqual(today);
                    if ("Weekly".equals(range))
                        return td.getYear() == today.getYear() && td.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR) == today.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                    if ("Monthly".equals(range))
                        return td.getYear() == today.getYear() && td.getMonth() == today.getMonth();
                    return td.getYear() == today.getYear(); // Yearly
                })
                .collect(Collectors.groupingBy(t -> t.getCategory().getName(),
                        Collectors.reducing(BigDecimal.ZERO,
                                t -> exchangeRateService.convert(t.getAmount(), t.getFromAccount().getCurrency(), currentUser.getDefaultCurrency()),
                                BigDecimal::add)));

        BigDecimal total = data.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        data.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> {
                    Div item = new Div();
                    item.getStyle().set("display", "flex").set("flex-direction", "column").set("width", "100%");
                    HorizontalLayout info = new HorizontalLayout(new Span(entry.getKey()), new Span(formatCurrency(entry.getValue(), currentUser.getDefaultCurrency())));
                    info.setWidthFull();
                    info.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
                    info.getStyle().set("font-size", "13px");
                    Div progressBg = new Div();
                    progressBg.setWidthFull();
                    progressBg.getStyle().set("background", "var(--lumo-contrast-10pct)").set("border-radius", "4px").set("height", "6px").set("overflow", "hidden");
                    Div bar = new Div();
                    double percentage = total.compareTo(BigDecimal.ZERO) > 0 ? entry.getValue().divide(total, 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0;
                    bar.setWidth(percentage + "%");
                    bar.setHeight("100%");
                    bar.getStyle().set("background", "var(--lumo-primary-color)");
                    progressBg.add(bar);
                    item.add(info, progressBg);
                    container.add(item);
                });
    }

    private void createAccountList(List<Account> accounts) {
        accountsLayout.setWidthFull();
        Div card = createCardContainer();
        Div content = new Div();
        content.getStyle().set("padding", "var(--lumo-space-m)");
        content.add(new H4(getTranslation("dashboard.accounts_overview", currentUser.getDefaultCurrency())));

        Map<String, List<Account>> grouped = accounts.stream()
                .collect(Collectors.groupingBy(a -> a.getAccountGroup() != null ? a.getAccountGroup() : "Other"));

        BigDecimal grandTotal = BigDecimal.ZERO;

        for (Map.Entry<String, List<Account>> entry : grouped.entrySet()) {
            Span groupHeader = new Span(entry.getKey().toUpperCase());
            groupHeader.getStyle().set("font-size", "11px").set("font-weight", "bold").set("color", "var(--lumo-primary-color)").set("margin-top", "15px").set("display", "block");
            content.add(groupHeader);

            BigDecimal groupSum = BigDecimal.ZERO;
            for (Account acc : entry.getValue()) {
                BigDecimal converted = exchangeRateService.convert(acc.getBalance(), acc.getCurrency(), currentUser.getDefaultCurrency());
                HorizontalLayout row = new HorizontalLayout(new Span(acc.getAccountName()), new Span(formatCurrency(converted, currentUser.getDefaultCurrency())));
                row.setWidthFull();
                row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
                row.getStyle().set("padding", "8px 0").set("border-bottom", "1px solid var(--lumo-contrast-5pct)").set("font-size", "14px");
                content.add(row);
                groupSum = groupSum.add(converted);
            }

            HorizontalLayout groupTotalRow = new HorizontalLayout(new Span("Total " + entry.getKey()), new Span(formatCurrency(groupSum, currentUser.getDefaultCurrency())));
            groupTotalRow.setWidthFull();
            groupTotalRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
            groupTotalRow.getStyle().set("font-weight", "bold").set("padding", "10px 0").set("font-size", "13px").set("color", "var(--lumo-primary-text-color)");
            content.add(groupTotalRow);
            grandTotal = grandTotal.add(groupSum);
        }

        HorizontalLayout grandTotalRow = new HorizontalLayout(new Span("Grand Total"), new Span(formatCurrency(grandTotal, currentUser.getDefaultCurrency())));
        grandTotalRow.setWidthFull();
        grandTotalRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        grandTotalRow.getStyle().set("font-weight", "800").set("font-size", "16px").set("padding-top", "20px").set("border-top", "2px solid var(--lumo-contrast-10pct)").set("margin-top", "10px");
        content.add(grandTotalRow);

        card.add(content);
        accountsLayout.add(card);
    }

    private Div createMetricCard(String title, BigDecimal amount, String background) {
        Div card = new Div();
        card.getStyle()
                .set("flex", "1 1 280px")
                .set("min-width", "250px")
                .set("background", background)
                .set("color", "white")
                .set("border-radius", "16px")
                .set("padding", "20px")
                .set("box-shadow", "var(--lumo-box-shadow-s)");

        Span t = new Span(title);
        t.getStyle()
                .set("font-size", "13px")
                .set("opacity", "0.9")
                .set("display", "block")
                .set("color", "white");

        H4 val = new H4(formatCurrency(amount, currentUser.getDefaultCurrency()));
        val.getStyle()
                .set("margin", "5px 0 0 0").set("font-size", "20px").set("color", "white");

        card.add(t, val);
        return card;
    }

    private Div createCardContainer() {
        Div card = new Div();
        card.setWidthFull();
        card.getStyle().set("background-color", "var(--lumo-base-color)")
                .set("border-radius", "16px")
                .set("box-shadow", "var(--lumo-box-shadow-s)")
                .set("overflow", "hidden");
        return card;
    }

    private HorizontalLayout createLegendItem(String label, String color) {
        HorizontalLayout item = new HorizontalLayout();
        item.setAlignItems(Alignment.CENTER);
        item.setSpacing(true);
        Div dot = new Div();
        dot.setWidth("8px");
        dot.setHeight("8px");
        dot.getStyle().set("background", color).set("border-radius", "50%");
        Span s = new Span(label);
        s.getStyle().set("font-size", "11px").set("color", "var(--lumo-secondary-text-color)");
        item.add(dot, s);
        return item;
    }

    @Override
    public Locale getLocale() {
        return Locale.forLanguageTag(currentUser.getLocale());
    }

    private String formatCurrency(BigDecimal amount, String currencyCode) {
        if (amount == null) return "";
        NumberFormat formatter = NumberFormat.getCurrencyInstance(getLocale());
        try {
            java.util.Currency currency = java.util.Currency.getInstance(currencyCode);
            formatter.setCurrency(currency);
        } catch (Exception e) {
        }
        return formatter.format(amount);
    }

    /**
     * Data class for asset performance calculations
     */
    private record AssetPerformanceData(
            BigDecimal totalUnits,
            BigDecimal totalCost,
            BigDecimal currentValue,
            BigDecimal currentPrice,
            BigDecimal gainLoss,
            BigDecimal gainLossPercent
    ) {
    }
}
