package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.Category;
import com.cuenti.homebanking.model.Transaction;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.CategoryService;
import com.cuenti.homebanking.service.ExchangeRateService;
import com.cuenti.homebanking.service.TransactionService;
import com.cuenti.homebanking.service.UserService;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
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
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Vehicle fuel tracking and consumption statistics view.
 */
@Route(value = "vehicles", layout = MainLayout.class)
@PermitAll
public class VehiclesView extends VerticalLayout implements HasDynamicTitle {

    @Override
    public String getPageTitle() {
        return getTranslation("vehicles.title") + " | " + getTranslation("app.name");
    }


    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final ExchangeRateService exchangeRateService;
    private final User currentUser;

    private ComboBox<Category> categorySelect;
    private Select<String> timeRangeSelect;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private LocalDate startDate;
    private LocalDate endDate;

    private final Div summaryContainer = new Div();
    private final Grid<FuelEntry> grid = new Grid<>();

    private List<FuelEntry> fuelEntries = new ArrayList<>();

    private static final Pattern ODOMETER_PATTERN = Pattern.compile("d[=:]\\s*(\\d+(?:[.,]\\d+)?)");
    private static final Pattern LITERS_PATTERN = Pattern.compile("[vl][~=:]\\s*(\\d+(?:[.,]\\d+)?)");

    public VehiclesView(TransactionService transactionService, CategoryService categoryService,
                       UserService userService, ExchangeRateService exchangeRateService,
                       SecurityUtils securityUtils) {
        this.transactionService = transactionService;
        this.categoryService = categoryService;
        this.exchangeRateService = exchangeRateService;

        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        this.currentUser = userService.findByUsername(username);

        this.startDate = LocalDate.now().withDayOfMonth(1);
        this.endDate = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());

        addClassName("vehicles-view");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("padding", "var(--lumo-space-m)")
                .set("overflow", "hidden");

