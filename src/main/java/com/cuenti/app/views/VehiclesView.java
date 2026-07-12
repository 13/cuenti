package com.cuenti.app.views;

import com.cuenti.app.model.Category;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import com.cuenti.app.security.SecurityUtils;
import com.cuenti.app.views.components.EmptyStateNotice;
import com.cuenti.app.service.CategoryService;
import com.cuenti.app.service.ExchangeRateService;
import com.cuenti.app.service.TransactionService;
import com.cuenti.app.service.UserService;
import com.cuenti.app.service.VehicleReportService;
import com.cuenti.app.service.VehicleReportService.FuelEntry;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.AfterNavigationEvent;
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

/**
 * Vehicle fuel tracking and consumption statistics view.
 */
@Route(value = "vehicles", layout = MainLayout.class)
@PermitAll
public class VehiclesView extends VerticalLayout implements HasDynamicTitle, AfterNavigationObserver {

    @Override
    public String getPageTitle() {
        return getTranslation("vehicles.title") + " | " + getTranslation("app.name");
    }


    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final ExchangeRateService exchangeRateService;
    private final UserService userService;
    private final User currentUser;

    private ComboBox<Category> categorySelect;
    private Button saveDefaultCategoryButton;
    private Select<String> timeRangeSelect;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private LocalDate startDate;
    private LocalDate endDate;

    private final Div summaryContainer = new Div();
    private final Grid<FuelEntry> grid = new Grid<>();

    private List<FuelEntry> fuelEntries = new ArrayList<>();

    public VehiclesView(TransactionService transactionService, CategoryService categoryService,
                       UserService userService, ExchangeRateService exchangeRateService,
                       SecurityUtils securityUtils) {
        this.transactionService = transactionService;
        this.categoryService = categoryService;
        this.exchangeRateService = exchangeRateService;
        this.userService = userService;

        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        this.currentUser = userService.findByUsername(username);

        // Default timeframe should match the preselected timeRangeSelect value ("this_year")
        LocalDate now = LocalDate.now();
        // set default to "this_year"
        this.startDate = now.withDayOfYear(1);
        this.endDate = now.with(TemporalAdjusters.lastDayOfYear());

        addClassNames("vehicles-view", "page-scroll", "page-shell");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        setupUI();
        // Initial load is deferred to afterNavigation to ensure the view is fully attached
        // and that listeners (time range/category) have settled. afterNavigation() will
        // perform an initial selection if needed and call loadVehicleData().
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        // Ensure a category is selected (try saved default, otherwise pick first expense category)
        if (categorySelect != null && categorySelect.getValue() == null) {
            List<Category> expenseCategories = categoryService.getAllCategories().stream()
                    .filter(c -> c.getType() == Category.CategoryType.EXPENSE)
                    .sorted(Comparator.comparing(Category::getFullName))
                    .collect(Collectors.toList());
            if (!expenseCategories.isEmpty()) {
                // Try to preselect saved category first
                Long saved = currentUser.getDefaultVehicleCategoryId();
                if (saved != null) {
                    expenseCategories.stream().filter(c -> saved.equals(c.getId())).findFirst()
                            .ifPresentOrElse(categorySelect::setValue, () -> categorySelect.setValue(expenseCategories.get(0)));
                } else {
                    categorySelect.setValue(expenseCategories.get(0));
                }
            }
        }

        // Finally load data for the current selection/date range
        loadVehicleData();
    }

    private void setupUI() {
        // Page header
        Span title = new Span(getTranslation("vehicles.title"));
        title.addComponentAsFirst(VaadinIcon.CAR.create());
        title.addClassName("page-title");

        summaryContainer.setWidthFull();
        configureGrid();

        // Outer card
        Div card = new Div();
        card.setSizeFull();
        card.addClassName("card");
        card.addClassName("card--flex");

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
        inner.getStyle().set("flex-wrap", "wrap").set("gap", "var(--vaadin-gap-s)");

        Div toolbar = new Div(inner);
        toolbar.setWidthFull();
        toolbar.addClassName("card-toolbar");
        toolbar.getStyle().set("box-sizing", "border-box");
        return toolbar;
    }

