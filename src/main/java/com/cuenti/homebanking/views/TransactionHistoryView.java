package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.*;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Route(value = "transactions", layout = MainLayout.class)
@PageTitle("Transactions | Cuenti")
@PermitAll
public class TransactionHistoryView extends VerticalLayout {

    private final TransactionService transactionService;
    private final AccountService accountService;
    private final UserService userService;
    private final ExchangeRateService exchangeRateService;
    private final CategoryService categoryService;
    private final AssetService assetService;
    private final PayeeService payeeService;
    private final TagService tagService;
    private final SecurityUtils securityUtils;
    private final User currentUser;

    private final Grid<Transaction> grid = new Grid<>(Transaction.class, false);
    private final TextField searchField = new TextField();
    private final ComboBox<Account> accountSelector = new ComboBox<>();
    private final DatePicker dateFrom = new DatePicker();
    private final DatePicker dateTo = new DatePicker();
    private final Tabs typeTabs = new Tabs();
    private Transaction.TransactionType selectedTypeFilter = null;

    private List<Transaction> allAccountTransactions = new ArrayList<>();
    private Map<Long, BigDecimal> balanceCache = new HashMap<>();

    public TransactionHistoryView(TransactionService transactionService, AccountService accountService,
                                  UserService userService, ExchangeRateService exchangeRateService, 
                                  CategoryService categoryService, AssetService assetService,
                                  PayeeService payeeService, TagService tagService, SecurityUtils securityUtils) {
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.userService = userService;
        this.exchangeRateService = exchangeRateService;
        this.categoryService = categoryService;
        this.assetService = assetService;
        this.payeeService = payeeService;
        this.tagService = tagService;
        this.securityUtils = securityUtils;

        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        this.currentUser = userService.findByUsername(username);

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("background-color", "var(--lumo-contrast-5pct)");
        
        setupUI();
        refreshGrid();
    }

    private void setupUI() {
        H2 title = new H2(getTranslation("transactions.title"));
        title.getStyle().set("margin-top", "0").set("color", "var(--lumo-primary-text-color)");

        accountSelector.setPlaceholder(getTranslation("dialog.account"));
        accountSelector.setItems(accountService.getAccountsByUser(currentUser));
        accountSelector.setItemLabelGenerator(Account::getAccountName);
        accountSelector.setClearButtonVisible(true);
        accountSelector.addValueChangeListener(e -> refreshGrid());
        accountSelector.setWidth("200px");

        LocalDate now = LocalDate.now();
        dateFrom.setPlaceholder(getTranslation("dialog.from"));
        dateFrom.setClearButtonVisible(true);
        dateFrom.addValueChangeListener(e -> updateFilters());
        dateFrom.setWidth("150px");
        dateFrom.setValue(now.with(TemporalAdjusters.firstDayOfMonth()));

        dateTo.setPlaceholder(getTranslation("dialog.to"));
        dateTo.setClearButtonVisible(true);
        dateTo.addValueChangeListener(e -> updateFilters());
        dateTo.setWidth("150px");
        dateTo.setValue(now.with(TemporalAdjusters.lastDayOfMonth()));

        searchField.setPlaceholder(getTranslation("transactions.search"));
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> updateFilters());
        searchField.setWidth("200px");

        Button addButton = new Button(getTranslation("transactions.new"), VaadinIcon.PLUS.create(), e -> openTransactionDialog(new Transaction()));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        setupTabs();

        HorizontalLayout toolbar = new HorizontalLayout(accountSelector, dateFrom, dateTo, typeTabs, searchField, addButton);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.expand(typeTabs);

        setupGrid();
        
        add(title);

