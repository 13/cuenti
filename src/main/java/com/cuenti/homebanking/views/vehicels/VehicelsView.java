package com.cuenti.homebanking.views.vehicels;

import com.cuenti.homebanking.data.Category;
import com.cuenti.homebanking.data.Transaction;
import com.cuenti.homebanking.data.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.services.CategoryService;
import com.cuenti.homebanking.services.TransactionService;
import com.cuenti.homebanking.services.UserService;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Vehicle fuel tracking and consumption statistics view.
 * Parses transaction memos to extract odometer readings and fuel amounts.
 * Memo format: d=XXXXXX v~XX.XX (optional price/liter info)
 * where d= is odometer in km and v~ is liters fueled.
 */
@PageTitle("Vehicles")
@Route("vehicles")
@Menu(order = 4, icon = LineAwesomeIconUrl.CAR_SOLID)
@PermitAll
public class VehicelsView extends VerticalLayout {

    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final User currentUser;

    private ComboBox<Category> categorySelect;
    private final Div summaryContainer = new Div();
    private final Grid<FuelEntry> grid = new Grid<>();
    private List<FuelEntry> fuelEntries = new ArrayList<>();

    private static final Pattern ODOMETER_PATTERN = Pattern.compile("d[=:]\\s*(\\d+(?:[.,]\\d+)?)");
    private static final Pattern LITERS_PATTERN = Pattern.compile("[vl][~=:]\\s*(\\d+(?:[.,]\\d+)?)");

    public VehicelsView(TransactionService transactionService, CategoryService categoryService,
                        UserService userService, SecurityUtils securityUtils) {
        this.transactionService = transactionService;
        this.categoryService = categoryService;

        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        this.currentUser = userService.findByUsername(username);

        addClassName("vehicles-view");
        setSpacing(true);
        setPadding(true);
        setMaxWidth("1200px");
        getStyle().set("margin", "0 auto");

        setupUI();
    }

    private void setupUI() {
        H2 title = new H2(getTranslation("vehicles.title"));
        title.getStyle().set("margin-top", "0").set("color", "var(--lumo-primary-text-color)");

        categorySelect = new ComboBox<>(getTranslation("vehicles.category"));
        categorySelect.setItemLabelGenerator(Category::getFullName);
        categorySelect.setClearButtonVisible(true);

        // Load expense categories that might be fuel-related
        List<Category> categories = categoryService.getAllCategories().stream()
                .filter(c -> c.getType() == Category.CategoryType.EXPENSE)
                .collect(Collectors.toList());
        categorySelect.setItems(categories);

        // Auto-select if there's a "Fuel" or "Car" category
        categories.stream()
                .filter(c -> c.getName().toLowerCase().contains("fuel") ||
                            c.getName().toLowerCase().contains("gas") ||
                            c.getName().toLowerCase().contains("tankstelle"))
                .findFirst()
                .ifPresent(categorySelect::setValue);

        categorySelect.addValueChangeListener(e -> loadData());

        HorizontalLayout toolbar = new HorizontalLayout(categorySelect);
        toolbar.setAlignItems(Alignment.END);

        summaryContainer.setWidthFull();
        summaryContainer.getStyle().set("margin-bottom", "var(--lumo-space-m)");

        setupGrid();

        Div card = new Div();
        card.setSizeFull();
        card.getStyle()
                .set("background-color", "var(--lumo-base-color)")
                .set("border-radius", "16px")
                .set("padding", "var(--lumo-space-l)")
                .set("box-shadow", "var(--lumo-box-shadow-m)")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("box-sizing", "border-box");
        card.add(toolbar, summaryContainer, grid);

        add(title, card);
        expand(card);

        loadData();
    }

    private void setupGrid() {
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        grid.addColumn(fe -> fe.date != null ? fe.date.format(formatter) : "-")
                .setHeader(getTranslation("transactions.date")).setAutoWidth(true).setSortable(true);
        grid.addColumn(fe -> fe.payee)
                .setHeader(getTranslation("transactions.payee")).setAutoWidth(true);
        grid.addColumn(fe -> fe.odometer != null ? String.format("%.0f km", fe.odometer) : "-")
                .setHeader(getTranslation("vehicles.odometer")).setAutoWidth(true);
        grid.addColumn(fe -> fe.liters != null ? String.format("%.2f L", fe.liters) : "-")
                .setHeader(getTranslation("vehicles.liters")).setAutoWidth(true);
        grid.addColumn(fe -> fe.distance != null ? String.format("%.0f km", fe.distance) : "-")
                .setHeader(getTranslation("vehicles.distance")).setAutoWidth(true);
        grid.addColumn(fe -> fe.consumption != null ? String.format("%.2f L/100km", fe.consumption) : "-")
                .setHeader(getTranslation("vehicles.consumption")).setAutoWidth(true);
        grid.addColumn(fe -> String.format("€%.2f", fe.amount))
                .setHeader(getTranslation("transactions.amount")).setAutoWidth(true);
    }

