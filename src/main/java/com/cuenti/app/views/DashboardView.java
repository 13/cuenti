package com.cuenti.app.views;

import com.cuenti.app.model.*;
import com.cuenti.app.security.SecurityUtils;
import com.cuenti.app.service.AccountService;
import com.cuenti.app.service.AssetService;
import com.cuenti.app.service.ExchangeRateService;
import com.cuenti.app.service.TransactionService;
import com.cuenti.app.service.UserService;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.HasDynamicTitle;
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
@PermitAll
public class DashboardView extends VerticalLayout implements HasDynamicTitle {

    @Override
    public String getPageTitle() {
        return getTranslation("dashboard.title") + " | " + getTranslation("app.name");
    }


    private final AccountService accountService;
    private final TransactionService transactionService;
    private final AssetService assetService;
    private final ExchangeRateService exchangeRateService;
    private final com.cuenti.app.service.BudgetService budgetService;
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
                         ExchangeRateService exchangeRateService, SecurityUtils securityUtils,
                         com.cuenti.app.service.BudgetService budgetService) {
        this.budgetService = budgetService;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.assetService = assetService;
        this.exchangeRateService = exchangeRateService;

        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        this.currentUser = userService.findByUsername(username);

        addClassNames("dashboard-view", "page-scroll");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        setAlignItems(Alignment.CENTER);

        // Scaffold with skeletons; data loads async after attach (@Push)
        container.addClassName("page-container");
        container.add(new com.cuenti.app.views.components.PageHeader(getTranslation("dashboard.title")));
        for (int i = 0; i < 4; i++) {
            Div skeleton = new Div();
            skeleton.addClassName("skeleton");
            skeleton.setMinHeight(i == 0 ? "90px" : "220px");
            skeletons.add(skeleton);
            container.add(skeleton);
        }
        add(container);
    }

    private final Div container = new Div();
    private final java.util.List<Div> skeletons = new ArrayList<>();
    private boolean dataLoaded;

    @Override
    protected void onAttach(com.vaadin.flow.component.AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        if (dataLoaded) {
            return;
        }
        dataLoaded = true;
        com.vaadin.flow.component.UI ui = attachEvent.getUI();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            // heavy reads off the UI thread; entities are join-fetched
            List<Account> fetchedAccounts = accountService.getAccountsByUser(currentUser);
            List<Transaction> fetchedTransactions = transactionService.getTransactionsByUser(currentUser);
            ui.access(() -> {
                skeletons.forEach(container::remove);
                setupUI(fetchedAccounts, fetchedTransactions);
            });
        });
    }

    private void setupUI(List<Account> allAccounts, List<Transaction> userTransactions) {
        // Filter out accounts excluded from summary for dashboard display
        List<Account> accounts = allAccounts.stream()
                .filter(a -> !a.isExcludeFromSummary())
                .collect(java.util.stream.Collectors.toList());

        // Calculate asset performance data first (used by metrics and asset list)
        calculateAssetPerformance(userTransactions);



        createMetrics(accounts);
        createAssetPerformanceSection();
        createAccountList(accounts);
        createCharts(userTransactions);
        Div budgetsSection = createBudgetsSection();

        container.add(metricsLayout, assetPerformanceLayout, chartsLayout, budgetsSection, accountsLayout);
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
        metricsLayout.getStyle().set("gap", "var(--vaadin-gap-m)");

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
                createMetricCard(getTranslation("dashboard.available_cash"),  availableCash,  VaadinIcon.WALLET),
                createMetricCard(getTranslation("dashboard.portfolio_value"), portfolioValue, VaadinIcon.STOCK),
                createMetricCard(getTranslation("dashboard.net_worth"),       netWorth,       VaadinIcon.TRENDING_UP)
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
        card.add(createSectionHeader("dashboard.asset_performance", VaadinIcon.STOCK));

        // Column header
        HorizontalLayout headerRow = new HorizontalLayout();
        headerRow.setWidthFull();
        headerRow.getStyle()
                .set("padding", "var(--vaadin-gap-xs) var(--vaadin-gap-s)")
                .set("margin-bottom", "2px");
        for (String[] hdr : new String[][]{
                {getTranslation("dashboard.asset"),         "2",   "left"},
                {getTranslation("dashboard.units"),         "1",   "right"},
                {getTranslation("dashboard.cost_basis"),    "1.5", "right"},
                {getTranslation("dashboard.current_value"), "1.5", "right"},
                {getTranslation("dashboard.gain_loss"),     "1.5", "right"}}) {
            Span h = new Span(hdr[0].toUpperCase());
            h.getStyle()
                    .set("flex", hdr[1]).set("text-align", hdr[2])
                    .set("font-size", "10px").set("font-weight", "700")
                    .set("letter-spacing", "0.07em").set("color", "var(--vaadin-text-color-secondary)");
            headerRow.add(h);
        }
        Div contentScroll = new Div();
        contentScroll.addClassName("scroll-x");
        contentScroll.setWidthFull();
        Div content = new Div(headerRow);
        content.setWidthFull();

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
            row.addClassName("hover-row");
            row.getStyle()
                    .set("padding", "var(--vaadin-gap-s) var(--vaadin-gap-s)")
                    .set("border-radius", "8px")
                    .set("border-bottom", "1px solid var(--cuenti-divider)");

            // Asset name + symbol stacked
            Div assetInfo = new Div();
            assetInfo.getStyle().set("flex", "2").set("display", "flex")
                    .set("flex-direction", "column").set("gap", "2px");
            Span assetName = new Span(asset.getName());
            assetName.getStyle().set("font-weight", "600").set("font-size", "var(--aura-font-size-s)");
            Span assetSymbol = new Span(asset.getSymbol());
            assetSymbol.getStyle()
                    .set("font-size", "10px").set("font-weight", "700").set("letter-spacing", "0.05em")
                    .set("padding", "1px 5px").set("border-radius", "4px")
                    .set("background", "var(--vaadin-background-container-strong)")
                    .set("color", "var(--vaadin-text-color-secondary)").set("width", "fit-content");
            assetInfo.add(assetName, assetSymbol);

            Span unitsSpan = new Span(data.totalUnits().stripTrailingZeros().toPlainString());
            unitsSpan.getStyle().set("flex", "1").set("text-align", "right")
                    .set("font-size", "var(--aura-font-size-s)").set("color", "var(--vaadin-text-color-secondary)");

            Span costSpan = new Span(formatCurrency(data.totalCost(), currentUser.getDefaultCurrency()));
            costSpan.getStyle().set("flex", "1.5").set("text-align", "right").set("font-size", "var(--aura-font-size-s)");

            Span valueSpan = new Span(formatCurrency(data.currentValue(), currentUser.getDefaultCurrency()));
            valueSpan.getStyle().set("flex", "1.5").set("text-align", "right")
                    .set("font-weight", "600").set("font-size", "var(--aura-font-size-s)");

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
                .set("padding", "var(--vaadin-gap-s) var(--vaadin-gap-s)")
                .set("margin-top", "var(--vaadin-gap-xs)")
                .set("border-top", "2px solid var(--vaadin-border-color-secondary)")
                .set("background", "var(--vaadin-background-container)")
                .set("border-radius", "0 0 8px 8px");

        Span totalLabel = new Span(getTranslation("dashboard.total_portfolio").toUpperCase());
        totalLabel.getStyle().set("flex", "2").set("font-size", "10px")
                .set("font-weight", "700").set("letter-spacing", "0.07em");
        Span emptyUnits = new Span(""); emptyUnits.getStyle().set("flex", "1");
        Span totalCostSpan = new Span(formatCurrency(totalCost, currentUser.getDefaultCurrency()));
        totalCostSpan.getStyle().set("flex", "1.5").set("text-align", "right")
                .set("font-weight", "700").set("font-size", "var(--aura-font-size-s)");
        Span totalValueSpan = new Span(formatCurrency(totalValue, currentUser.getDefaultCurrency()));
        totalValueSpan.getStyle().set("flex", "1.5").set("text-align", "right")
                .set("font-weight", "700").set("font-size", "var(--aura-font-size-s)");
        Div totalGainLossDiv = createGainLossDisplay(totalGainLoss, totalGainLossPercent);
        totalGainLossDiv.getStyle().set("flex", "1.5").set("text-align", "right");

        totalRow.add(totalLabel, emptyUnits, totalCostSpan, totalValueSpan, totalGainLossDiv);
        content.add(totalRow);
        contentScroll.add(content);
        card.add(contentScroll);
        assetPerformanceLayout.add(card);
    }

    /**
     * Create gain/loss display with color coding and icon
     */
    private Div createGainLossDisplay(BigDecimal gainLoss, BigDecimal percent) {
        boolean isPositive = gainLoss.compareTo(BigDecimal.ZERO) >= 0;
        String color = isPositive ? "var(--aura-green)" : "var(--aura-red)";
        String sign   = isPositive ? "+" : "";

        Div container = new Div();
        container.getStyle()
                .set("display", "flex").set("flex-direction", "column").set("align-items", "flex-end")
                .set("gap", "3px");

        Span amountSpan = new Span(sign + formatCurrency(gainLoss.abs(), currentUser.getDefaultCurrency()));
        amountSpan.getStyle()
                .set("font-size", "var(--aura-font-size-s)").set("font-weight", "700").set("color", color);

        Span pctBadge = new Span(sign + percent.setScale(2, RoundingMode.HALF_UP) + "%");
        pctBadge.getStyle()
                .set("font-size", "10px").set("font-weight", "700").set("letter-spacing", "0.04em")
                .set("padding", "1px 6px").set("border-radius", "99px")
                .set("background", color + "1a").set("color", color);

        container.add(amountSpan, pctBadge);
        return container;
    }

    private void createCharts(List<Transaction> transactions) {
        chartsLayout.setWidthFull();
        chartsLayout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        chartsLayout.getStyle().set("gap", "var(--vaadin-gap-m)");

        // ── Cash Flow card ────────────────────────────────────────────
        Div timeChartCard = createCardContainer();
        timeChartCard.getStyle().set("flex", "1 1 450px").set("min-width", "0");
        timeChartCard.add(createSectionHeader("dashboard.cash_flow", VaadinIcon.BAR_CHART, currentUser.getDefaultCurrency()));

        // Range selector + legend in one toolbar row
        Select<String> timeRange = new Select<>();
        timeRange.setItems("daily", "weekly", "monthly", "yearly");
        timeRange.setItemLabelGenerator(this::getTimeRangeLabel);
        timeRange.setValue("monthly");
        timeRange.getStyle().set("min-width", "130px");

        HorizontalLayout chartToolbar = new HorizontalLayout();
        chartToolbar.setWidthFull();
        chartToolbar.setAlignItems(Alignment.CENTER);
        chartToolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        chartToolbar.addClassName("card-toolbar");
        chartToolbar.getStyle().set("margin-bottom", "var(--vaadin-gap-s)");

        HorizontalLayout legend = new HorizontalLayout();
        legend.setSpacing(false);
        legend.getStyle().set("gap", "var(--vaadin-gap-m)");
        legend.add(
            createLegendItem(getTranslation("dashboard.revenue"), "var(--cuenti-chart-income)"),
            createLegendItem(getTranslation("dashboard.spending"), "var(--cuenti-chart-expense)")
        );
        chartToolbar.add(legend, timeRange);
        timeChartCard.add(chartToolbar);

        timeChartContainer.setWidthFull();
        timeChartContainer.getStyle()
                .set("overflow-x", "auto")
                .set("padding-bottom", "var(--vaadin-gap-xs)");
        renderTimeChart(timeChartContainer, transactions, timeRange.getValue());
        timeRange.addValueChangeListener(e -> {
            renderTimeChart(timeChartContainer, transactions, e.getValue());
            renderDistributionChart(distributionContainer, transactions, e.getValue());
        });
        timeChartCard.add(timeChartContainer);

        // ── Top Spending card ─────────────────────────────────────────
        Div distCard = createCardContainer();
        distCard.getStyle().set("flex", "1 1 300px").set("min-width", "0");
        distCard.add(createSectionHeader("dashboard.top_spending", VaadinIcon.PIE_CHART, currentUser.getDefaultCurrency()));

        distributionContainer.setWidthFull();
        distributionContainer.getStyle()
                .set("display", "flex").set("flex-direction", "column").set("gap", "var(--vaadin-gap-s)");
        renderDistributionChart(distributionContainer, transactions, timeRange.getValue());
        distCard.add(distributionContainer);

        chartsLayout.add(timeChartCard, distCard);
    }

    private void renderTimeChart(Div container, List<Transaction> transactions, String range) {
        container.removeAll();

        LocalDate today = LocalDate.now();
        Locale userLocale = Locale.forLanguageTag(currentUser.getLocale());

        // Build ordered bucket labels covering the last N periods
        Map<String, BigDecimal[]> chartData = new LinkedHashMap<>();
        int buckets = switch (range) {
            case "daily"   -> 30;
            case "weekly"  -> 12;
            case "yearly"  -> 5;
            default         -> 12; // monthly
        };

        for (int i = buckets - 1; i >= 0; i--) {
            String label = switch (range) {
                case "daily"  -> today.minusDays(i).format(DateTimeFormatter.ofPattern("dd.MM"));
                case "weekly" -> {
                    LocalDate w = today.minusWeeks(i);
                    yield "W" + w.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                            + "’" + String.valueOf(w.getYear()).substring(2);
                }
                case "yearly" -> String.valueOf(today.minusYears(i).getYear());
                default -> today.minusMonths(i).getMonth().getDisplayName(TextStyle.SHORT, userLocale)
                            + " ’" + String.valueOf(today.minusMonths(i).getYear()).substring(2);
            };
            chartData.put(label, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }

        // Populate from transactions
        for (Transaction t : transactions) {
            LocalDate td = t.getTransactionDate().toLocalDate();
            String label = switch (range) {
                case "daily"  -> td.format(DateTimeFormatter.ofPattern("dd.MM"));
                case "weekly" -> "W" + td.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                                 + "’" + String.valueOf(td.getYear()).substring(2);
                case "yearly" -> String.valueOf(td.getYear());
                default -> td.getMonth().getDisplayName(TextStyle.SHORT, userLocale)
                           + " ’" + String.valueOf(td.getYear()).substring(2);
            };
            if (!chartData.containsKey(label)) continue;
            Account acc = t.getType() == Transaction.TransactionType.INCOME ? t.getToAccount() : t.getFromAccount();
            if (acc == null) continue;
            BigDecimal converted = exchangeRateService.convert(t.getAmount(), acc.getCurrency(), currentUser.getDefaultCurrency());
            BigDecimal[] vals = chartData.get(label);
            if (t.getType() == Transaction.TransactionType.INCOME)       vals[0] = vals[0].add(converted);
            else if (t.getType() == Transaction.TransactionType.EXPENSE)  vals[1] = vals[1].add(converted);
        }

        container.add(new com.cuenti.app.views.components.charts.CashFlowChart(
                chartData, amount -> formatCurrency(amount, currentUser.getDefaultCurrency())));
    }

    private void renderDistributionChart(Div container, List<Transaction> transactions, String range) {
        container.removeAll();
        LocalDate today = LocalDate.now();

        Map<String, BigDecimal> data = transactions.stream()
                .filter(t -> t.getCategory() != null && t.getType() == Transaction.TransactionType.EXPENSE && t.getFromAccount() != null)
                .filter(t -> {
                    LocalDate td = t.getTransactionDate().toLocalDate();
                    if ("daily".equals(range))  return td.isEqual(today);
                    if ("weekly".equals(range))
                        return td.getYear() == today.getYear()
                            && td.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                               == today.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                    if ("monthly".equals(range))
                        return td.getYear() == today.getYear() && td.getMonth() == today.getMonth();
                    return td.getYear() == today.getYear();
                })
                .collect(Collectors.groupingBy(t -> t.getCategory().getName(),
                        Collectors.reducing(BigDecimal.ZERO,
                                t -> exchangeRateService.convert(t.getAmount(), t.getFromAccount().getCurrency(), currentUser.getDefaultCurrency()),
                                BigDecimal::add)));

        if (data.isEmpty()) {
            Span empty = new Span(getTranslation("dashboard.no_spending"));
            empty.getStyle().set("font-size", "var(--aura-font-size-s)")
                    .set("color", "var(--vaadin-text-color-secondary)");
            container.add(empty);
            return;
        }

        BigDecimal total = data.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        // Collect top 5 first
        List<Map.Entry<String, BigDecimal>> top5 = data.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());

        // Donut with the same validated categorical palette as the list dots
        List<com.cuenti.app.views.components.charts.DonutChart.Slice> slices = top5.stream()
                .map(e -> new com.cuenti.app.views.components.charts.DonutChart.Slice(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        com.cuenti.app.views.components.charts.DonutChart donut =
                new com.cuenti.app.views.components.charts.DonutChart(slices, total,
                        getTranslation("dashboard.top_spending"),
                        amount -> formatCurrency(amount, currentUser.getDefaultCurrency()));
        Div donutWrap = new Div(donut);
        donutWrap.getStyle().set("display", "flex").set("justify-content", "center")
                .set("padding", "var(--vaadin-gap-s) 0");
        container.add(donutWrap);

        String[] COLORS = {
            "var(--cuenti-chart-cat-1)",
            "var(--cuenti-chart-cat-2)",
            "var(--cuenti-chart-cat-3)",
            "var(--cuenti-chart-cat-4)",
            "var(--cuenti-chart-cat-5)"
        };

        int[] ci = {0};
        top5.forEach(entry -> {
                    String color = COLORS[ci[0] % COLORS.length];
                    ci[0]++;

                    // Share of total spending — used for both bar width and label
                    double ofTotal = total.compareTo(BigDecimal.ZERO) > 0
                            ? entry.getValue().divide(total, 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0;

                    Span catSpan = new Span(entry.getKey());
                    catSpan.getStyle().set("font-size", "var(--aura-font-size-s)").set("font-weight", "500");

                    Span pctSpan = new Span(String.format("%.0f%%", ofTotal));
                    pctSpan.getStyle().set("font-size", "var(--aura-font-size-xs)")
                            .set("color", "var(--vaadin-text-color-secondary)");

                    Span amtSpan = new Span(formatCurrency(entry.getValue(), currentUser.getDefaultCurrency()));
                    amtSpan.getStyle().set("font-size", "var(--aura-font-size-s)").set("font-weight", "700")
                            .set("color", "var(--vaadin-text-color)");

                    Div dot = new Div();
                    dot.getStyle().set("width", "10px").set("height", "10px")
                            .set("border-radius", "3px").set("background", color)
                            .set("flex-shrink", "0").set("align-self", "center");

                    HorizontalLayout info = new HorizontalLayout();
                    info.setWidthFull();
                    info.setAlignItems(Alignment.BASELINE);
                    info.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
                    info.getStyle().set("gap", "var(--vaadin-gap-s)");

                    HorizontalLayout left = new HorizontalLayout(dot, catSpan, pctSpan);
                    left.setAlignItems(Alignment.BASELINE);
                    left.setSpacing(false);
                    left.getStyle().set("gap", "var(--vaadin-gap-xs)");
                    info.add(left, amtSpan);

                    Div progressBg = new Div();
                    progressBg.setWidthFull();
                    progressBg.getStyle()
                            .set("background", "var(--vaadin-background-container)")
                            .set("border-radius", "6px").set("height", "8px").set("overflow", "hidden");
                    Div bar = new Div();
                    // Bar width = actual share of total spending (honest representation)
                    bar.setWidth(ofTotal + "%");
                    bar.setHeight("100%");
                    bar.getStyle()
                            .set("background", color)
                            .set("border-radius", "6px")
                            .set("opacity", "0.85");
                    progressBg.add(bar);

                    Div item = new Div(info, progressBg);
                    item.setWidthFull();
                    item.addClassName("hover-row");
                    item.getStyle().set("display", "flex").set("flex-direction", "column")
                            .set("gap", "4px").set("padding", "var(--vaadin-gap-xs) var(--vaadin-gap-xs)")
                            .set("border-radius", "8px");
                    container.add(item);
                });
    }

    /** Budgets overview: top budgets by usage with progress bars (this month). */
    private Div createBudgetsSection() {
        Div wrapper = new Div();
        wrapper.setWidthFull();

        List<com.cuenti.app.model.Budget> budgets = budgetService.getBudgets(currentUser);
        if (budgets.isEmpty()) {
            return wrapper;
        }
        Map<Long, BigDecimal> spent = budgetService.getSpentThisMonth(currentUser);

        Div card = createCardContainer();
        card.add(createSectionHeader("dashboard.budgets", VaadinIcon.PIGGY_BANK));

        budgets.stream()
                .sorted(Comparator.comparingDouble((com.cuenti.app.model.Budget b) -> usage(b, spent)).reversed())
                .limit(6)
                .forEach(b -> {
                    double ratio = usage(b, spent);
                    boolean over = ratio > 1.0;

                    Span name = new Span(b.getCategory().getFullName());
                    name.getStyle().set("font-size", "var(--aura-font-size-s)").set("font-weight", "500");

                    Span figures = new Span(formatCurrency(spent.getOrDefault(b.getCategory().getId(), BigDecimal.ZERO),
                                    currentUser.getDefaultCurrency())
                            + " / " + formatCurrency(b.getMonthlyLimit(), currentUser.getDefaultCurrency()));
                    figures.getStyle().set("font-size", "var(--aura-font-size-xs)")
                            .set("color", over ? "var(--aura-red-text)" : "var(--vaadin-text-color-secondary)")
                            .set("font-variant-numeric", "tabular-nums");

                    HorizontalLayout head = new HorizontalLayout(name, figures);
                    head.setWidthFull();
                    head.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
                    head.setAlignItems(Alignment.BASELINE);

                    com.vaadin.flow.component.progressbar.ProgressBar bar =
                            new com.vaadin.flow.component.progressbar.ProgressBar(0, 1, Math.min(1.0, ratio));
                    bar.setWidthFull();
                    if (over) {
                        bar.getStyle().set("--vaadin-progress-bar-fill-background", "var(--aura-red)");
                    } else if (ratio > 0.85) {
                        bar.getStyle().set("--vaadin-progress-bar-fill-background", "var(--aura-orange)");
                    }

                    Div row = new Div(head, bar);
                    row.getStyle().set("display", "flex").set("flex-direction", "column")
                            .set("gap", "4px").set("padding", "var(--vaadin-gap-xs) 0");
                    card.add(row);
                });

        wrapper.add(card);
        return wrapper;
    }

    private double usage(com.cuenti.app.model.Budget b, Map<Long, BigDecimal> spent) {
        if (b.getMonthlyLimit() == null || b.getMonthlyLimit().compareTo(BigDecimal.ZERO) <= 0) return 0;
        return spent.getOrDefault(b.getCategory().getId(), BigDecimal.ZERO)
                .divide(b.getMonthlyLimit(), 4, RoundingMode.HALF_UP).doubleValue();
    }

    private void createAccountList(List<Account> accounts) {
        accountsLayout.setWidthFull();
        Div card = createCardContainer();
        card.add(createSectionHeader("dashboard.accounts_overview", VaadinIcon.CREDIT_CARD, currentUser.getDefaultCurrency()));

        Map<String, List<Account>> grouped = accounts.stream()
                .collect(Collectors.groupingBy(a -> a.getAccountGroup() != null ? a.getAccountGroup() : "Other"));

        BigDecimal grandTotal = BigDecimal.ZERO;

        for (Map.Entry<String, List<Account>> entry : grouped.entrySet()) {
            // Group label
            Span groupLabel = new Span(entry.getKey().toUpperCase());
            groupLabel.getStyle()
                    .set("font-size", "10px").set("font-weight", "700").set("letter-spacing", "0.08em")
                    .set("color", "var(--aura-accent-color)").set("display", "block")
                    .set("margin-top", "var(--vaadin-gap-m)").set("margin-bottom", "var(--vaadin-gap-xs)");
            card.add(groupLabel);

            BigDecimal groupSum = BigDecimal.ZERO;
            for (Account acc : entry.getValue()) {
                BigDecimal converted = exchangeRateService.convert(acc.getBalance(), acc.getCurrency(), currentUser.getDefaultCurrency());
                boolean negative = converted.compareTo(BigDecimal.ZERO) < 0;

                Span nameSpan = new Span(acc.getAccountName());
                nameSpan.getStyle().set("font-size", "var(--aura-font-size-s)").set("font-weight", "500");

                Span balSpan = new Span(formatCurrency(converted, currentUser.getDefaultCurrency()));
                balSpan.getStyle()
                        .set("font-size", "var(--aura-font-size-s)").set("font-weight", "600")
                        .set("color", negative ? "var(--aura-red)" : "var(--vaadin-text-color)");

                HorizontalLayout row = new HorizontalLayout(nameSpan, balSpan);
                row.setWidthFull();
                row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
                row.setAlignItems(Alignment.CENTER);
                row.addClassName("hover-row");
                row.getStyle()
                        .set("padding", "var(--vaadin-gap-xs) var(--vaadin-gap-s)")
                        .set("border-radius", "8px")
                        .set("border-bottom", "1px solid var(--cuenti-divider)");
                card.add(row);
                groupSum = groupSum.add(converted);
            }

            // Group subtotal
            Span groupTotalLabel = new Span(getTranslation("dashboard.group_total", entry.getKey()));
            groupTotalLabel.getStyle().set("font-size", "var(--aura-font-size-s)").set("font-weight", "700")
                    .set("color", "var(--vaadin-text-color-secondary)");
            Span groupTotalVal = new Span(formatCurrency(groupSum, currentUser.getDefaultCurrency()));
            groupTotalVal.getStyle().set("font-size", "var(--aura-font-size-s)").set("font-weight", "700");
            HorizontalLayout groupTotalRow = new HorizontalLayout(groupTotalLabel, groupTotalVal);
            groupTotalRow.setWidthFull();
            groupTotalRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
            groupTotalRow.getStyle()
                    .set("padding", "var(--vaadin-gap-xs) var(--vaadin-gap-s)")
                    .set("background", "var(--vaadin-background-container)")
                    .set("border-radius", "8px").set("margin-top", "2px");
            card.add(groupTotalRow);
            grandTotal = grandTotal.add(groupSum);
        }

        // Grand total
        Span gtLabel = new Span(getTranslation("dashboard.grand_total").toUpperCase());
        gtLabel.getStyle()
                .set("font-size", "10px").set("font-weight", "700").set("letter-spacing", "0.07em");
        Span gtValue = new Span(formatCurrency(grandTotal, currentUser.getDefaultCurrency()));
        gtValue.getStyle()
                .set("font-size", "var(--aura-font-size-xl)").set("font-weight", "800")
                .set("color", grandTotal.compareTo(BigDecimal.ZERO) >= 0 ? "var(--aura-green)" : "var(--aura-red)");
        HorizontalLayout grandTotalRow = new HorizontalLayout(gtLabel, gtValue);
        grandTotalRow.setWidthFull();
        grandTotalRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        grandTotalRow.setAlignItems(Alignment.CENTER);
        grandTotalRow.getStyle()
                .set("padding", "var(--vaadin-gap-m) var(--vaadin-gap-s)")
                .set("margin-top", "var(--vaadin-gap-s)")
                .set("border-top", "2px solid var(--vaadin-border-color-secondary)");
        card.add(grandTotalRow);

        // Total incl. investment gain/loss
        BigDecimal totalInvestmentGain = assetPerformanceMap.values().stream()
                .map(AssetPerformanceData::gainLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalInvestmentGain.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal grandTotalWithGain = grandTotal.add(totalInvestmentGain);
            Span gtwgLabel = new Span(getTranslation("dashboard.grand_total_with_gain").toUpperCase());
            gtwgLabel.getStyle()
                    .set("font-size", "10px").set("font-weight", "700").set("letter-spacing", "0.07em")
                    .set("color", "var(--vaadin-text-color-secondary)");
            Span gtwgValue = new Span(formatCurrency(grandTotalWithGain, currentUser.getDefaultCurrency()));
            gtwgValue.getStyle()
                    .set("font-size", "var(--aura-font-size-xl)").set("font-weight", "800")
                    .set("color", grandTotalWithGain.compareTo(BigDecimal.ZERO) >= 0 ? "var(--aura-green)" : "var(--aura-red)");
            HorizontalLayout gtwgRow = new HorizontalLayout(gtwgLabel, gtwgValue);
            gtwgRow.setWidthFull();
            gtwgRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
            gtwgRow.setAlignItems(Alignment.CENTER);
            gtwgRow.getStyle()
                    .set("padding", "var(--vaadin-gap-s) var(--vaadin-gap-s)")
                    .set("border-top", "1px solid var(--vaadin-border-color-secondary)")
                    .set("background", "var(--vaadin-background-container)")
                    .set("border-radius", "0 0 8px 8px)");
            card.add(gtwgRow);
        }

        accountsLayout.add(card);
    }

    private com.cuenti.app.views.components.StatCard createMetricCard(String title, BigDecimal amount, VaadinIcon iconType) {
        com.cuenti.app.views.components.StatCard card = new com.cuenti.app.views.components.StatCard(
                iconType, title, formatCurrency(amount, currentUser.getDefaultCurrency()));
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            card.setTrend(false);
        }
        return card;
    }

    private Div createCardContainer() {
        Div card = new Div();
        card.setWidthFull();
        card.addClassName("card");
        return card;
    }

    private Div createSectionHeader(String titleKey, VaadinIcon iconType) {
        return createSectionHeader(titleKey, iconType, (Object[]) null);
    }

    private Div createSectionHeader(String titleKey, VaadinIcon iconType, Object... params) {
        Icon ico = iconType.create();
        ico.getStyle()
                .set("color", "var(--vaadin-text-color-secondary)")
                .set("font-size", "var(--aura-font-size-m)").set("flex-shrink", "0");
        String translatedTitle = (params != null && params.length > 0)
                ? getTranslation(titleKey, params)
                : getTranslation(titleKey);
        Span title = new Span(translatedTitle);
        title.getStyle()
                .set("font-size", "var(--aura-font-size-m)").set("font-weight", "700")
                .set("color", "var(--vaadin-text-color)");
        HorizontalLayout header = new HorizontalLayout(ico, title);
        header.setAlignItems(Alignment.CENTER);
        header.setSpacing(false);
        header.getStyle()
                .set("gap", "var(--vaadin-gap-s)")
                .set("margin-bottom", "var(--vaadin-gap-m)")
                .set("padding-bottom", "var(--vaadin-gap-s)")
                .set("border-bottom", "1px solid var(--vaadin-border-color-secondary)");
        Div wrapper = new Div(header);
        wrapper.setWidthFull();
        return wrapper;
    }

    private HorizontalLayout createLegendItem(String label, String color) {
        HorizontalLayout item = new HorizontalLayout();
        item.setAlignItems(Alignment.CENTER);
        item.setSpacing(false);
        item.getStyle().set("gap", "6px");
        Div dot = new Div();
        dot.setWidth("10px"); dot.setHeight("10px");
        dot.getStyle().set("background", color).set("border-radius", "3px").set("flex-shrink", "0");
        Span s = new Span(label);
        s.getStyle().set("font-size", "var(--aura-font-size-xs)").set("font-weight", "500")
                .set("color", "var(--vaadin-text-color-secondary)");
        item.add(dot, s);
        return item;
    }

    @Override
    public Locale getLocale() {
        return Locale.forLanguageTag(currentUser.getLocale());
    }

    private String formatCurrency(BigDecimal amount, String currencyCode) {
        return com.cuenti.app.util.CurrencyFormat.format(amount, currencyCode, getLocale());
    }

    private String getTimeRangeLabel(String range) {
        return switch (range) {
            case "daily" -> getTranslation("dashboard.range.daily");
            case "weekly" -> getTranslation("dashboard.range.weekly");
            case "monthly" -> getTranslation("dashboard.range.monthly");
            case "yearly" -> getTranslation("dashboard.range.yearly");
            default -> range;
        };
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
