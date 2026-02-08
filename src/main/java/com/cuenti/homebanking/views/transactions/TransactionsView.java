package com.cuenti.homebanking.views.transactions;

import com.cuenti.homebanking.data.*;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.services.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
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
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@PageTitle("Transactions")
@Route("transactions")
@Menu(order = 1, icon = LineAwesomeIconUrl.TH_LIST_SOLID)
@PermitAll
public class TransactionsView extends VerticalLayout {

    private final TransactionService transactionService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final PayeeService payeeService;
    private final UserService userService;
    private final User currentUser;

    private final Grid<Transaction> grid = new Grid<>(Transaction.class, false);
    private final TextField searchField = new TextField();
    private final ComboBox<Account> accountSelector = new ComboBox<>();
    private final DatePicker dateFrom = new DatePicker();
    private final DatePicker dateTo = new DatePicker();
    private final Tabs typeTabs = new Tabs();
    private Transaction.TransactionType selectedTypeFilter = null;

    private List<Transaction> allTransactions = new ArrayList<>();

    public TransactionsView(TransactionService transactionService, AccountService accountService,
                            CategoryService categoryService, PayeeService payeeService,
                            UserService userService, SecurityUtils securityUtils) {
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.payeeService = payeeService;
        this.userService = userService;

        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        this.currentUser = userService.findByUsername(username);

        setSpacing(true);
        setPadding(true);
        setMaxWidth("1200px");
        getStyle().set("margin", "0 auto");

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
        dateFrom.setPlaceholder("From");
        dateFrom.setClearButtonVisible(true);
        dateFrom.addValueChangeListener(e -> updateFilters());
        dateFrom.setWidth("150px");
        dateFrom.setValue(now.with(TemporalAdjusters.firstDayOfMonth()));

        dateTo.setPlaceholder("To");
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

        Button addButton = new Button(getTranslation("transactions.new"), VaadinIcon.PLUS.create());
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.addClickListener(e -> openTransactionDialog(new Transaction()));

        setupTabs();

        HorizontalLayout toolbar = new HorizontalLayout(accountSelector, dateFrom, dateTo, typeTabs, searchField, addButton);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.expand(typeTabs);

        setupGrid();

        add(title);

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
        Tab all = new Tab("All");
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

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        grid.addColumn(t -> t.getTransactionDate().format(dateFormatter))
                .setHeader(getTranslation("transactions.date"))
                .setSortable(true)
                .setComparator(Transaction::getTransactionDate)
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.addColumn(Transaction::getPayee)
                .setHeader(getTranslation("transactions.payee"))
                .setSortable(true)
                .setAutoWidth(true);

        grid.addColumn(t -> t.getCategory() != null ? t.getCategory().getFullName() : "")
                .setHeader(getTranslation("transactions.category"))
                .setSortable(true)
                .setAutoWidth(true);

        grid.addComponentColumn(t -> {
            boolean isExpense = t.getType() == Transaction.TransactionType.EXPENSE;
            if (isExpense) {
                Span s = new Span(formatCurrency(t.getAmount()));
                s.getStyle().set("color", "var(--lumo-error-text-color)");
                return s;
            }
            return new Span();
        }).setHeader("Expense")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setSortable(true)
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.addComponentColumn(t -> {
            boolean isIncome = t.getType() == Transaction.TransactionType.INCOME;
            if (isIncome) {
                Span s = new Span(formatCurrency(t.getAmount()));
                s.getStyle().set("color", "var(--lumo-success-text-color)");
                return s;
            }
            return new Span();
        }).setHeader("Income")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setSortable(true)
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.addColumn(t -> {
            if (t.getType() == Transaction.TransactionType.TRANSFER) {
                String from = t.getFromAccount() != null ? t.getFromAccount().getAccountName() : "?";
                String to = t.getToAccount() != null ? t.getToAccount().getAccountName() : "?";
                return from + " → " + to;
            }
            return t.getFromAccount() != null ? t.getFromAccount().getAccountName() :
                    (t.getToAccount() != null ? t.getToAccount().getAccountName() : "");
        }).setHeader("Account")
                .setSortable(true)
                .setAutoWidth(true);

        // Action buttons
        grid.addComponentColumn(t -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> openTransactionDialog(t));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e -> deleteTransaction(t));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);
    }

    private void deleteTransaction(Transaction transaction) {
        Dialog confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle("Delete Transaction");
        confirmDialog.add(new Span("Are you sure you want to delete this transaction?"));

        Button deleteBtn = new Button("Delete", e -> {
            transactionService.deleteTransaction(transaction);
            refreshGrid();
            confirmDialog.close();
            Notification.show("Transaction deleted").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        Button cancelBtn = new Button("Cancel", e -> confirmDialog.close());
        confirmDialog.getFooter().add(cancelBtn, deleteBtn);
        confirmDialog.open();
    }

    private void openTransactionDialog(Transaction transaction) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(transaction.getId() == null ?
                getTranslation("dialog.add_transaction") : getTranslation("dialog.edit_transaction"));
        dialog.setWidth("600px");

        // Transaction type tabs
        Tabs typeTabs = new Tabs();
        Tab expenseTab = new Tab(getTranslation("transaction.type.expense"));
        Tab incomeTab = new Tab(getTranslation("transaction.type.income"));
        Tab transferTab = new Tab(getTranslation("transaction.type.transfer"));
        typeTabs.add(expenseTab, incomeTab, transferTab);
        typeTabs.setWidthFull();

        if (transaction.getType() != null) {
            switch (transaction.getType()) {
                case INCOME -> typeTabs.setSelectedTab(incomeTab);
                case TRANSFER -> typeTabs.setSelectedTab(transferTab);
                default -> typeTabs.setSelectedTab(expenseTab);
            }
        }

        // Form fields
        DatePicker datePicker = new DatePicker(getTranslation("dialog.date"));
        datePicker.setValue(transaction.getTransactionDate() != null ?
                transaction.getTransactionDate().toLocalDate() : LocalDate.now());
        datePicker.setWidthFull();

        BigDecimalField amountField = new BigDecimalField(getTranslation("dialog.amount"));
        amountField.setValue(transaction.getAmount() != null ? transaction.getAmount() : BigDecimal.ZERO);
        amountField.setWidthFull();

        List<Account> userAccounts = accountService.getAccountsByUser(currentUser);

        ComboBox<Account> fromAccountCombo = new ComboBox<>(getTranslation("dialog.from"));
        fromAccountCombo.setItems(userAccounts);
        fromAccountCombo.setItemLabelGenerator(Account::getAccountName);
        fromAccountCombo.setValue(transaction.getFromAccount());
        fromAccountCombo.setWidthFull();

        ComboBox<Account> toAccountCombo = new ComboBox<>(getTranslation("dialog.to"));
        toAccountCombo.setItems(userAccounts);
        toAccountCombo.setItemLabelGenerator(Account::getAccountName);
        toAccountCombo.setValue(transaction.getToAccount());
        toAccountCombo.setWidthFull();

        ComboBox<String> payeeCombo = new ComboBox<>(getTranslation("transactions.payee"));
        List<String> existingPayees = payeeService.getAllPayees().stream()
                .map(Payee::getName).distinct().toList();
        payeeCombo.setItems(existingPayees);
        payeeCombo.setAllowCustomValue(true);
        payeeCombo.setValue(transaction.getPayee());
        payeeCombo.addCustomValueSetListener(e -> payeeCombo.setValue(e.getDetail()));
        payeeCombo.setWidthFull();

        ComboBox<Category> categoryCombo = new ComboBox<>(getTranslation("transactions.category"));
        categoryCombo.setItemLabelGenerator(Category::getFullName);
        categoryCombo.setValue(transaction.getCategory());
        categoryCombo.setWidthFull();

        TextArea memoField = new TextArea(getTranslation("dialog.memo"));
        memoField.setValue(transaction.getMemo() != null ? transaction.getMemo() : "");
        memoField.setWidthFull();

        // Update visibility and categories based on type tab
        Runnable updateVisibility = () -> {
            Tab selected = typeTabs.getSelectedTab();
            boolean isTransfer = selected == transferTab;
            boolean isExpense = selected == expenseTab;
            boolean isIncome = selected == incomeTab;

            toAccountCombo.setVisible(isTransfer);
            fromAccountCombo.setLabel(isTransfer ? getTranslation("dialog.from") : getTranslation("dialog.account"));
            payeeCombo.setVisible(!isTransfer);

            // Filter categories by type
            Category.CategoryType catType = isIncome ? Category.CategoryType.INCOME : Category.CategoryType.EXPENSE;
            List<Category> filteredCategories = categoryService.getAllCategories().stream()
                    .filter(c -> c.getType() == catType)
                    .toList();
            categoryCombo.setItems(filteredCategories);
        };

        typeTabs.addSelectedChangeListener(e -> updateVisibility.run());
        updateVisibility.run();

        // Form layout
        FormLayout formLayout = new FormLayout();
        formLayout.add(datePicker, amountField, fromAccountCombo, toAccountCombo, payeeCombo, categoryCombo, memoField);
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2)
        );
        formLayout.setColspan(memoField, 2);

        // Save button
        Button saveButton = new Button(getTranslation("dialog.save"), e -> {
            if (amountField.getValue() == null || amountField.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                Notification.show("Please enter a valid amount").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            Tab selected = typeTabs.getSelectedTab();
            Transaction.TransactionType type;
            if (selected == incomeTab) type = Transaction.TransactionType.INCOME;
            else if (selected == transferTab) type = Transaction.TransactionType.TRANSFER;
            else type = Transaction.TransactionType.EXPENSE;

            transaction.setType(type);
            transaction.setAmount(amountField.getValue());
            transaction.setTransactionDate(datePicker.getValue().atStartOfDay());
            transaction.setPayee(payeeCombo.getValue());
            transaction.setCategory(categoryCombo.getValue());
            transaction.setMemo(memoField.getValue());

            if (type == Transaction.TransactionType.EXPENSE) {
                transaction.setFromAccount(fromAccountCombo.getValue());
                transaction.setToAccount(null);
            } else if (type == Transaction.TransactionType.INCOME) {
                transaction.setFromAccount(null);
                transaction.setToAccount(fromAccountCombo.getValue());
            } else { // Transfer
                transaction.setFromAccount(fromAccountCombo.getValue());
                transaction.setToAccount(toAccountCombo.getValue());
            }

            transactionService.saveTransaction(transaction);
            refreshGrid();
            dialog.close();
            Notification.show(getTranslation("transactions.saved")).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());

        dialog.add(typeTabs, formLayout);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void refreshGrid() {
        allTransactions = transactionService.getTransactionsByUser(currentUser);
        grid.setItems(allTransactions);
        updateFilters();
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
        } catch (Exception e) {
            // Use default
        }
        return formatter.format(amount);
    }
}
