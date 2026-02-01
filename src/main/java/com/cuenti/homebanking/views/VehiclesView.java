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
import com.vaadin.flow.component.html.Span;
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
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Vehicle fuel tracking and consumption statistics view.
 */
@Route(value = "vehicles", layout = MainLayout.class)
@PageTitle("Vehicles | Cuenti")
@PermitAll
public class VehiclesView extends VerticalLayout {

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
        setPadding(true);
        setSpacing(true);
        getStyle().set("background-color", "var(--lumo-contrast-5pct)");

        setupUI();
    }

    private void setupUI() {
        H2 title = new H2(getTranslation("vehicles.title"));
        title.getStyle().set("margin-top", "0").set("color", "var(--lumo-primary-text-color)");

        HorizontalLayout filters = createFilters();
        HorizontalLayout categorySelector = createCategorySelector();
        
        HorizontalLayout toolbar = new HorizontalLayout(filters, categorySelector);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.END);
        toolbar.setSpacing(true);

        summaryContainer.setWidthFull();
        configureGrid();

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
        card.add(toolbar, summaryContainer, grid);
        add(title, card);
        expand(card);
    }

    private HorizontalLayout createFilters() {
        HorizontalLayout filterLayout = new HorizontalLayout();
        filterLayout.setAlignItems(Alignment.END);
        filterLayout.setSpacing(true);

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
        selectorLayout.setAlignItems(Alignment.END);

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
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setSizeFull();

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        grid.addColumn(e -> e.date.format(dateFormatter)).setHeader(getTranslation("vehicles.date")).setAutoWidth(true);
        grid.addColumn(e -> e.odometer != null ? e.odometer.setScale(0, RoundingMode.HALF_UP) + " km" : "-").setHeader(getTranslation("vehicles.odometer")).setAutoWidth(true);
        grid.addColumn(e -> e.distance != null ? e.distance.setScale(0, RoundingMode.HALF_UP) + " km" : "-").setHeader(getTranslation("vehicles.distance")).setAutoWidth(true);
        grid.addColumn(e -> e.liters != null ? e.liters.setScale(2, RoundingMode.HALF_UP) + " L" : "-").setHeader(getTranslation("vehicles.liters")).setAutoWidth(true);
        grid.addColumn(e -> formatCurrency(exchangeRateService.convert(e.amount, e.currency, currentUser.getDefaultCurrency()))).setHeader(getTranslation("vehicles.amount")).setAutoWidth(true);
        grid.addColumn(e -> e.pricePerLiter != null ? formatCurrency(e.pricePerLiter) + "/L" : "-").setHeader(getTranslation("vehicles.price_per_liter")).setAutoWidth(true);
        grid.addColumn(e -> e.consumption != null ? e.consumption + " L/100km" : "-").setHeader(getTranslation("vehicles.consumption")).setAutoWidth(true);
        grid.addColumn(e -> e.station != null ? e.station : "-").setHeader(getTranslation("vehicles.station")).setFlexGrow(1);
    }

    private void loadVehicleData() {
        summaryContainer.removeAll();
        fuelEntries.clear();

        Category selectedCategory = categorySelect.getValue();
        if (selectedCategory == null) {
            grid.setItems(Collections.emptyList());
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
        if (fuelEntries.isEmpty()) return;

        BigDecimal totalLiters = fuelEntries.stream().filter(e -> e.liters != null).map(e -> e.liters).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = fuelEntries.stream().map(e -> exchangeRateService.convert(e.amount, e.currency, currentUser.getDefaultCurrency())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDistance = fuelEntries.stream().filter(e -> e.distance != null).map(e -> e.distance).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgConsumption = (totalDistance.compareTo(BigDecimal.ZERO) > 0 && totalLiters.compareTo(BigDecimal.ZERO) > 0)
                ? totalLiters.divide(totalDistance, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        FlexLayout summaryLayout = new FlexLayout();
        summaryLayout.setWidthFull();
        summaryLayout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        summaryLayout.getStyle().set("gap", "var(--lumo-space-m)");

        summaryLayout.add(
                createSummaryCard(getTranslation("vehicles.total_fillups"), String.valueOf(fuelEntries.size())),
                createSummaryCard(getTranslation("vehicles.total_liters"), totalLiters.setScale(2, RoundingMode.HALF_UP) + " L"),
                createSummaryCard(getTranslation("vehicles.total_cost"), formatCurrency(totalAmount)),
                createSummaryCard(getTranslation("vehicles.total_distance"), totalDistance.setScale(0, RoundingMode.HALF_UP) + " km"),
                createSummaryCard(getTranslation("vehicles.avg_consumption"), avgConsumption + " L/100km")
        );
        summaryContainer.add(summaryLayout);
    }

    private Div createSummaryCard(String title, String value) {
        Div card = new Div();
        card.getStyle()
                .set("flex", "1 1 150px")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "8px")
                .set("padding", "var(--lumo-space-m)");

        Span titleSpan = new Span(title);
        titleSpan.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)").set("display", "block");

        Span valueSpan = new Span(value);
        valueSpan.getStyle().set("font-size", "var(--lumo-font-size-l)").set("font-weight", "bold").set("display", "block").set("margin-top", "var(--lumo-space-xs)");

        card.add(titleSpan, valueSpan);
        return card;
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