    private void loadData() {
        fuelEntries.clear();
        summaryContainer.removeAll();

        Category selectedCategory = categorySelect.getValue();
        if (selectedCategory == null) {
            grid.setItems(fuelEntries);
            summaryContainer.add(new Span("Select a fuel category to view vehicle statistics"));
            return;
        }

        List<Transaction> transactions = transactionService.getTransactionsByUser(currentUser).stream()
                .filter(t -> t.getCategory() != null && t.getCategory().getId().equals(selectedCategory.getId()))
                .sorted(Comparator.comparing(Transaction::getTransactionDate))
                .collect(Collectors.toList());

        Double previousOdometer = null;
        for (Transaction t : transactions) {
            FuelEntry entry = parseTransaction(t);

            // Calculate distance from previous odometer reading
            if (entry.odometer != null && previousOdometer != null) {
                entry.distance = entry.odometer - previousOdometer;
                if (entry.liters != null && entry.distance > 0) {
                    entry.consumption = (entry.liters / entry.distance) * 100;
                }
            }

            if (entry.odometer != null) {
                previousOdometer = entry.odometer;
            }

            fuelEntries.add(entry);
        }

        // Reverse to show newest first
        Collections.reverse(fuelEntries);
        grid.setItems(fuelEntries);

        // Calculate summary
        renderSummary(transactions);
    }

    private FuelEntry parseTransaction(Transaction t) {
        FuelEntry entry = new FuelEntry();
        entry.date = t.getTransactionDate().toLocalDate();
        entry.payee = t.getPayee();
        entry.amount = t.getAmount().doubleValue();

        String memo = t.getMemo();
        if (memo != null) {
            Matcher odometerMatcher = ODOMETER_PATTERN.matcher(memo.toLowerCase());
            if (odometerMatcher.find()) {
                entry.odometer = parseDouble(odometerMatcher.group(1));
            }

            Matcher litersMatcher = LITERS_PATTERN.matcher(memo.toLowerCase());
            if (litersMatcher.find()) {
                entry.liters = parseDouble(litersMatcher.group(1));
            }
        }

        return entry;
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value.replace(",", "."));
        } catch (Exception e) {
            return null;
        }
    }

    private void renderSummary(List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            summaryContainer.add(new Span("No fuel transactions found for this category"));
            return;
        }

        BigDecimal totalSpent = transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Double totalLiters = fuelEntries.stream()
                .filter(fe -> fe.liters != null)
                .mapToDouble(fe -> fe.liters)
                .sum();

        Double totalDistance = fuelEntries.stream()
                .filter(fe -> fe.distance != null)
                .mapToDouble(fe -> fe.distance)
                .sum();

        Double avgConsumption = totalDistance > 0 ? (totalLiters / totalDistance) * 100 : 0;

        FlexLayout summaryLayout = new FlexLayout();
        summaryLayout.setWidthFull();
        summaryLayout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        summaryLayout.getStyle().set("gap", "var(--lumo-space-m)");

        summaryLayout.add(createSummaryCard("Total Spent", String.format("€%.2f", totalSpent)));
        summaryLayout.add(createSummaryCard("Total Fuel", String.format("%.1f L", totalLiters)));
        summaryLayout.add(createSummaryCard("Total Distance", String.format("%.0f km", totalDistance)));
        summaryLayout.add(createSummaryCard("Avg Consumption", String.format("%.2f L/100km", avgConsumption)));
        summaryLayout.add(createSummaryCard("Fill-ups", String.valueOf(transactions.size())));

        summaryContainer.add(summaryLayout);
    }

    private Div createSummaryCard(String title, String value) {
        Div card = new Div();
        card.getStyle()
                .set("flex", "1 1 150px")
                .set("min-width", "120px")
                .set("background-color", "var(--lumo-contrast-5pct)")
                .set("border-radius", "12px")
                .set("padding", "var(--lumo-space-m)");

        Span titleSpan = new Span(title);
        titleSpan.getStyle().set("font-size", "12px").set("color", "var(--lumo-secondary-text-color)").set("display", "block");

        H4 valueSpan = new H4(value);
        valueSpan.getStyle().set("margin", "var(--lumo-space-xs) 0 0 0");

        card.add(titleSpan, valueSpan);
        return card;
    }

    /**
     * Data class for fuel entries parsed from transactions
     */
    public static class FuelEntry {
        public java.time.LocalDate date;
        public String payee;
        public Double odometer;
        public Double liters;
        public Double distance;
        public Double consumption;
        public Double amount;
    }
}