    private HorizontalLayout createFilters() {
        HorizontalLayout filterLayout = new HorizontalLayout();
        filterLayout.setAlignItems(Alignment.BASELINE);
        filterLayout.setSpacing(false);
        filterLayout.getStyle().set("gap", "var(--vaadin-gap-s)");

        timeRangeSelect = new Select<>();
        timeRangeSelect.setLabel(getTranslation("statistics.time_range"));
        timeRangeSelect.setItems("today", "this_week", "this_month", "this_quarter", "this_year",
                                 "last_month", "last_quarter", "last_year", "all_time", "custom");
        timeRangeSelect.setItemLabelGenerator(item -> getTranslation("statistics.range_" + item));
        timeRangeSelect.setValue("this_year");
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

        saveDefaultCategoryButton = new Button(getTranslation("vehicles.save_default"), e -> saveDefaultCategory());
        saveDefaultCategoryButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        saveDefaultCategoryButton.setEnabled(false);

        List<Category> expenseCategories = categoryService.getAllCategories().stream()
                .filter(c -> c.getType() == Category.CategoryType.EXPENSE)
                .sorted(Comparator.comparing(Category::getFullName))
                .collect(Collectors.toList());

        categorySelect.setItems(expenseCategories);
        categorySelect.setClearButtonVisible(true);
        categorySelect.addValueChangeListener(e -> {
            refreshDefaultButtonState();
            loadVehicleData();
        });

        preselectSavedCategory(expenseCategories);
        refreshDefaultButtonState();

        selectorLayout.add(categorySelect, saveDefaultCategoryButton);
        return selectorLayout;
    }

    private void preselectSavedCategory(List<Category> expenseCategories) {
        Long savedCategoryId = currentUser.getDefaultVehicleCategoryId();
        if (savedCategoryId == null) {
            return;
        }

        expenseCategories.stream()
                .filter(c -> savedCategoryId.equals(c.getId()))
                .findFirst()
                .ifPresent(categorySelect::setValue);
    }

    private void saveDefaultCategory() {
        Category selected = categorySelect.getValue();
        if (selected == null) {
            com.cuenti.app.views.components.UiNotifier.error(getTranslation("vehicles.save_default_none"));
            return;
        }

        userService.updateDefaultVehicleCategory(currentUser, selected.getId());
        currentUser.setDefaultVehicleCategoryId(selected.getId());
        refreshDefaultButtonState();

        com.cuenti.app.views.components.UiNotifier.success(getTranslation("vehicles.save_default_success"));
    }

    private void refreshDefaultButtonState() {
        if (saveDefaultCategoryButton == null) {
            return;
        }

        Category selected = categorySelect.getValue();
        Long currentDefault = currentUser.getDefaultVehicleCategoryId();
        boolean isAlreadyDefault = selected != null && selected.getId() != null && selected.getId().equals(currentDefault);
        saveDefaultCategoryButton.setEnabled(selected != null && !isAlreadyDefault);
    }