        setupUI();
        renderEmptyState();
    }

    private void setupUI() {
        // Page header
        Span title = new Span(getTranslation("vehicles.title"));
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-xxl)")
                .set("font-weight", "700")
                .set("color", "var(--lumo-header-text-color)");

        summaryContainer.setWidthFull();
        configureGrid();

        // Outer card
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
                .set("gap", "var(--lumo-space-m)");

        card.add(createToolbar(), summaryContainer, grid);
        add(title, card);
        expand(card);
    }

    private Div createToolbar() {
        HorizontalLayout filters = createFilters();
        HorizontalLayout categorySelector = createCategorySelector();

        HorizontalLayout inner = new HorizontalLayout(categorySelector, filters);
        inner.setWidthFull();
        inner.setAlignItems(Alignment.BASELINE);
        inner.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        inner.getStyle().set("flex-wrap", "wrap").set("gap", "var(--lumo-space-s)");

        Div toolbar = new Div(inner);
        toolbar.setWidthFull();
        toolbar.getStyle()
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "12px")
                .set("box-sizing", "border-box");
        return toolbar;
    }

    private HorizontalLayout createFilters() {
        HorizontalLayout filterLayout = new HorizontalLayout();
        filterLayout.setAlignItems(Alignment.BASELINE);
        filterLayout.setSpacing(false);
        filterLayout.getStyle().set("gap", "var(--lumo-space-s)");

        timeRangeSelect = new Select<>();
        timeRangeSelect.setLabel(getTranslation("statistics.time_range"));
        timeRangeSelect.setItems("today", "this_week", "this_month", "this_quarter", "this_year",
                                 "last_month", "last_quarter", "last_year", "all_time", "custom");
        timeRangeSelect.setItemLabelGenerator(item -> getTranslation("statistics.range_" + item));
        timeRangeSelect.setValue("this_month");
        timeRangeSelect.addValueChangeListener(e -> {
            updateDateRange(e.getValue());
            loadVehicleData();
        });

        startDatePicker = new DatePicker(getTranslation("statistics.from"));
        startDatePicker.setValue(startDate);
        startDatePicker.setVisible(false);
        startDatePicker.addValueChangeListener(e -> {
            startDate = e.getValue();
            loadVehicleData();
        });

        endDatePicker = new DatePicker(getTranslation("statistics.to"));
        endDatePicker.setValue(endDate);
        endDatePicker.setVisible(false);
        endDatePicker.addValueChangeListener(e -> {
            endDate = e.getValue();
            loadVehicleData();
        });

        filterLayout.add(timeRangeSelect, startDatePicker, endDatePicker);
        return filterLayout;
    }

    private void updateDateRange(String range) {
        boolean showCustom = "custom".equals(range);
        startDatePicker.setVisible(showCustom);
        endDatePicker.setVisible(showCustom);

        if (!showCustom) {
            LocalDate now = LocalDate.now();
            switch (range) {
                case "today": startDate = now; endDate = now; break;
                case "this_week":
                    startDate = now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                    endDate = now.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));
                    break;
                case "this_month":
                    startDate = now.withDayOfMonth(1);
                    endDate = now.with(TemporalAdjusters.lastDayOfMonth());
                    break;
                case "this_year":
                    startDate = now.withDayOfYear(1);
                    endDate = now.with(TemporalAdjusters.lastDayOfYear());
                    break;
                case "last_month":
                    startDate = now.minusMonths(1).withDayOfMonth(1);
                    endDate = now.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
                    break;
                case "last_year":
                    startDate = now.minusYears(1).withDayOfYear(1);
                    endDate = now.minusYears(1).with(TemporalAdjusters.lastDayOfYear());
                    break;
                case "all_time":
                    startDate = LocalDate.of(1900, 1, 1);
                    endDate = LocalDate.of(2100, 12, 31);
                    break;
            }
            startDatePicker.setValue(startDate);
            endDatePicker.setValue(endDate);
        }
    }

    private HorizontalLayout createCategorySelector() {
        HorizontalLayout selectorLayout = new HorizontalLayout();
        selectorLayout.setAlignItems(Alignment.BASELINE);

        categorySelect = new ComboBox<>();
        categorySelect.setLabel(getTranslation("vehicles.select_category"));
        categorySelect.setPlaceholder(getTranslation("vehicles.select_placeholder"));
        categorySelect.setItemLabelGenerator(Category::getFullName);

        List<Category> expenseCategories = categoryService.getAllCategories().stream()
                .filter(c -> c.getType() == Category.CategoryType.EXPENSE)
                .sorted(Comparator.comparing(Category::getFullName))
                .collect(Collectors.toList());

        categorySelect.setItems(expenseCategories);
        categorySelect.setClearButtonVisible(true);
        categorySelect.addValueChangeListener(e -> loadVehicleData());

        selectorLayout.add(categorySelect);
        return selectorLayout;
    }

    private void configureGrid() {
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        grid.setSizeFull();

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        // Date
        grid.addComponentColumn(e -> {
            Span s = new Span(e.date.format(dateFormatter));
            s.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)");
            return s;
        }).setHeader(getTranslation("vehicles.date")).setAutoWidth(true).setSortable(true)
                .setComparator(java.util.Comparator.comparing(e -> e.date));

        // Odometer
        grid.addComponentColumn(e -> {
            Span s = new Span(e.odometer != null ? e.odometer.setScale(0, RoundingMode.HALF_UP) + " km" : "—");
            s.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)");
            return s;
        }).setHeader(getTranslation("vehicles.odometer")).setAutoWidth(true).setSortable(true)
                .setComparator(java.util.Comparator.comparing(e -> e.odometer != null ? e.odometer : BigDecimal.ZERO));

        // Distance
        grid.addComponentColumn(e -> {
                    if (e.distance == null) { Span s = new Span("—"); s.getStyle().set("color", "var(--lumo-disabled-text-color)"); return s; }
                    Span s = new Span(e.distance.setScale(0, RoundingMode.HALF_UP) + " km");
                    s.getStyle().set("font-size", "var(--lumo-font-size-s)");
                    return s;
                }).setHeader(getTranslation("vehicles.distance")).setAutoWidth(true).setSortable(true)
                .setComparator(java.util.Comparator.comparing(e -> e.distance != null ? e.distance : BigDecimal.ZERO));

        // Liters
        grid.addComponentColumn(e -> {
            Span s = new Span(e.liters != null ? e.liters.setScale(2, RoundingMode.HALF_UP) + " L" : "—");
            s.getStyle().set("font-size", "var(--lumo-font-size-s)");
            return s;
        }).setHeader(getTranslation("vehicles.liters")).setAutoWidth(true).setSortable(true)
                .setComparator(java.util.Comparator.comparing(e -> e.liters != null ? e.liters : BigDecimal.ZERO));

        // Amount
        grid.addComponentColumn(e -> {
            BigDecimal converted = exchangeRateService.convert(e.amount, e.currency, currentUser.getDefaultCurrency());
            Span s = new Span(formatCurrency(converted));
            s.getStyle().set("font-weight", "700").set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-error-color)");
            return s;
        }).setHeader(getTranslation("vehicles.amount")).setAutoWidth(true).setSortable(true)
                .setComparator(java.util.Comparator.comparing(e -> e.amount));

        // Price / L
        grid.addComponentColumn(e -> {
            Span s = new Span(e.pricePerLiter != null ? formatCurrency(e.pricePerLiter) + "/L" : "—");
            s.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)");
            return s;
        }).setHeader(getTranslation("vehicles.price_per_liter")).setAutoWidth(true).setSortable(true)
                .setComparator(java.util.Comparator.comparing(e -> e.pricePerLiter != null ? e.pricePerLiter : BigDecimal.ZERO));

        // Consumption — coloured pill
        grid.addComponentColumn(e -> {
            if (e.consumption == null) { Span s = new Span("—"); s.getStyle().set("color", "var(--lumo-disabled-text-color)"); return s; }
            Span s = new Span(e.consumption + " L/100km");
            // Low consumption = green (< 6), medium = warning (6-9), high = red (> 9)
            String color = e.consumption.compareTo(BigDecimal.valueOf(6)) < 0
                    ? "var(--lumo-success-color)"
                    : e.consumption.compareTo(BigDecimal.valueOf(9)) < 0
                            ? "var(--lumo-warning-color, #e8a000)"
                            : "var(--lumo-error-color)";
            s.getStyle()
                    .set("font-size", "var(--lumo-font-size-xs)").set("font-weight", "700")
                    .set("padding", "2px 8px").set("border-radius", "99px")
                    .set("background", color + "1a") // 10% tint
                    .set("color", color).set("white-space", "nowrap");
            return s;
        }).setHeader(getTranslation("vehicles.consumption")).setAutoWidth(true).setSortable(true)
                .setComparator(java.util.Comparator.comparing(e -> e.consumption != null ? e.consumption : BigDecimal.ZERO));

        // Station
        grid.addComponentColumn(e -> {
                    Span s = new Span(e.station != null ? e.station : "—");
                    s.getStyle().set("font-weight", "500").set("font-size", "var(--lumo-font-size-s)");
                    return s;
                }).setHeader(getTranslation("vehicles.station")).setFlexGrow(1).setSortable(true)
                .setComparator(java.util.Comparator.comparing(e -> e.station != null ? e.station : ""));

    }

    private void loadVehicleData() {
        summaryContainer.removeAll();
        fuelEntries.clear();

        Category selectedCategory = categorySelect.getValue();
        if (selectedCategory == null) {
            grid.setItems(Collections.emptyList());
            renderEmptyState();
            return;
        }

        List<Transaction> transactions = transactionService.getTransactionsByUser(currentUser).stream()
                .filter(t -> t.getCategory() != null && t.getCategory().getId().equals(selectedCategory.getId()))
                .filter(t -> t.getType() == Transaction.TransactionType.EXPENSE)
                .filter(t -> {
                    LocalDate txDate = t.getTransactionDate().toLocalDate();
                    return !txDate.isBefore(startDate) && !txDate.isAfter(endDate);
                })
                .sorted(Comparator.comparing(Transaction::getTransactionDate))
                .collect(Collectors.toList());

        for (Transaction t : transactions) {
            FuelEntry entry = parseFuelEntry(t);
            if (entry != null) fuelEntries.add(entry);
        }

        calculateDerivedValues();
        renderSummary();
        grid.setItems(fuelEntries.stream().sorted(Comparator.comparing(FuelEntry::getDate).reversed()).toList());
    }

    private FuelEntry parseFuelEntry(Transaction t) {
        BigDecimal odometer = extractValue(t.getMemo(), ODOMETER_PATTERN, "(\\d{4,})\\s*km");
        BigDecimal liters = extractValue(t.getMemo(), LITERS_PATTERN, "(\\d+(?:[.,]\\d+)?)\\s*[Ll](?:\\s|$|\\))");

        return new FuelEntry(t.getTransactionDate().toLocalDate(), odometer, liters, t.getAmount(),
                t.getFromAccount() != null ? t.getFromAccount().getCurrency() : currentUser.getDefaultCurrency(),
                t.getPayee(), t.getMemo());
    }

    private BigDecimal extractValue(String memo, Pattern primary, String secondaryRegex) {
        if (memo == null || memo.isEmpty()) return null;
        Matcher m = primary.matcher(memo);
        if (m.find()) return new BigDecimal(m.group(1).replace(",", "."));
        Matcher m2 = Pattern.compile(secondaryRegex).matcher(memo);
        if (m2.find()) return new BigDecimal(m2.group(1).replace(",", "."));
        return null;
    }

    private void calculateDerivedValues() {
        FuelEntry previous = null;
        for (FuelEntry entry : fuelEntries) {
            if (previous != null && entry.odometer != null && previous.odometer != null) {
                entry.distance = entry.odometer.subtract(previous.odometer);
                if (entry.liters != null && entry.distance.compareTo(BigDecimal.ZERO) > 0) {
                    entry.consumption = entry.liters.divide(entry.distance, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                }
            }
            if (entry.liters != null && entry.liters.compareTo(BigDecimal.ZERO) > 0) {
                entry.pricePerLiter = entry.amount.divide(entry.liters, 3, RoundingMode.HALF_UP);
            }
            previous = entry;
        }
    }

    private void renderSummary() {
        if (fuelEntries.isEmpty()) {
            Div empty = new Div();
            empty.getStyle()
                    .set("display", "flex").set("flex-direction", "column").set("align-items", "center")
                    .set("justify-content", "center").set("padding", "var(--lumo-space-xl)")
                    .set("color", "var(--lumo-secondary-text-color)").set("gap", "var(--lumo-space-s)");
            Icon ico = VaadinIcon.DROP.create();
            ico.getStyle().set("font-size", "48px").set("opacity", "0.3");
            Span msg = new Span(getTranslation("vehicles.no_data"));
            msg.getStyle().set("font-size", "var(--lumo-font-size-m)");
            empty.add(ico, msg);
            summaryContainer.add(empty);
            return;
        }

        BigDecimal totalLiters   = fuelEntries.stream().filter(e -> e.liters != null).map(e -> e.liters).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount   = fuelEntries.stream().map(e -> exchangeRateService.convert(e.amount, e.currency, currentUser.getDefaultCurrency())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDistance = fuelEntries.stream().filter(e -> e.distance != null).map(e -> e.distance).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgConsumption = (totalDistance.compareTo(BigDecimal.ZERO) > 0 && totalLiters.compareTo(BigDecimal.ZERO) > 0)
                ? totalLiters.divide(totalDistance, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal avgPricePerL  = totalLiters.compareTo(BigDecimal.ZERO) > 0
                ? totalAmount.divide(totalLiters, 3, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        // Summary cards row
        FlexLayout summaryLayout = new FlexLayout();
        summaryLayout.setWidthFull();
        summaryLayout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        summaryLayout.getStyle().set("gap", "var(--lumo-space-m)");

        summaryLayout.add(
                createSummaryCard(getTranslation("vehicles.total_fillups"),  String.valueOf(fuelEntries.size()),                                   VaadinIcon.DROP, "var(--lumo-primary-color)"),
                createSummaryCard(getTranslation("vehicles.total_liters"),   totalLiters.setScale(2, RoundingMode.HALF_UP) + " L",                 VaadinIcon.FILL,  "var(--lumo-primary-color)"),
                createSummaryCard(getTranslation("vehicles.total_cost"),     formatCurrency(totalAmount),                                          VaadinIcon.MONEY,  "var(--lumo-error-color)"),
                createSummaryCard(getTranslation("vehicles.total_distance"), totalDistance.setScale(0, RoundingMode.HALF_UP) + " km",              VaadinIcon.ROAD,   "var(--lumo-success-color)"),
                createSummaryCard(getTranslation("vehicles.avg_consumption"),avgConsumption + " L/100km",                                          VaadinIcon.DASHBOARD, consumptionColor(avgConsumption)),
                createSummaryCard(getTranslation("vehicles.avg_price_per_liter"), avgPricePerL.compareTo(BigDecimal.ZERO) > 0 ? formatCurrency(avgPricePerL) + "/L" : "—", VaadinIcon.TAG, "var(--lumo-secondary-text-color)")
        );

        // Consumption trend mini chart
        Div trendSection = new Div();
        trendSection.setWidthFull();
        trendSection.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "16px")
                .set("padding", "var(--lumo-space-m) var(--lumo-space-l)")
                .set("box-sizing", "border-box")
                .set("margin-top", "var(--lumo-space-s)");

        Span trendTitle = new Span(getTranslation("vehicles.consumption_trend").toUpperCase());
        trendTitle.getStyle()
                .set("font-size", "10px").set("font-weight", "700").set("letter-spacing", "0.08em")
                .set("color", "var(--lumo-secondary-text-color)").set("display", "block")
                .set("margin-bottom", "var(--lumo-space-s)");
        trendSection.add(trendTitle);
        renderConsumptionChart(trendSection);

        summaryContainer.add(summaryLayout, trendSection);
    }

    private String consumptionColor(BigDecimal c) {
        if (c.compareTo(BigDecimal.ZERO) == 0) return "var(--lumo-secondary-text-color)";
        if (c.compareTo(BigDecimal.valueOf(6)) < 0) return "var(--lumo-success-color)";
        if (c.compareTo(BigDecimal.valueOf(9)) < 0) return "var(--lumo-warning-color, #e8a000)";
        return "var(--lumo-error-color)";
    }

    private void renderConsumptionChart(Div container) {
        List<FuelEntry> withConsumption = fuelEntries.stream()
                .filter(e -> e.consumption != null)
                .sorted(java.util.Comparator.comparing(e -> e.date))
                .toList();
        if (withConsumption.isEmpty()) {
            Span none = new Span(getTranslation("vehicles.no_consumption_data"));
            none.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)");
            container.add(none);
            return;
        }

        BigDecimal maxC = withConsumption.stream().map(e -> e.consumption).max(BigDecimal::compareTo).orElse(BigDecimal.TEN);

        Div chartArea = new Div();
        chartArea.getStyle()
                .set("display", "flex").set("align-items", "flex-end").set("gap", "6px")
                .set("height", "80px").set("overflow-x", "auto").set("padding-bottom", "2px");

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM");
        for (FuelEntry e : withConsumption) {
            double pct = maxC.compareTo(BigDecimal.ZERO) > 0
                    ? e.consumption.divide(maxC, 4, RoundingMode.HALF_UP).doubleValue() * 68 : 2;
            String color = consumptionColor(e.consumption);

            Div barGroup = new Div();
            barGroup.getStyle().set("display", "flex").set("flex-direction", "column")
                    .set("align-items", "center").set("gap", "3px").set("min-width", "36px");

            Span value = new Span(e.consumption.toPlainString());
            value.getStyle().set("font-size", "9px").set("font-weight", "700")
                    .set("color", color).set("white-space", "nowrap");

            Div bar = new Div();
            bar.getStyle()
                    .set("width", "24px").set("height", Math.max(4, pct) + "px")
                    .set("background", "linear-gradient(to top, " + color + ", " + color + "88)")
                    .set("border-radius", "4px 4px 0 0");

            Span lbl = new Span(e.date.format(fmt));
            lbl.getStyle().set("font-size", "9px").set("color", "var(--lumo-secondary-text-color)")
                    .set("white-space", "nowrap");

            barGroup.add(value, bar, lbl);
            chartArea.add(barGroup);
        }
        container.add(chartArea);
    }

    private Div createSummaryCard(String title, String value, VaadinIcon iconType, String accentColor) {
        Div card = new Div();
        card.getStyle()
                .set("flex", "1 1 150px").set("min-width", "140px")
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "16px")
                .set("padding", "var(--lumo-space-m) var(--lumo-space-l)")
                .set("box-shadow", "0 1px 6px rgba(0,0,0,0.07)")
                .set("border-left", "4px solid " + accentColor)
                .set("display", "flex").set("flex-direction", "column").set("gap", "var(--lumo-space-xs)");

        Span titleSpan = new Span(title.toUpperCase());
        titleSpan.getStyle()
                .set("font-size", "10px").set("font-weight", "700").set("letter-spacing", "0.08em")
                .set("color", "var(--lumo-secondary-text-color)");

        Span valueSpan = new Span(value);
        valueSpan.getStyle()
                .set("font-size", "var(--lumo-font-size-xl)").set("font-weight", "700")
                .set("color", accentColor).set("line-height", "1.1");

        card.add(titleSpan, valueSpan);
        return card;
    }

    private void renderEmptyState() {
        summaryContainer.removeAll();
        Div empty = new Div();
        empty.getStyle()
                .set("display", "flex").set("flex-direction", "column").set("align-items", "center")
                .set("justify-content", "center").set("padding", "var(--lumo-space-xl)")
                .set("color", "var(--lumo-secondary-text-color)").set("gap", "var(--lumo-space-s)");
        Icon ico = VaadinIcon.CAR.create();
        ico.getStyle().set("font-size", "48px").set("opacity", "0.25");
        Span msg = new Span(getTranslation("vehicles.select_placeholder"));
        msg.getStyle().set("font-size", "var(--lumo-font-size-m)");
        empty.add(ico, msg);
        summaryContainer.add(empty);
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "-";
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag(currentUser.getLocale()));
        try {
            formatter.setCurrency(java.util.Currency.getInstance(currentUser.getDefaultCurrency()));
        } catch (Exception ignored) {}
        return formatter.format(amount);
    }

    private static class FuelEntry {
        java.time.LocalDate date;
        BigDecimal odometer;
        BigDecimal liters;
        BigDecimal amount;
        String currency;
        String station;
        String memo;
        BigDecimal distance;
        BigDecimal consumption;
        BigDecimal pricePerLiter;

        FuelEntry(java.time.LocalDate date, BigDecimal odometer, BigDecimal liters, BigDecimal amount, String currency, String station, String memo) {
            this.date = date; this.odometer = odometer; this.liters = liters; this.amount = amount; this.currency = currency; this.station = station; this.memo = memo;
        }
        public java.time.LocalDate getDate() { return date; }
    }
}
