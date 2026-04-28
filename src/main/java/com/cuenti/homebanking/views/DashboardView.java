package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.*;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.AccountService;
import com.cuenti.homebanking.service.AssetService;
import com.cuenti.homebanking.service.ExchangeRateService;
import com.cuenti.homebanking.service.TransactionService;
import com.cuenti.homebanking.service.UserService;
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
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        setAlignItems(Alignment.CENTER);
        getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("overflow-y", "auto");

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

        // Page title
        Span pageTitle = new Span(getTranslation("dashboard.title"));
        pageTitle.getStyle()
                .set("font-size", "var(--lumo-font-size-xxl)")
                .set("font-weight", "700")
                .set("color", "var(--lumo-header-text-color)");

        Div container = new Div();
        container.setWidthFull();
        container.setMaxWidth("1400px");
        container.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-m)")
                .set("padding", "var(--lumo-space-m)")
                .set("box-sizing", "border-box");

        createMetrics(accounts);
        createAssetPerformanceSection();
        createAccountList(accounts);
        createCharts(userTransactions);

        container.add(pageTitle, metricsLayout, assetPerformanceLayout, chartsLayout, accountsLayout);
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
                createMetricCard(getTranslation("dashboard.available_cash"),  availableCash,  VaadinIcon.WALLET,    "linear-gradient(135deg, #1a9a5c 0%, #2ecc71 100%)"),
                createMetricCard(getTranslation("dashboard.portfolio_value"), portfolioValue, VaadinIcon.STOCK,     "linear-gradient(135deg, #1a6bbf 0%, #3498db 100%)"),
                createMetricCard(getTranslation("dashboard.net_worth"),       netWorth,       VaadinIcon.TRENDING_UP, "linear-gradient(135deg, #7b2fa8 0%, #9b59b6 100%)")
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
                .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
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
                    .set("letter-spacing", "0.07em").set("color", "var(--lumo-secondary-text-color)");
            headerRow.add(h);
        }
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
            row.getStyle()
                    .set("padding", "var(--lumo-space-s) var(--lumo-space-s)")
                    .set("border-radius", "8px")
                    .set("border-bottom", "1px solid var(--lumo-contrast-5pct)")
                    .set("transition", "background 0.12s");
            row.getElement().executeJs(
                "this.addEventListener('mouseenter',()=>this.style.background='var(--lumo-contrast-5pct)');" +
                "this.addEventListener('mouseleave',()=>this.style.background='');"
            );

            // Asset name + symbol stacked
            Div assetInfo = new Div();
            assetInfo.getStyle().set("flex", "2").set("display", "flex")
                    .set("flex-direction", "column").set("gap", "2px");
            Span assetName = new Span(asset.getName());
            assetName.getStyle().set("font-weight", "600").set("font-size", "var(--lumo-font-size-s)");
            Span assetSymbol = new Span(asset.getSymbol());
            assetSymbol.getStyle()
                    .set("font-size", "10px").set("font-weight", "700").set("letter-spacing", "0.05em")
                    .set("padding", "1px 5px").set("border-radius", "4px")
                    .set("background", "var(--lumo-contrast-10pct)")
                    .set("color", "var(--lumo-secondary-text-color)").set("width", "fit-content");
            assetInfo.add(assetName, assetSymbol);

            Span unitsSpan = new Span(data.totalUnits().stripTrailingZeros().toPlainString());
            unitsSpan.getStyle().set("flex", "1").set("text-align", "right")
                    .set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)");

            Span costSpan = new Span(formatCurrency(data.totalCost(), currentUser.getDefaultCurrency()));
            costSpan.getStyle().set("flex", "1.5").set("text-align", "right").set("font-size", "var(--lumo-font-size-s)");

            Span valueSpan = new Span(formatCurrency(data.currentValue(), currentUser.getDefaultCurrency()));
            valueSpan.getStyle().set("flex", "1.5").set("text-align", "right")
                    .set("font-weight", "600").set("font-size", "var(--lumo-font-size-s)");

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
                .set("padding", "var(--lumo-space-s) var(--lumo-space-s)")
                .set("margin-top", "var(--lumo-space-xs)")
                .set("border-top", "2px solid var(--lumo-contrast-10pct)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "0 0 8px 8px");

        Span totalLabel = new Span(getTranslation("dashboard.total_portfolio").toUpperCase());
        totalLabel.getStyle().set("flex", "2").set("font-size", "10px")
                .set("font-weight", "700").set("letter-spacing", "0.07em");
        Span emptyUnits = new Span(""); emptyUnits.getStyle().set("flex", "1");
        Span totalCostSpan = new Span(formatCurrency(totalCost, currentUser.getDefaultCurrency()));
        totalCostSpan.getStyle().set("flex", "1.5").set("text-align", "right")
                .set("font-weight", "700").set("font-size", "var(--lumo-font-size-s)");
        Span totalValueSpan = new Span(formatCurrency(totalValue, currentUser.getDefaultCurrency()));
        totalValueSpan.getStyle().set("flex", "1.5").set("text-align", "right")
                .set("font-weight", "700").set("font-size", "var(--lumo-font-size-s)");
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
        boolean isPositive = gainLoss.compareTo(BigDecimal.ZERO) >= 0;
        String color = isPositive ? "var(--lumo-success-color)" : "var(--lumo-error-color)";
        String sign   = isPositive ? "+" : "";

        Div container = new Div();
        container.getStyle()
                .set("display", "flex").set("flex-direction", "column").set("align-items", "flex-end")
                .set("gap", "3px");

        Span amountSpan = new Span(sign + formatCurrency(gainLoss.abs(), currentUser.getDefaultCurrency()));
        amountSpan.getStyle()
                .set("font-size", "var(--lumo-font-size-s)").set("font-weight", "700").set("color", color);

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
        chartsLayout.getStyle().set("gap", "var(--lumo-space-m)");

        // ── Cash Flow card ────────────────────────────────────────────
        Div timeChartCard = createCardContainer();
        timeChartCard.getStyle().set("flex", "1 1 450px").set("min-width", "0");
        timeChartCard.add(createSectionHeader("dashboard.cash_flow", VaadinIcon.BAR_CHART));

        // Range selector + legend in one toolbar row
        Select<String> timeRange = new Select<>();
        timeRange.setItems("Daily", "Weekly", "Monthly", "Yearly");
        timeRange.setValue("Monthly");
        timeRange.getStyle().set("min-width", "130px");

        HorizontalLayout chartToolbar = new HorizontalLayout();
        chartToolbar.setWidthFull();
        chartToolbar.setAlignItems(Alignment.CENTER);
        chartToolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        chartToolbar.getStyle()
                .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "8px")
                .set("margin-bottom", "var(--lumo-space-s)");

        HorizontalLayout legend = new HorizontalLayout();
        legend.setSpacing(false);
        legend.getStyle().set("gap", "var(--lumo-space-m)");
        legend.add(
            createLegendItem(getTranslation("dashboard.revenue"), "var(--lumo-success-color)"),
            createLegendItem(getTranslation("dashboard.spending"), "var(--lumo-error-color)")
        );
        chartToolbar.add(legend, timeRange);
        timeChartCard.add(chartToolbar);

        timeChartContainer.setWidthFull();
        timeChartContainer.getStyle()
                .set("overflow-x", "auto")
                .set("padding-bottom", "var(--lumo-space-xs)");
        renderTimeChart(timeChartContainer, transactions, timeRange.getValue());
        timeRange.addValueChangeListener(e -> {
            renderTimeChart(timeChartContainer, transactions, e.getValue());
            renderDistributionChart(distributionContainer, transactions, e.getValue());
        });
        timeChartCard.add(timeChartContainer);

        // ── Top Spending card ─────────────────────────────────────────
        Div distCard = createCardContainer();
        distCard.getStyle().set("flex", "1 1 300px").set("min-width", "0");
        distCard.add(createSectionHeader("dashboard.top_spending", VaadinIcon.PIE_CHART));

        distributionContainer.setWidthFull();
        distributionContainer.getStyle()
                .set("display", "flex").set("flex-direction", "column").set("gap", "var(--lumo-space-s)");
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
            case "Daily"   -> 30;
            case "Weekly"  -> 12;
            case "Yearly"  -> 5;
            default        -> 12; // Monthly
        };

        for (int i = buckets - 1; i >= 0; i--) {
            String label = switch (range) {
                case "Daily"  -> today.minusDays(i).format(DateTimeFormatter.ofPattern("dd.MM"));
                case "Weekly" -> {
                    LocalDate w = today.minusWeeks(i);
                    yield "W" + w.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                            + "’" + String.valueOf(w.getYear()).substring(2);
                }
                case "Yearly" -> String.valueOf(today.minusYears(i).getYear());
                default -> today.minusMonths(i).getMonth().getDisplayName(TextStyle.SHORT, userLocale)
                            + " ’" + String.valueOf(today.minusMonths(i).getYear()).substring(2);
            };
            chartData.put(label, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }

        // Populate from transactions
        for (Transaction t : transactions) {
            LocalDate td = t.getTransactionDate().toLocalDate();
            String label = switch (range) {
                case "Daily"  -> td.format(DateTimeFormatter.ofPattern("dd.MM"));
                case "Weekly" -> "W" + td.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                                 + "’" + String.valueOf(td.getYear()).substring(2);
                case "Yearly" -> String.valueOf(td.getYear());
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

        BigDecimal max = BigDecimal.ONE;
        for (BigDecimal[] v : chartData.values()) max = max.max(v[0]).max(v[1]);

        final int CHART_H = 160;

        Div chartArea = new Div();
        chartArea.getStyle()
                .set("display", "flex").set("align-items", "flex-end").set("justify-content", "space-around")
                .set("gap", "4px").set("height", (CHART_H + 24) + "px")
                .set("border-bottom", "2px solid var(--lumo-contrast-10pct)")
                .set("padding", "0 var(--lumo-space-xs)").set("min-width", "min-content");

        int idx = 0;
        for (Map.Entry<String, BigDecimal[]> entry : chartData.entrySet()) {
            boolean isCurrent = (idx == chartData.size() - 1);
            BigDecimal inc = entry.getValue()[0];
            BigDecimal exp = entry.getValue()[1];

            double hInc = max.compareTo(BigDecimal.ZERO) > 0
                    ? inc.divide(max, 4, RoundingMode.HALF_UP).doubleValue() * CHART_H : 0;
            double hExp = max.compareTo(BigDecimal.ZERO) > 0
                    ? exp.divide(max, 4, RoundingMode.HALF_UP).doubleValue() * CHART_H : 0;

            Div group = new Div();
            group.getStyle()
                    .set("display", "flex").set("flex-direction", "column")
                    .set("align-items", "center").set("gap", "3px")
                    .set("min-width", "28px").set("flex", "1 1 0");

            Div barsRow = new Div();
            barsRow.getStyle().set("display", "flex").set("align-items", "flex-end")
                    .set("gap", "2px").set("height", CHART_H + "px");
            barsRow.add(createChartBar(hInc, "var(--lumo-success-color)", "#b7f5c8", isCurrent));
            barsRow.add(createChartBar(hExp, "var(--lumo-error-color)",   "#ffb3b3", isCurrent));

            Span lbl = new Span(entry.getKey());
            lbl.getStyle()
                    .set("font-size", "9px")
                    .set("font-weight", isCurrent ? "700" : "400")
                    .set("color", isCurrent ? "var(--lumo-primary-color)" : "var(--lumo-secondary-text-color)")
                    .set("white-space", "nowrap");
            group.add(barsRow, lbl);
            chartArea.add(group);
            idx++;
        }

        Div scroll = new Div(chartArea);
        scroll.setWidthFull();
        scroll.getStyle().set("overflow-x", "auto").set("padding-bottom", "2px");
        container.add(scroll);
    }

    private Div createChartBar(double heightPx, String colorTop, String colorBottom, boolean highlight) {
        Div bar = new Div();
        bar.setWidth("12px");
        bar.setHeight(Math.max(2, heightPx) + "px");
        bar.getStyle()
                .set("background", "linear-gradient(to top, " + colorBottom + ", " + colorTop + ")")
                .set("border-radius", "3px 3px 0 0")
                .set("opacity", highlight ? "1" : "0.7")
                .set("transition", "opacity 0.15s");
        bar.getElement().executeJs(
            "this.addEventListener('mouseenter',()=>this.style.opacity='1');" +
            "this.addEventListener('mouseleave',()=>this.style.opacity='" + (highlight ? "1" : "0.7") + "');"
        );
        return bar;
    }

    private void renderDistributionChart(Div container, List<Transaction> transactions, String range) {
        container.removeAll();
        LocalDate today = LocalDate.now();

        Map<String, BigDecimal> data = transactions.stream()
                .filter(t -> t.getCategory() != null && t.getType() == Transaction.TransactionType.EXPENSE && t.getFromAccount() != null)
                .filter(t -> {
                    LocalDate td = t.getTransactionDate().toLocalDate();
                    if ("Daily".equals(range))  return td.isEqual(today);
                    if ("Weekly".equals(range))
                        return td.getYear() == today.getYear()
                            && td.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                               == today.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                    if ("Monthly".equals(range))
                        return td.getYear() == today.getYear() && td.getMonth() == today.getMonth();
                    return td.getYear() == today.getYear();
                })
                .collect(Collectors.groupingBy(t -> t.getCategory().getName(),
                        Collectors.reducing(BigDecimal.ZERO,
                                t -> exchangeRateService.convert(t.getAmount(), t.getFromAccount().getCurrency(), currentUser.getDefaultCurrency()),
                                BigDecimal::add)));

        if (data.isEmpty()) {
            Span empty = new Span(getTranslation("dashboard.no_spending"));
            empty.getStyle().set("font-size", "var(--lumo-font-size-s)")
                    .set("color", "var(--lumo-secondary-text-color)");
            container.add(empty);
            return;
        }

        BigDecimal total = data.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        // Collect top 5 first so we can compute the max for bar scaling
        List<Map.Entry<String, BigDecimal>> top5 = data.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());

        // The largest bar fills 100 %; others are relative to it — bars always look meaningful
        BigDecimal maxValue = top5.isEmpty() ? BigDecimal.ONE : top5.get(0).getValue();

        // Colour palette cycling through Lumo + accent colours
        String[] COLORS = {
            "var(--lumo-primary-color)",
            "var(--lumo-error-color)",
            "var(--lumo-success-color)",
            "#f5a623",
            "#9b59b6"
        };

        int[] ci = {0};
        top5.forEach(entry -> {
                    String color = COLORS[ci[0] % COLORS.length];
                    ci[0]++;

                    // Bar width relative to top entry (always 100 % for #1)
                    double barPct = maxValue.compareTo(BigDecimal.ZERO) > 0
                            ? entry.getValue().divide(maxValue, 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0;

                    // Label shows share of total spending for context
                    double ofTotal = total.compareTo(BigDecimal.ZERO) > 0
                            ? entry.getValue().divide(total, 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0;

                    Span catSpan = new Span(entry.getKey());
                    catSpan.getStyle().set("font-size", "var(--lumo-font-size-s)").set("font-weight", "500");

                    Span pctSpan = new Span(String.format("%.0f%%", ofTotal));
                    pctSpan.getStyle().set("font-size", "var(--lumo-font-size-xs)")
                            .set("color", "var(--lumo-secondary-text-color)");

                    Span amtSpan = new Span(formatCurrency(entry.getValue(), currentUser.getDefaultCurrency()));
                    amtSpan.getStyle().set("font-size", "var(--lumo-font-size-s)").set("font-weight", "700")
                            .set("color", color);

                    HorizontalLayout info = new HorizontalLayout();
                    info.setWidthFull();
                    info.setAlignItems(Alignment.BASELINE);
                    info.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
                    info.getStyle().set("gap", "var(--lumo-space-s)");

                    HorizontalLayout left = new HorizontalLayout(catSpan, pctSpan);
                    left.setAlignItems(Alignment.BASELINE);
                    left.setSpacing(false);
                    left.getStyle().set("gap", "var(--lumo-space-xs)");
                    info.add(left, amtSpan);

                    Div progressBg = new Div();
                    progressBg.setWidthFull();
                    progressBg.getStyle()
                            .set("background", "var(--lumo-contrast-5pct)")
                            .set("border-radius", "6px").set("height", "8px").set("overflow", "hidden");
                    Div bar = new Div();
                    bar.setWidth(barPct + "%");
                    bar.setHeight("100%");
                    bar.getStyle()
                            .set("background", color)
                            .set("border-radius", "6px")
                            .set("opacity", "0.85");
                    progressBg.add(bar);

                    Div item = new Div(info, progressBg);
                    item.setWidthFull();
                    item.getStyle().set("display", "flex").set("flex-direction", "column")
                            .set("gap", "4px").set("padding", "var(--lumo-space-xs) var(--lumo-space-xs)")
                            .set("border-radius", "8px").set("transition", "background 0.12s");
                    item.getElement().executeJs(
                        "this.addEventListener('mouseenter',()=>this.style.background='var(--lumo-contrast-5pct)');" +
                        "this.addEventListener('mouseleave',()=>this.style.background='');"
                    );
                    container.add(item);
                });
    }

    private void createAccountList(List<Account> accounts) {
        accountsLayout.setWidthFull();
        Div card = createCardContainer();
        card.add(createSectionHeader("dashboard.accounts_overview", VaadinIcon.CREDIT_CARD));

        Map<String, List<Account>> grouped = accounts.stream()
                .collect(Collectors.groupingBy(a -> a.getAccountGroup() != null ? a.getAccountGroup() : "Other"));

        BigDecimal grandTotal = BigDecimal.ZERO;

        for (Map.Entry<String, List<Account>> entry : grouped.entrySet()) {
            // Group label
            Span groupLabel = new Span(entry.getKey().toUpperCase());
            groupLabel.getStyle()
                    .set("font-size", "10px").set("font-weight", "700").set("letter-spacing", "0.08em")
                    .set("color", "var(--lumo-primary-color)").set("display", "block")
                    .set("margin-top", "var(--lumo-space-m)").set("margin-bottom", "var(--lumo-space-xs)");
            card.add(groupLabel);

            BigDecimal groupSum = BigDecimal.ZERO;
            for (Account acc : entry.getValue()) {
                BigDecimal converted = exchangeRateService.convert(acc.getBalance(), acc.getCurrency(), currentUser.getDefaultCurrency());
                boolean negative = converted.compareTo(BigDecimal.ZERO) < 0;

                Span nameSpan = new Span(acc.getAccountName());
                nameSpan.getStyle().set("font-size", "var(--lumo-font-size-s)").set("font-weight", "500");

                Span balSpan = new Span(formatCurrency(converted, currentUser.getDefaultCurrency()));
                balSpan.getStyle()
                        .set("font-size", "var(--lumo-font-size-s)").set("font-weight", "600")
                        .set("color", negative ? "var(--lumo-error-color)" : "var(--lumo-body-text-color)");

                HorizontalLayout row = new HorizontalLayout(nameSpan, balSpan);
                row.setWidthFull();
                row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
                row.setAlignItems(Alignment.CENTER);
                row.getStyle()
                        .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
                        .set("border-radius", "8px")
                        .set("border-bottom", "1px solid var(--lumo-contrast-5pct)")
                        .set("transition", "background 0.12s");
                row.getElement().executeJs(
                    "this.addEventListener('mouseenter',()=>this.style.background='var(--lumo-contrast-5pct)');" +
                    "this.addEventListener('mouseleave',()=>this.style.background='');"
                );
                card.add(row);
                groupSum = groupSum.add(converted);
            }

            // Group subtotal
            Span groupTotalLabel = new Span(getTranslation("dashboard.group_total", entry.getKey()));
            groupTotalLabel.getStyle().set("font-size", "var(--lumo-font-size-s)").set("font-weight", "700")
                    .set("color", "var(--lumo-secondary-text-color)");
            Span groupTotalVal = new Span(formatCurrency(groupSum, currentUser.getDefaultCurrency()));
            groupTotalVal.getStyle().set("font-size", "var(--lumo-font-size-s)").set("font-weight", "700");
            HorizontalLayout groupTotalRow = new HorizontalLayout(groupTotalLabel, groupTotalVal);
            groupTotalRow.setWidthFull();
            groupTotalRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
            groupTotalRow.getStyle()
                    .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
                    .set("background", "var(--lumo-contrast-5pct)")
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
                .set("font-size", "var(--lumo-font-size-xl)").set("font-weight", "800")
                .set("color", grandTotal.compareTo(BigDecimal.ZERO) >= 0 ? "var(--lumo-success-color)" : "var(--lumo-error-color)");
        HorizontalLayout grandTotalRow = new HorizontalLayout(gtLabel, gtValue);
        grandTotalRow.setWidthFull();
        grandTotalRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        grandTotalRow.setAlignItems(Alignment.CENTER);
        grandTotalRow.getStyle()
                .set("padding", "var(--lumo-space-m) var(--lumo-space-s)")
                .set("margin-top", "var(--lumo-space-s)")
                .set("border-top", "2px solid var(--lumo-contrast-10pct)");
        card.add(grandTotalRow);
        accountsLayout.add(card);
    }

    private Div createMetricCard(String title, BigDecimal amount, VaadinIcon iconType, String background) {
        Div card = new Div();
        card.getStyle()
                .set("flex", "1 1 260px").set("min-width", "220px")
                .set("background", background)
                .set("border-radius", "20px")
                .set("padding", "var(--lumo-space-l)")
                .set("box-shadow", "0 4px 20px rgba(0,0,0,0.15)")
                .set("display", "flex").set("flex-direction", "column").set("gap", "var(--lumo-space-s)")
                .set("position", "relative").set("overflow", "hidden");

        // Decorative circle
        Div circle = new Div();
        circle.getStyle()
                .set("position", "absolute").set("top", "-20px").set("right", "-20px")
                .set("width", "100px").set("height", "100px").set("border-radius", "50%")
                .set("background", "rgba(255,255,255,0.12)");
        card.add(circle);

        // Icon
        Icon ico = iconType.create();
        ico.getStyle().set("color", "rgba(255,255,255,0.85)").set("font-size", "20px");

        Span titleSpan = new Span(title.toUpperCase());
        titleSpan.getStyle()
                .set("font-size", "10px").set("font-weight", "700").set("letter-spacing", "0.1em")
                .set("color", "rgba(255,255,255,0.8)");

        Span valueSpan = new Span(formatCurrency(amount, currentUser.getDefaultCurrency()));
        valueSpan.getStyle()
                .set("font-size", "var(--lumo-font-size-xxl)").set("font-weight", "800")
                .set("color", "white").set("line-height", "1.1");

        card.add(ico, titleSpan, valueSpan);
        return card;
    }

    private Div createCardContainer() {
        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background-color", "var(--lumo-base-color)")
                .set("border-radius", "20px")
                .set("box-shadow", "0 2px 12px rgba(0,0,0,0.06)")
                .set("padding", "var(--lumo-space-l)")
                .set("box-sizing", "border-box");
        return card;
    }

    private Div createSectionHeader(String titleKey, VaadinIcon iconType) {
        Icon ico = iconType.create();
        ico.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-m)").set("flex-shrink", "0");
        Span title = new Span(getTranslation(titleKey));
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-m)").set("font-weight", "700")
                .set("color", "var(--lumo-header-text-color)");
        HorizontalLayout header = new HorizontalLayout(ico, title);
        header.setAlignItems(Alignment.CENTER);
        header.setSpacing(false);
        header.getStyle()
                .set("gap", "var(--lumo-space-s)")
                .set("margin-bottom", "var(--lumo-space-m)")
                .set("padding-bottom", "var(--lumo-space-s)")
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)");
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
        s.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("font-weight", "500")
                .set("color", "var(--lumo-secondary-text-color)");
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