    private void configureGrid() {
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        grid.setSizeFull();

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        // Date
        grid.addComponentColumn(e -> {
            Span s = new Span(e.getDate().format(dateFormatter));
            s.getStyle().set("font-size", "var(--aura-font-size-s)").set("color", "var(--vaadin-text-color-secondary)");
            return s;
        }).setHeader(getTranslation("vehicles.date")).setAutoWidth(true).setSortable(true)
                .setComparator(java.util.Comparator.comparing(FuelEntry::getDate));

        // Odometer
        com.vaadin.flow.component.grid.Grid.Column<FuelEntry> odoCol =
        grid.addComponentColumn(e -> {
            Span s = new Span(e.getOdometer() != null ? e.getOdometer().setScale(0, RoundingMode.HALF_UP) + " km" : "—");
            s.getStyle().set("font-size", "var(--aura-font-size-s)").set("color", "var(--vaadin-text-color-secondary)");
            return s;
        }).setHeader(getTranslation("vehicles.odometer")).setAutoWidth(true).setSortable(true)
                .setComparator(java.util.Comparator.comparing(e -> e.getOdometer() != null ? e.getOdometer() : BigDecimal.ZERO));

        // Distance
        grid.addComponentColumn(e -> {
                    if (e.getDistance() == null) { Span s = new Span("—"); s.getStyle().set("color", "var(--vaadin-text-color-disabled)"); return s; }
                    Span s = new Span(e.getDistance().setScale(0, RoundingMode.HALF_UP) + " km");
                    s.getStyle().set("font-size", "var(--aura-font-size-s)");
                    return s;
                }).setHeader(getTranslation("vehicles.distance")).setAutoWidth(true).setSortable(true)
                .setComparator(java.util.Comparator.comparing(e -> e.getDistance() != null ? e.getDistance() : BigDecimal.ZERO));

        // Liters
        grid.addComponentColumn(e -> {
            Span s = new Span(e.getLiters() != null ? e.getLiters().setScale(2, RoundingMode.HALF_UP) + " L" : "—");
            s.getStyle().set("font-size", "var(--aura-font-size-s)");
            return s;
        }).setHeader(getTranslation("vehicles.liters")).setAutoWidth(true).setSortable(true)
                .setComparator(java.util.Comparator.comparing(e -> e.getLiters() != null ? e.getLiters() : BigDecimal.ZERO));

        // Amount
        grid.addComponentColumn(e -> {
            BigDecimal converted = exchangeRateService.convert(e.getAmount(), e.getCurrency(), currentUser.getDefaultCurrency());
            Span s = new Span(formatCurrency(converted));
            s.getStyle().set("font-weight", "700").set("font-size", "var(--aura-font-size-s)").set("color", "var(--aura-red)");
            return s;
        }).setHeader(getTranslation("vehicles.amount")).setAutoWidth(true).setSortable(true)
                .setComparator(java.util.Comparator.comparing(FuelEntry::getAmount));

        // Price / L
        com.vaadin.flow.component.grid.Grid.Column<FuelEntry> pplCol =
        grid.addComponentColumn(e -> {
            Span s = new Span(e.getPricePerLiter() != null ? formatCurrency(e.getPricePerLiter()) + "/L" : "—");
            s.getStyle().set("font-size", "var(--aura-font-size-s)").set("color", "var(--vaadin-text-color-secondary)");
            return s;
        }).setHeader(getTranslation("vehicles.price_per_liter")).setAutoWidth(true).setSortable(true)
                .setComparator(java.util.Comparator.comparing(e -> e.getPricePerLiter() != null ? e.getPricePerLiter() : BigDecimal.ZERO));

        // Consumption — coloured pill
        grid.addComponentColumn(e -> {
            if (e.getConsumption() == null) { Span s = new Span("—"); s.getStyle().set("color", "var(--vaadin-text-color-disabled)"); return s; }
            Span s = new Span(e.getConsumption() + " L/100km");
            // Low consumption = green (< 6), medium = warning (6-9), high = red (> 9)
            String color = e.getConsumption().compareTo(BigDecimal.valueOf(6)) < 0
                    ? "var(--aura-green)"
                    : e.getConsumption().compareTo(BigDecimal.valueOf(9)) < 0
                            ? "var(--aura-orange)"
                            : "var(--aura-red)";
            s.getStyle()
                    .set("font-size", "var(--aura-font-size-xs)").set("font-weight", "700")
                    .set("padding", "2px 8px").set("border-radius", "99px")
                    .set("background", color + "1a") // 10% tint
                    .set("color", color).set("white-space", "nowrap");
            return s;
        }).setHeader(getTranslation("vehicles.consumption")).setAutoWidth(true).setSortable(true)
                .setComparator(java.util.Comparator.comparing(e -> e.getConsumption() != null ? e.getConsumption() : BigDecimal.ZERO));

        // fullTank
        com.vaadin.flow.component.grid.Grid.Column<FuelEntry> fullTankCol =
        grid.addComponentColumn(e -> {
            if (e.isFullTank()) {
                Icon icon = VaadinIcon.CHECK.create();
                icon.setColor("var(--aura-green)");
                return icon;
            } else {
                Span dash = new Span("—");
                dash.getStyle().set("color", "var(--vaadin-text-color-disabled)");
                return dash;
            }
        })
        .setHeader(getTranslation("vehicles.full_tank"))
        //.setAutoWidth(true)
        .setWidth("80px")
        .setFlexGrow(0)
        .setSortable(true)
        .setComparator(FuelEntry::isFullTank);

        // Station
        com.vaadin.flow.component.grid.Grid.Column<FuelEntry> stationCol =
        grid.addComponentColumn(e -> {
                    Span s = new Span(e.getStation() != null ? e.getStation() : "—");
                    s.getStyle().set("font-weight", "500").set("font-size", "var(--aura-font-size-s)");
                    return s;
                }).setHeader(getTranslation("vehicles.station")).setFlexGrow(1).setSortable(true)
                .setComparator(java.util.Comparator.comparing(e -> e.getStation() != null ? e.getStation() : ""));

        // Phones: keep date/distance/liters/amount/consumption
        com.cuenti.app.views.components.ResponsiveGridColumns.hideBelow(768, grid,
                java.util.List.of(odoCol, pplCol, fullTankCol, stationCol));

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
            FuelEntry entry = VehicleReportService.parseFuelEntry(t, currentUser.getDefaultCurrency());
            if (entry != null) fuelEntries.add(entry);
        }