        // Always use card layout for better UX
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
        card.add(toolbar, grid);
        add(card);
        expand(card);
    }

    private void setupTabs() {
        Tab all = new Tab(getTranslation("nav.history"));
        Tab expenses = new Tab(getTranslation("transaction.type.expense"));
        Tab income = new Tab(getTranslation("transaction.type.income"));
        Tab transfers = new Tab(getTranslation("transaction.type.transfer"));

        typeTabs.add(all, expenses, income, transfers);
        typeTabs.addSelectedChangeListener(event -> {
            Tab selectedTab = event.getSelectedTab();
            if (selectedTab == all) selectedTypeFilter = null;
            else if (selectedTab == expenses) selectedTypeFilter = Transaction.TransactionType.EXPENSE;
            else if (selectedTab == income) selectedTypeFilter = Transaction.TransactionType.INCOME;
            else if (selectedTab == transfers) selectedTypeFilter = Transaction.TransactionType.TRANSFER;
            updateFilters();
        });
    }

    private void updateFilters() {
        ListDataProvider<Transaction> dataProvider = (ListDataProvider<Transaction>) grid.getDataProvider();
        String filter = searchField.getValue().toLowerCase();
        Account selectedAccount = accountSelector.getValue();
        LocalDate from = dateFrom.getValue();
        LocalDate to = dateTo.getValue();
        
        dataProvider.setFilter(t -> {
            boolean accountMatch = selectedAccount == null || 
                                  (t.getFromAccount() != null && t.getFromAccount().getId().equals(selectedAccount.getId())) || 
                                  (t.getToAccount() != null && t.getToAccount().getId().equals(selectedAccount.getId()));
            
            boolean dateMatch = true;
            if (from != null && t.getTransactionDate().toLocalDate().isBefore(from)) dateMatch = false;
            if (to != null && t.getTransactionDate().toLocalDate().isAfter(to)) dateMatch = false;

            boolean typeMatch = selectedTypeFilter == null || t.getType() == selectedTypeFilter;
            
            boolean searchMatch = filter.isEmpty() || 
                                 (t.getPayee() != null && t.getPayee().toLowerCase().contains(filter)) ||
                                 (t.getMemo() != null && t.getMemo().toLowerCase().contains(filter)) ||
                                 (t.getCategory() != null && t.getCategory().getFullName().toLowerCase().contains(filter));
            
            return accountMatch && dateMatch && typeMatch && searchMatch;
        });
    }

    private void setupGrid() {
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();
        
        // 1. Icon Column
        grid.addColumn(new ComponentRenderer<>(t -> {
            Transaction.PaymentMethod pm = t.getPaymentMethod();
            boolean showIcon = (pm != null && pm != Transaction.PaymentMethod.NONE) || t.getType() == Transaction.TransactionType.TRANSFER;
            if (showIcon) {
                Icon icon = getPaymentIcon(t);
                icon.getStyle().set("font-size", "var(--lumo-font-size-s)");
                return icon;
            }
            return new Span();
        })).setHeader("").setAutoWidth(true).setFlexGrow(0);
        // 2. Date Column
        Grid.Column<Transaction> dateColumn = grid.addColumn(t -> 
                t.getTransactionDate().format(getDateTimeFormatter()))
                .setHeader(getTranslation("transactions.date"))
                .setSortable(true)
                .setComparator(Transaction::getTransactionDate)
                .setAutoWidth(true)
                .setFlexGrow(0);

        // 3. Payee Column
        grid.addColumn(Transaction::getPayee)
                .setHeader(getTranslation("transactions.payee"))
                .setSortable(true)
                .setAutoWidth(true);

        // 4. Category Column
        grid.addColumn(t -> t.getCategory() != null ? t.getCategory().getFullName() : "")
                .setHeader(getTranslation("transactions.category"))
                .setSortable(true)
                .setAutoWidth(true);

        // 5. Tags Column (Colored Tags)
        grid.addComponentColumn(t -> {
            HorizontalLayout hl = new HorizontalLayout();
            hl.setSpacing(true);
            if (t.getTags() != null && !t.getTags().isEmpty()) {
                for (String tagName : t.getTags().split(",")) {
                    Span badge = new Span(tagName.trim());
                    badge.getElement().getThemeList().add("badge pill");
                    String color = getTagColor(tagName);
                    badge.getStyle().set("background-color", color).set("color", "white");
                    hl.add(badge);
                }
            }
            return hl;
        }).setHeader(getTranslation("dialog.tags"))
                .setSortable(true)
                .setAutoWidth(true)
                .setFlexGrow(0);

        // 6. Expense Column
        grid.addComponentColumn(t -> {
            Account selected = accountSelector.getValue();
            boolean isExpensePerspective = (t.getType() == Transaction.TransactionType.EXPENSE) || 
                                           (t.getType() == Transaction.TransactionType.TRANSFER && (selected == null || (t.getFromAccount() != null && t.getFromAccount().getId().equals(selected.getId()))));
            
            if (isExpensePerspective) {
                Span s = new Span(formatCurrency(t.getAmount()));
                s.getStyle().set("color", "var(--lumo-error-text-color)");
                return s;
            }
            return new Span();
        }).setHeader(getTranslation("category.type.expense"))
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setSortable(true)
                .setAutoWidth(true)
                .setFlexGrow(0);

        // 7. Income Column
        grid.addComponentColumn(t -> {
            Account selected = accountSelector.getValue();
            boolean isIncomePerspective = (t.getType() == Transaction.TransactionType.INCOME) || 
                                          (t.getType() == Transaction.TransactionType.TRANSFER && (selected == null || (t.getToAccount() != null && t.getToAccount().getId().equals(selected.getId()))));
            
            if (isIncomePerspective) {
                Span s = new Span(formatCurrency(t.getAmount()));
                s.getStyle().set("color", "var(--lumo-success-text-color)");
                return s;
            }
            return new Span();
        }).setHeader(getTranslation("category.type.income"))
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setSortable(true)
                .setAutoWidth(true)
                .setFlexGrow(0);

        // 8. Dynamic Balance Column
        grid.addColumn(new ComponentRenderer<>(t -> new Span(formatCurrency(balanceCache.getOrDefault(t.getId(), BigDecimal.ZERO)))))
            .setHeader(getTranslation("accounts.balance"))
            .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setSortable(true)
                .setAutoWidth(true)
                .setFlexGrow(0);;

        // 9. Memo Column
        grid.addColumn(Transaction::getMemo)
                .setHeader(getTranslation("dialog.memo"))
                .setSortable(true)
                .setAutoWidth(true)
                .setFlexGrow(0);;

        // 10. Actions
        grid.addComponentColumn(t -> {
            HorizontalLayout hl = new HorizontalLayout();
            hl.setSpacing(false);
            hl.getStyle().set("gap", "var(--lumo-space-xs)");

            // Reordering arrows logic
            Account selected = accountSelector.getValue();
            if (selected != null) {
                LocalDate date = t.getTransactionDate().toLocalDate();
                // Get transactions for the same day in display order (descending - latest on top)
                List<Transaction> sameDay = allAccountTransactions.stream()
                        .filter(tr -> tr.getTransactionDate().toLocalDate().equals(date))
                        .sorted(Comparator.comparing(Transaction::getSortOrder).reversed()
                                .thenComparing(Transaction::getId))
                        .collect(Collectors.toList());

                if (sameDay.size() > 1) {
                    // Find index by ID, not by object reference
                    int index = -1;
                    for (int i = 0; i < sameDay.size(); i++) {
                        if (sameDay.get(i).getId().equals(t.getId())) {
                            index = i;
                            break;
                        }
                    }

                    if (index >= 0) {
                        final int currentIdx = index;
                        // Up arrow moves transaction earlier in the day (higher sortOrder)
                        Button upBtn = new Button(VaadinIcon.ARROW_UP.create(), e -> moveTransaction(t, -1));
                        upBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                        upBtn.setEnabled(currentIdx > 0);
                        upBtn.setTooltipText(getTranslation("transactions.move_up"));

                        // Down arrow moves transaction later in the day (lower sortOrder)
                        Button downBtn = new Button(VaadinIcon.ARROW_DOWN.create(), e -> moveTransaction(t, 1));
                        downBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                        downBtn.setEnabled(currentIdx < sameDay.size() - 1);
                        downBtn.setTooltipText(getTranslation("transactions.move_down"));

                        hl.add(upBtn, downBtn);
                    }
                }
            }

            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> openTransactionDialog(t));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            editBtn.setTooltipText(getTranslation("transactions.edit"));
            
            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e -> confirmDelete(t));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            deleteBtn.setTooltipText(getTranslation("transactions.delete"));
            
            hl.add(editBtn, deleteBtn);
            return hl;
        }).setHeader(getTranslation("transactions.actions")).setFrozenToEnd(true).setAutoWidth(true);

        grid.setHeightFull();
        // Grid items are pre-sorted in refreshGrid() with latest entries on top
    }

    private void confirmDelete(Transaction t) {
        Dialog confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle(getTranslation("dialog.confirm_delete"));

        String message = t.getPayee() != null && !t.getPayee().isEmpty()
            ? t.getPayee() + " - " + formatCurrency(t.getAmount())
            : formatCurrency(t.getAmount());
        Span content = new Span(getTranslation("dialog.confirm_delete_message") + " \"" + message + "\"?");
        confirmDialog.add(content);

        Button cancelBtn = new Button(getTranslation("dialog.cancel"), e -> confirmDialog.close());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button deleteBtn = new Button(getTranslation("transactions.delete"), e -> {
            transactionService.deleteTransaction(t);
            confirmDialog.close();
            refreshGrid();
            Notification.show(getTranslation("transactions.deleted"));
        });
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        confirmDialog.getFooter().add(cancelBtn, deleteBtn);
        confirmDialog.open();
    }

    private void moveTransaction(Transaction t, int visualDirection) {
        Account selected = accountSelector.getValue();
        if (selected == null) return;

        LocalDate date = t.getTransactionDate().toLocalDate();

        // Fetch fresh transactions from database for this account and date
        List<Transaction> sameDayTransactions = transactionService.getTransactionsByAccount(selected).stream()
                .filter(tr -> tr.getTransactionDate().toLocalDate().equals(date))
                .collect(Collectors.toList());

        if (sameDayTransactions.size() < 2) return;

        // First, normalize sortOrder values to ensure they are unique and sequential
        // Sort by current sortOrder (descending - highest first, which appears at top)
        sameDayTransactions.sort(Comparator.comparing(Transaction::getSortOrder).reversed()
                .thenComparing(Transaction::getId)); // Secondary sort by ID for consistency

        // Assign unique sequential sortOrder values (highest = top of list)
        for (int i = 0; i < sameDayTransactions.size(); i++) {
            sameDayTransactions.get(i).setSortOrder((sameDayTransactions.size() - i) * 10);
        }

        // Find current transaction by ID
        int currentIndex = -1;
        for (int i = 0; i < sameDayTransactions.size(); i++) {
            if (sameDayTransactions.get(i).getId().equals(t.getId())) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) return;

        int targetIndex = currentIndex + visualDirection;
        if (targetIndex < 0 || targetIndex >= sameDayTransactions.size()) return;

        // Swap the sortOrder values between current and target
        Transaction current = sameDayTransactions.get(currentIndex);
        Transaction target = sameDayTransactions.get(targetIndex);

        int tempOrder = current.getSortOrder();
        current.setSortOrder(target.getSortOrder());
        target.setSortOrder(tempOrder);

        // Save all transactions to persist the new order
        for (Transaction tr : sameDayTransactions) {
            transactionService.saveTransaction(tr);
        }

        // Refresh to show new order and recalculate balances
        refreshGrid();
    }

    private void refreshGrid() {
        Account selected = accountSelector.getValue();
        if (selected == null) {
            grid.setItems(Collections.emptyList());
            allAccountTransactions = Collections.emptyList();
            balanceCache.clear();
        } else {
            List<Transaction> rawTransactions = transactionService.getTransactionsByAccount(selected);

            // Calculate running balance cache (in chronological order)
            List<Transaction> sortedForBalance = new ArrayList<>(rawTransactions);
            sortedForBalance.sort(Comparator.comparing(Transaction::getTransactionDate)
                              .thenComparing(Transaction::getSortOrder));

            balanceCache.clear();
            BigDecimal currentBalance = selected.getStartBalance();
            for (Transaction t : sortedForBalance) {
                BigDecimal amount = t.getAmount();
                if (t.getType() == Transaction.TransactionType.INCOME && t.getToAccount() != null && t.getToAccount().getId().equals(selected.getId())) {
                    currentBalance = currentBalance.add(amount);
                } else if (t.getType() == Transaction.TransactionType.EXPENSE && t.getFromAccount() != null && t.getFromAccount().getId().equals(selected.getId())) {
                    currentBalance = currentBalance.subtract(amount);
                } else if (t.getType() == Transaction.TransactionType.TRANSFER) {
                    if (t.getFromAccount() != null && t.getFromAccount().getId().equals(selected.getId())) currentBalance = currentBalance.subtract(amount);
                    if (t.getToAccount() != null && t.getToAccount().getId().equals(selected.getId())) currentBalance = currentBalance.add(amount);
                }
                balanceCache.put(t.getId(), currentBalance);
            }

            // Store sorted transactions for display (descending order - latest on top)
            allAccountTransactions = new ArrayList<>(rawTransactions);
            allAccountTransactions.sort(Comparator.comparing(Transaction::getTransactionDate)
                              .thenComparing(Transaction::getSortOrder)
                              .reversed());

            grid.setItems(allAccountTransactions);
        }
        updateFilters();
    }

    private String getTagColor(String tag) {
        int hash = tag.hashCode();
        String[] colors = {"#3498db", "#e74c3c", "#2ecc71", "#f1c40f", "#9b59b6", "#1abc9c", "#e67e22", "#34495e"};
        return colors[Math.abs(hash) % colors.length];
    }

    private Icon getPaymentIcon(Transaction t) {
        // Prefer showing an icon for transfer transactions regardless of payment method
        if (t.getType() == Transaction.TransactionType.TRANSFER) {
            return VaadinIcon.EXCHANGE.create();
        }

        Transaction.PaymentMethod method = t.getPaymentMethod();
        if (method == null) return VaadinIcon.QUESTION.create();

        switch (method) {
            case DEBIT_CARD: return VaadinIcon.CREDIT_CARD.create();
            case CASH: return VaadinIcon.MONEY.create();
            case BANK_TRANSFER: return VaadinIcon.INSTITUTION.create();
            case STANDING_ORDER: return VaadinIcon.REFRESH.create();
            case ELECTRONIC_PAYMENT: return VaadinIcon.MOBILE.create();
            case FI_FEE: return VaadinIcon.INVOICE.create();
            case CARD_TRANSACTION: return VaadinIcon.CREDIT_CARD.create();
            case TRADE: return VaadinIcon.CHART_3D.create();
            case TRANSFER: return VaadinIcon.EXCHANGE.create();
            case REWARD: return VaadinIcon.GIFT.create();
            case INTEREST: return VaadinIcon.TRENDING_UP.create();
            default: return VaadinIcon.QUESTION.create();
        }
    }

    private void openTransactionDialog(Transaction transaction) {
        final Transaction[] currentFormTransaction = {transaction};
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(transaction.getId() == null ? getTranslation("dialog.add_transaction") : getTranslation("dialog.edit_transaction"));
        dialog.setWidth("600px");

        Tabs dialogTabs = new Tabs();
        Tab expenseTab = new Tab(getTranslation("transaction.type.expense"));
        Tab incomeTab = new Tab(getTranslation("transaction.type.income"));
        Tab transferTab = new Tab(getTranslation("transaction.type.transfer"));
        dialogTabs.add(expenseTab, incomeTab, transferTab);
        dialogTabs.setWidthFull();
        
        if (currentFormTransaction[0].getType() != null) {
            if (currentFormTransaction[0].getType() == Transaction.TransactionType.INCOME) dialogTabs.setSelectedTab(incomeTab);
            else if (currentFormTransaction[0].getType() == Transaction.TransactionType.TRANSFER) dialogTabs.setSelectedTab(transferTab);
            else dialogTabs.setSelectedTab(expenseTab);
        }

        DatePicker datePicker = new DatePicker(getTranslation("dialog.date"));
        datePicker.setLocale(getLocale());
        
        if (currentUser.getLocale().equals("de-DE")) {
            DatePicker.DatePickerI18n i18n = new DatePicker.DatePickerI18n();
            i18n.setDateFormat("dd.MM.yyyy");
            i18n.setMonthNames(List.of("Januar", "Februar", "März", "April", "Mai", "Juni", 
                                     "Juli", "August", "September", "Oktober", "November", "Dezember"));
            i18n.setWeekdays(List.of("Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"));
            i18n.setWeekdaysShort(List.of("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"));
            i18n.setToday("Heute");
            i18n.setCancel("Abbrechen");
            i18n.setFirstDayOfWeek(1);
            datePicker.setI18n(i18n);
        }
        
        datePicker.setValue(currentFormTransaction[0].getTransactionDate() != null ? currentFormTransaction[0].getTransactionDate().toLocalDate() : LocalDateTime.now().toLocalDate());

        BigDecimalField amountField = new BigDecimalField(getTranslation("dialog.amount"));
        amountField.setValue(currentFormTransaction[0].getAmount() != null ? currentFormTransaction[0].getAmount() : BigDecimal.ZERO);
        amountField.setRequiredIndicatorVisible(true);

        List<Account> userAccounts = accountService.getAccountsByUser(currentUser);
        ComboBox<Account> accountCombo = new ComboBox<>(getTranslation("dialog.account"));
        accountCombo.setItems(userAccounts);
        accountCombo.setItemLabelGenerator(Account::getAccountName);
        accountCombo.setRequired(true);
        
        if (currentFormTransaction[0].getId() == null && accountSelector.getValue() != null) {
            accountCombo.setValue(accountSelector.getValue());
        }

        ComboBox<Account> toAccountCombo = new ComboBox<>(getTranslation("dialog.to"));
        toAccountCombo.setItems(userAccounts);
        toAccountCombo.setItemLabelGenerator(Account::getAccountName);

        ComboBox<Transaction.PaymentMethod> paymentCombo = new ComboBox<>(getTranslation("dialog.payment_method"));
        paymentCombo.setItems(Transaction.PaymentMethod.values());
        paymentCombo.setItemLabelGenerator(pm -> {
            if (pm == Transaction.PaymentMethod.NONE) return getTranslation("dialog.none");
            return pm.getLabel();
        });
        paymentCombo.setValue(currentFormTransaction[0].getPaymentMethod() != null ? currentFormTransaction[0].getPaymentMethod() : Transaction.PaymentMethod.NONE);

        TextField numberField = new TextField(getTranslation("dialog.number"));
        numberField.setValue(currentFormTransaction[0].getNumber() != null ? currentFormTransaction[0].getNumber() : "");

        ComboBox<String> payeeCombo = new ComboBox<>(getTranslation("transactions.payee"));
        List<String> existingPayees = payeeService.getAllPayees().stream().map(Payee::getName).distinct().toList();
        payeeCombo.setItems(existingPayees);
        payeeCombo.setAllowCustomValue(true);
        payeeCombo.setValue(currentFormTransaction[0].getPayee());
        payeeCombo.addCustomValueSetListener(e -> payeeCombo.setValue(e.getDetail()));

        ComboBox<Category> categoryCombo = new ComboBox<>(getTranslation("transactions.category"));
        categoryCombo.setItemLabelGenerator(Category::getFullName);
        categoryCombo.setAllowCustomValue(true);
        categoryCombo.addCustomValueSetListener(e -> {
            String newCatName = e.getDetail();
            Category.CategoryType categoryType = dialogTabs.getSelectedTab() == incomeTab ? Category.CategoryType.INCOME : Category.CategoryType.EXPENSE;

            Category saved;
            // Check if the name contains ":" for Parent:Child format
            if (newCatName != null && newCatName.contains(":")) {
                String[] parts = newCatName.split(":", 2);
                if (parts.length == 2) {
                    String parentName = parts[0].trim();
                    String childName = parts[1].trim();

                    // Find or create parent category
                    Category parentCategory = categoryService.getAllCategories().stream()
                            .filter(c -> c.getName().equals(parentName) && c.getParent() == null && c.getType() == categoryType)
                            .findFirst()
                            .orElse(null);

                    if (parentCategory == null) {
                        // Create new parent category
                        parentCategory = Category.builder()
                                .name(parentName)
                                .type(categoryType)
                                .user(currentUser)
                                .parent(null)
                                .build();
                        parentCategory = categoryService.saveCategory(parentCategory);
                        Notification.show(getTranslation("categories.parent_created") + ": " + parentName, 3000, Notification.Position.MIDDLE);
                    }

                    // Create child category with parent
                    Category newCat = Category.builder()
                            .name(childName)
                            .type(categoryType)
                            .parent(parentCategory)
                            .user(currentUser)
                            .build();
                    saved = categoryService.saveCategory(newCat);
                } else {
                    // Fallback to simple category creation
                    Category newCat = Category.builder()
                            .name(newCatName)
                            .type(categoryType)
                            .user(currentUser)
                            .build();
                    saved = categoryService.saveCategory(newCat);
                }
            } else {
                // Simple category creation
                Category newCat = Category.builder()
                        .name(newCatName)
                        .type(categoryType)
                        .user(currentUser)
                        .build();
                saved = categoryService.saveCategory(newCat);
            }

            categoryCombo.setItems(getFilteredCategoriesFromTabs(dialogTabs, expenseTab, incomeTab));
            categoryCombo.setValue(saved);
        });

        ComboBox<Asset> assetCombo = new ComboBox<>(getTranslation("dialog.asset"));
        assetCombo.setItems(assetService.getAllAssets());
        assetCombo.setItemLabelGenerator(Asset::getSymbol);
        assetCombo.setValue(currentFormTransaction[0].getAsset());

        BigDecimalField unitsField = new BigDecimalField(getTranslation("dialog.units"));
        unitsField.setValue(currentFormTransaction[0].getUnits() != null ? currentFormTransaction[0].getUnits() : BigDecimal.ZERO);

        BigDecimalField unitPriceField = new BigDecimalField(getTranslation("dialog.unit_price"));

        TextArea memoField = new TextArea(getTranslation("dialog.memo"));
        memoField.setValue(currentFormTransaction[0].getMemo() != null ? currentFormTransaction[0].getMemo() : "");

        MultiSelectComboBox<Tag> tagsCombo = new MultiSelectComboBox<>(getTranslation("dialog.tags"));
        tagsCombo.setItems(tagService.getAllTags());
        tagsCombo.setItemLabelGenerator(Tag::getName);
        tagsCombo.setAllowCustomValue(true);
        if (currentFormTransaction[0].getTags() != null && !currentFormTransaction[0].getTags().isEmpty()) {
            Set<String> tagNames = new HashSet<>(Arrays.asList(currentFormTransaction[0].getTags().split(",")));
            tagsCombo.setValue(tagService.getAllTags().stream().filter(t -> tagNames.contains(t.getName())).collect(Collectors.toSet()));
        }
        tagsCombo.addCustomValueSetListener(e -> {
            String newTagName = e.getDetail();
            Tag newTag = Tag.builder().name(newTagName).build();
            tagService.saveTag(newTag);
            tagsCombo.setItems(tagService.getAllTags());
            Set<Tag> currentSelection = new HashSet<>(tagsCombo.getValue());
            currentSelection.add(newTag);
            tagsCombo.setValue(currentSelection);
        });

        Runnable updateVisibility = () -> {
            Tab selected = dialogTabs.getSelectedTab();
            boolean isTransfer = selected == transferTab;
            boolean isIncome = selected == incomeTab;
            
            toAccountCombo.setVisible(isTransfer);
            paymentCombo.setVisible(!isTransfer);
            accountCombo.setLabel(isTransfer ? getTranslation("dialog.from") : getTranslation("dialog.account"));
            
            categoryCombo.setItems(getFilteredCategoriesFromTabs(dialogTabs, expenseTab, incomeTab));

            boolean assetVisible = false;
            Account acc = accountCombo.getValue();
            Account toAcc = toAccountCombo.getValue();
            if (acc != null && acc.getAccountType() == Account.AccountType.ASSET) assetVisible = true;
            if (isTransfer && toAcc != null && toAcc.getAccountType() == Account.AccountType.ASSET) assetVisible = true;
            
            assetCombo.setVisible(assetVisible);
            unitsField.setVisible(assetVisible);
            unitPriceField.setVisible(assetVisible);
        };

        dialogTabs.addSelectedChangeListener(e -> updateVisibility.run());
        accountCombo.addValueChangeListener(e -> updateVisibility.run());
        toAccountCombo.addValueChangeListener(e -> updateVisibility.run());

        if (currentFormTransaction[0].getId() != null) {
            if (currentFormTransaction[0].getType() == Transaction.TransactionType.INCOME) {
                accountCombo.setValue(currentFormTransaction[0].getToAccount());
            } else {
                accountCombo.setValue(currentFormTransaction[0].getFromAccount());
                if (currentFormTransaction[0].getType() == Transaction.TransactionType.TRANSFER) {
                    toAccountCombo.setValue(currentFormTransaction[0].getToAccount());
                }
            }
            categoryCombo.setItems(getFilteredCategoriesFromTabs(dialogTabs, expenseTab, incomeTab));
            categoryCombo.setValue(currentFormTransaction[0].getCategory());
            
            // Set unit price if units exist
            if (currentFormTransaction[0].getUnits() != null && currentFormTransaction[0].getUnits().compareTo(BigDecimal.ZERO) != 0) {
                unitPriceField.setValue(currentFormTransaction[0].getAmount().divide(currentFormTransaction[0].getUnits(), 4, RoundingMode.HALF_UP));
            }
        }
        
        updateVisibility.run();

        FormLayout formLayout = new FormLayout(dialogTabs, datePicker, amountField, accountCombo, toAccountCombo, paymentCombo, numberField, payeeCombo, categoryCombo, assetCombo, unitsField, unitPriceField, memoField, tagsCombo);
        formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));
        formLayout.setColspan(dialogTabs, 2);
        formLayout.setColspan(memoField, 2);
        formLayout.setColspan(tagsCombo, 2);

        Button saveButton = new Button(transaction.getId() == null ? getTranslation("dialog.add") : getTranslation("dialog.save"), e -> {
            if (accountCombo.isEmpty()) {
                Notification.show(getTranslation("accounts.name_required"), 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            if (amountField.getValue() == null || amountField.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                Notification.show(getTranslation("dialog.amount_positive"), 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            saveFromTabs(currentFormTransaction[0], dialogTabs, expenseTab, incomeTab, transferTab, datePicker, amountField, accountCombo, toAccountCombo, paymentCombo, numberField, payeeCombo, categoryCombo, assetCombo, unitsField, memoField, tagsCombo);
            refreshGrid();
            dialog.close();
            Notification.show(getTranslation("transactions.saved"));
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button addKeepButton = new Button(getTranslation("dialog.add_keep"), e -> {
            if (accountCombo.isEmpty()) {
                Notification.show(getTranslation("accounts.name_required"), 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            if (amountField.getValue() == null || amountField.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                Notification.show(getTranslation("dialog.amount_positive"), 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            
            saveFromTabs(new Transaction(), dialogTabs, expenseTab, incomeTab, transferTab, datePicker, amountField, accountCombo, toAccountCombo, paymentCombo, numberField, payeeCombo, categoryCombo, assetCombo, unitsField, memoField, tagsCombo);
            refreshGrid();
            Notification.show(getTranslation("transactions.added"));
            
            currentFormTransaction[0] = new Transaction();
            dialog.setHeaderTitle(getTranslation("dialog.add_transaction"));
        });
        addKeepButton.setVisible(true);

        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());

        dialog.add(formLayout);
        dialog.getFooter().add(cancelButton, addKeepButton, saveButton);
        dialog.open();
    }

    private List<Category> getFilteredCategoriesFromTabs(Tabs tabs, Tab exp, Tab inc) {
        if (tabs.getSelectedTab() == exp) return categoryService.getCategoriesByType(Category.CategoryType.EXPENSE);
        if (tabs.getSelectedTab() == inc) return categoryService.getCategoriesByType(Category.CategoryType.INCOME);
        return categoryService.getAllCategories();
    }

    private void saveFromTabs(Transaction transaction, Tabs tabs, Tab exp, Tab inc, Tab transferTab, DatePicker datePicker, BigDecimalField amountField, ComboBox<Account> accountCombo, ComboBox<Account> toAccountCombo, ComboBox<Transaction.PaymentMethod> paymentCombo, TextField numberField, ComboBox<String> payeeCombo, ComboBox<Category> categoryCombo, ComboBox<Asset> assetCombo, BigDecimalField unitsField, TextArea memoField, MultiSelectComboBox<Tag> tagsCombo) {
        Transaction.TransactionType type = Transaction.TransactionType.EXPENSE;
        if (tabs.getSelectedTab() == inc) type = Transaction.TransactionType.INCOME;
        else if (tabs.getSelectedTab() == transferTab) type = Transaction.TransactionType.TRANSFER;
        
        transaction.setType(type);
        transaction.setTransactionDate(datePicker.getValue().atStartOfDay());
        transaction.setAmount(amountField.getValue());
        
        if (type == Transaction.TransactionType.INCOME) {
            transaction.setToAccount(accountCombo.getValue());
            transaction.setFromAccount(null);
        } else if (type == Transaction.TransactionType.EXPENSE) {
            transaction.setFromAccount(accountCombo.getValue());
            transaction.setToAccount(null);
        } else {
            transaction.setFromAccount(accountCombo.getValue());
            transaction.setToAccount(toAccountCombo.getValue());
        }

        String payeeName = payeeCombo.getValue();
        if (payeeName != null && !payeeName.isEmpty()) {
            boolean exists = payeeService.getAllPayees().stream().anyMatch(p -> p.getName().equalsIgnoreCase(payeeName));
            if (!exists) {
                Payee newPayee = Payee.builder().name(payeeName).build();
                payeeService.savePayee(newPayee);
            }
        }

        transaction.setPaymentMethod(paymentCombo.getValue());
        transaction.setNumber(numberField.getValue());
        transaction.setPayee(payeeName);
        transaction.setCategory(categoryCombo.getValue());
        transaction.setAsset(assetCombo.getValue());
        transaction.setUnits(unitsField.getValue());
        transaction.setMemo(memoField.getValue());
        
        String tags = tagsCombo.getValue().stream().map(Tag::getName).collect(Collectors.joining(","));
        transaction.setTags(tags);

        if (transaction.getId() == null) {
            // Get all transactions for the same date to calculate proper sortOrder
            LocalDate txDate = transaction.getTransactionDate().toLocalDate();
            Account relevantAccount = null;
            if (type == Transaction.TransactionType.INCOME) {
                relevantAccount = transaction.getToAccount();
            } else {
                relevantAccount = transaction.getFromAccount();
            }

            int maxOrder = 0;
            if (relevantAccount != null) {
                maxOrder = transactionService.getTransactionsByAccount(relevantAccount).stream()
                        .filter(tr -> tr.getTransactionDate().toLocalDate().equals(txDate))
                        .mapToInt(Transaction::getSortOrder)
                        .max().orElse(0);
            }
            transaction.setSortOrder(maxOrder + 10); // Use steps of 10 for easier insertion later
        }

        transactionService.saveTransaction(transaction);
    }

    private DateTimeFormatter getDateTimeFormatter() {
        String pattern = currentUser.getLocale().equals("de-DE") ? "dd.MM.yyyy" : "MM/dd/yyyy";
        return DateTimeFormatter.ofPattern(pattern);
    }

    @Override
    public Locale getLocale() {
        return Locale.forLanguageTag(currentUser.getLocale());
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "";
        NumberFormat formatter = NumberFormat.getCurrencyInstance(getLocale());
        try {
            java.util.Currency currency = java.util.Currency.getInstance(currentUser.getDefaultCurrency());
            formatter.setCurrency(currency);
        } catch (Exception e) {}
        return formatter.format(amount);
    }
}