        calculateDerivedValues();
        renderSummary();
        grid.setItems(fuelEntries.stream().sorted(Comparator.comparing(FuelEntry::getDate).reversed()).toList());
    }

    /** Liters/distance actually attributed to consumption figures (drives the averages). */
    private BigDecimal attributedLiters = BigDecimal.ZERO;
    private BigDecimal attributedDistance = BigDecimal.ZERO;

    private void calculateDerivedValues() {
        BigDecimal[] attributed = VehicleReportService.computeDerivedValues(fuelEntries);
        attributedLiters = attributed[0];
        attributedDistance = attributed[1];
    }

    private void renderSummary() {
        if (fuelEntries.isEmpty()) {
            summaryContainer.add(new EmptyStateNotice(
                    VaadinIcon.DROP, getTranslation("vehicles.no_data"), null));
            return;
        }

        BigDecimal totalLiters   = fuelEntries.stream().filter(e -> e.getLiters() != null).map(FuelEntry::getLiters).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount   = fuelEntries.stream().map(e -> exchangeRateService.convert(e.getAmount(), e.getCurrency(), currentUser.getDefaultCurrency())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDistance = fuelEntries.stream().filter(e -> e.getDistance() != null).map(FuelEntry::getDistance).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Average from liters actually attributed to measured distances
        // (excludes the first fill, which has no distance to attribute)
        BigDecimal avgConsumption = attributedDistance.compareTo(BigDecimal.ZERO) > 0
                ? attributedLiters.divide(attributedDistance, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        // Price per liter only over entries whose liters are known
        BigDecimal literAmount = fuelEntries.stream()
                .filter(e -> e.getLiters() != null && e.getLiters().compareTo(BigDecimal.ZERO) > 0)
                .map(e -> exchangeRateService.convert(e.getAmount(), e.getCurrency(), currentUser.getDefaultCurrency()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgPricePerL  = totalLiters.compareTo(BigDecimal.ZERO) > 0
                ? literAmount.divide(totalLiters, 3, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        // Summary cards row
        FlexLayout summaryLayout = new FlexLayout();
        summaryLayout.setWidthFull();
        summaryLayout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        summaryLayout.getStyle().set("gap", "var(--vaadin-gap-m)");

        summaryLayout.add(
                createSummaryCard(getTranslation("vehicles.total_fillups"),  String.valueOf(fuelEntries.size()),                                   VaadinIcon.DROP, "var(--aura-accent-color)"),
                createSummaryCard(getTranslation("vehicles.total_liters"),   totalLiters.setScale(2, RoundingMode.HALF_UP) + " L",                 VaadinIcon.FILL,  "var(--aura-accent-color)"),
                createSummaryCard(getTranslation("vehicles.total_cost"),     formatCurrency(totalAmount),                                          VaadinIcon.MONEY,  "var(--aura-red)"),
                createSummaryCard(getTranslation("vehicles.total_distance"), totalDistance.setScale(0, RoundingMode.HALF_UP) + " km",              VaadinIcon.ROAD,   "var(--aura-green)"),
                createSummaryCard(getTranslation("vehicles.avg_consumption"),avgConsumption + " L/100km",                                          VaadinIcon.DASHBOARD, consumptionColor(avgConsumption)),
                createSummaryCard(getTranslation("vehicles.avg_price_per_liter"), avgPricePerL.compareTo(BigDecimal.ZERO) > 0 ? formatCurrency(avgPricePerL) + "/L" : "—", VaadinIcon.TAG, "var(--vaadin-text-color-secondary)")
        );

        // Consumption trend mini chart
        Div trendSection = new Div();
        trendSection.setWidthFull();
        trendSection.getStyle()
                .set("border-top", "1px solid var(--cuenti-divider)")
                .set("padding", "var(--vaadin-gap-m) 0 0")
                .set("box-sizing", "border-box")
                .set("margin-top", "var(--vaadin-gap-s)");

        Span trendTitle = new Span(getTranslation("vehicles.consumption_trend").toUpperCase());
        trendTitle.getStyle()
                .set("font-size", "10px").set("font-weight", "700").set("letter-spacing", "0.08em")
                .set("color", "var(--vaadin-text-color-secondary)").set("display", "block")
                .set("margin-bottom", "var(--vaadin-gap-s)");
        trendSection.add(trendTitle);
        renderConsumptionChart(trendSection);

        summaryContainer.add(summaryLayout, trendSection);
    }

    private String consumptionColor(BigDecimal c) {
        if (c.compareTo(BigDecimal.ZERO) == 0) return "var(--vaadin-text-color-secondary)";
        if (c.compareTo(BigDecimal.valueOf(6)) < 0) return "var(--aura-green)";
        if (c.compareTo(BigDecimal.valueOf(9)) < 0) return "var(--aura-orange)";
        return "var(--aura-red)";
    }

    private void renderConsumptionChart(Div container) {
        List<FuelEntry> withConsumption = fuelEntries.stream()
                .filter(e -> e.getConsumption() != null)
                .sorted(java.util.Comparator.comparing(FuelEntry::getDate))
                .toList();
        if (withConsumption.isEmpty()) {
            Span none = new Span(getTranslation("vehicles.no_consumption_data"));
            none.getStyle().set("font-size", "var(--aura-font-size-s)").set("color", "var(--vaadin-text-color-secondary)");
            container.add(none);
            return;
        }

        BigDecimal maxC = withConsumption.stream().map(FuelEntry::getConsumption).max(BigDecimal::compareTo).orElse(BigDecimal.TEN);

        Div chartArea = new Div();
        chartArea.getStyle()
                .set("display", "flex").set("align-items", "flex-end").set("gap", "6px")
                .set("height", "80px").set("overflow-x", "auto").set("padding-bottom", "2px");

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM");
        for (FuelEntry e : withConsumption) {
            double pct = maxC.compareTo(BigDecimal.ZERO) > 0
                    ? e.getConsumption().divide(maxC, 4, RoundingMode.HALF_UP).doubleValue() * 68 : 2;
            String color = consumptionColor(e.getConsumption());

            Div barGroup = new Div();
            barGroup.getStyle().set("display", "flex").set("flex-direction", "column")
                    .set("align-items", "center").set("gap", "3px").set("min-width", "36px");

            Span value = new Span(e.getConsumption().toPlainString());
            value.getStyle().set("font-size", "9px").set("font-weight", "700")
                    .set("color", color).set("white-space", "nowrap");

            Div bar = new Div();
            bar.getStyle()
                    .set("width", "24px").set("height", Math.max(4, pct) + "px")
                    .set("background", "linear-gradient(to top, " + color + ", " + color + "88)")
                    .set("border-radius", "4px 4px 0 0");

            Span lbl = new Span(e.getDate().format(fmt));
            lbl.getStyle().set("font-size", "9px").set("color", "var(--vaadin-text-color-secondary)")
                    .set("white-space", "nowrap");

            barGroup.add(value, bar, lbl);
            chartArea.add(barGroup);
        }
        container.add(chartArea);
    }

    private Div createSummaryCard(String title, String value, VaadinIcon iconType, String accentColor) {
        Div card = new Div();
        card.addClassName("card");
        card.getStyle()
                .set("flex", "1 1 150px").set("min-width", "140px")
                .set("border-left", "4px solid " + accentColor)
                .set("display", "flex").set("flex-direction", "column").set("gap", "var(--vaadin-gap-xs)");

        Span titleSpan = new Span(title.toUpperCase());
        titleSpan.addClassName("text-overline");

        Span valueSpan = new Span(value);
        valueSpan.getStyle()
                .set("font-size", "var(--aura-font-size-xl)").set("font-weight", "700")
                .set("color", accentColor).set("line-height", "1.1");

        card.add(titleSpan, valueSpan);
        return card;
    }

    private void renderEmptyState() {
        summaryContainer.removeAll();
        summaryContainer.add(new EmptyStateNotice(
                VaadinIcon.CAR, getTranslation("vehicles.select_placeholder"), null));
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "-";
        return com.cuenti.app.util.CurrencyFormat.format(amount, currentUser.getDefaultCurrency(),
                Locale.forLanguageTag(currentUser.getLocale()));
    }
}
