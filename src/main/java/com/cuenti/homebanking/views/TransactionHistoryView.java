package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.*;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.*;
import com.cuenti.homebanking.views.components.TagColorUtil;
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
import java.util.stream.Stream;
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
        setPadding(false);
        setSpacing(false);
        getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("padding", "var(--lumo-space-m)")
                .set("overflow", "hidden");
        
        setupUI();
        refreshGrid();
    }

    private void setupUI() {
        Span title = new Span(getTranslation("transactions.title"));
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-xxl)")
                .set("font-weight", "700")
                .set("color", "var(--lumo-header-text-color)");

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
        dateFrom.setLocale(getLocale());

        if (currentUser.getLocale().equals("de-DE")) {
            DatePicker.DatePickerI18n i18nFrom = new DatePicker.DatePickerI18n();
            i18nFrom.setDateFormat("dd.MM.yyyy");
            i18nFrom.setMonthNames(List.of("Januar", "Februar", "März", "April", "Mai", "Juni",
                                     "Juli", "August", "September", "Oktober", "November", "Dezember"));
            i18nFrom.setWeekdays(List.of("Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"));
            i18nFrom.setWeekdaysShort(List.of("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"));
            i18nFrom.setToday("Heute");
            i18nFrom.setCancel("Abbrechen");
            i18nFrom.setFirstDayOfWeek(1);
            dateFrom.setI18n(i18nFrom);
        }
        dateFrom.setValue(now.with(TemporalAdjusters.firstDayOfMonth()));

        dateTo.setPlaceholder(getTranslation("dialog.to"));
        dateTo.setClearButtonVisible(true);
        dateTo.addValueChangeListener(e -> updateFilters());
        dateTo.setWidth("150px");
        dateTo.setLocale(getLocale());

        if (currentUser.getLocale().equals("de-DE")) {
            DatePicker.DatePickerI18n i18nTo = new DatePicker.DatePickerI18n();
            i18nTo.setDateFormat("dd.MM.yyyy");
            i18nTo.setMonthNames(List.of("Januar", "Februar", "März", "April", "Mai", "Juni",
                                     "Juli", "August", "September", "Oktober", "November", "Dezember"));
            i18nTo.setWeekdays(List.of("Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"));
            i18nTo.setWeekdaysShort(List.of("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"));
            i18nTo.setToday("Heute");
            i18nTo.setCancel("Abbrechen");
            i18nTo.setFirstDayOfWeek(1);
            dateTo.setI18n(i18nTo);
        }
        dateTo.setValue(now.with(TemporalAdjusters.lastDayOfMonth()));

        searchField.setPlaceholder(getTranslation("transactions.search"));
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> updateFilters());
        searchField.setWidth("220px");

        Button addButton = new Button(getTranslation("transactions.new"), VaadinIcon.PLUS.create(), e -> openTransactionDialog(new Transaction()));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.getStyle().set("border-radius", "8px");

        setupTabs();

        // Filters row
        HorizontalLayout filtersRow = new HorizontalLayout(accountSelector, dateFrom, dateTo, searchField);
        filtersRow.setAlignItems(Alignment.BASELINE);
        filtersRow.setSpacing(false);
        filtersRow.getStyle().set("gap", "var(--lumo-space-s)").set("flex-wrap", "wrap");

        // Top toolbar: filters left, add button right
        HorizontalLayout toolbar = new HorizontalLayout(filtersRow, addButton);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        toolbar.getStyle()
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "12px");

        // Type tabs row — full width below toolbar
        typeTabs.setWidthFull();
        typeTabs.getStyle()
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)")
                .set("margin-bottom", "var(--lumo-space-xs)");

        setupGrid();

        add(title);

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
                .set("gap", "var(--lumo-space-s)");
        card.add(toolbar, typeTabs, grid);
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
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        grid.setSizeFull();

        // 1. Type + icon avatar
        grid.addComponentColumn(t -> {
            Div avatar = new Div();
            avatar.getStyle()
                    .set("width", "32px").set("height", "32px").set("border-radius", "50%")
                    .set("display", "flex").set("align-items", "center").set("justify-content", "center")
                    .set("flex-shrink", "0");
            String bg;
            if (t.getType() == Transaction.TransactionType.INCOME)        bg = "rgba(var(--lumo-success-color-50pct-rgb,0,168,80),0.15)";
            else if (t.getType() == Transaction.TransactionType.TRANSFER) bg = "rgba(var(--lumo-primary-color-50pct-rgb,26,119,242),0.15)";
            else                                                           bg = "rgba(var(--lumo-error-color-50pct-rgb,255,63,63),0.15)";
            avatar.getStyle().set("background", bg);
            Icon icon = getPaymentIcon(t);
            String iconColor;
            if (t.getType() == Transaction.TransactionType.INCOME)        iconColor = "var(--lumo-success-color)";
            else if (t.getType() == Transaction.TransactionType.TRANSFER) iconColor = "var(--lumo-primary-color)";
            else                                                           iconColor = "var(--lumo-error-color)";
            icon.getStyle().set("font-size", "14px").set("color", iconColor);
            avatar.add(icon);
            return avatar;
        }).setHeader("").setWidth("48px").setFlexGrow(0);

        // 2. Date
        grid.addComponentColumn(t -> {
            Span date = new Span(t.getTransactionDate().format(getDateTimeFormatter()));
            date.getStyle()
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("color", "var(--lumo-secondary-text-color)");
            return date;
        }).setHeader(getTranslation("transactions.date"))
                .setSortable(true).setComparator(Transaction::getTransactionDate)
                .setAutoWidth(true).setFlexGrow(0);

        // 3. Payee + account stacked
        grid.addComponentColumn(t -> {
            Span payee = new Span(t.getPayee() != null ? t.getPayee() : "—");
            payee.getStyle().set("font-weight", "600").set("font-size", "var(--lumo-font-size-s)");

            Account acc = t.getType() == Transaction.TransactionType.INCOME ? t.getToAccount() : t.getFromAccount();
            String accName = acc != null ? acc.getAccountName() : "";
            if (t.getType() == Transaction.TransactionType.TRANSFER && t.getFromAccount() != null && t.getToAccount() != null) {
                accName = t.getFromAccount().getAccountName() + " → " + t.getToAccount().getAccountName();
            }
            Span account = new Span(accName);
            account.getStyle()
                    .set("font-size", "var(--lumo-font-size-xs)")
                    .set("color", "var(--lumo-secondary-text-color)");

            Div stack = new Div(payee, account);
            stack.getStyle().set("display", "flex").set("flex-direction", "column")
                    .set("gap", "1px").set("padding", "var(--lumo-space-xs) 0");
            return stack;
        }).setHeader(getTranslation("transactions.payee"))
                .setSortable(true)
                .setComparator(Comparator.comparing(t -> t.getPayee() != null ? t.getPayee() : ""))
                .setAutoWidth(true);

        // 4. Category pill
        grid.addComponentColumn(t -> {
            String cat;
            if (t.getSplits() != null && !t.getSplits().isEmpty()) {
                String s = getTranslation("transactions.split");
                cat = s.startsWith("!") ? "Split" : s;
            } else {
                cat = t.getCategory() != null ? t.getCategory().getFullName() : "";
            }
            if (cat.isBlank()) return new Span();
            Span badge = new Span(cat);
            badge.getStyle()
                    .set("font-size", "var(--lumo-font-size-xs)").set("font-weight", "500")
                    .set("padding", "2px 8px").set("border-radius", "99px")
                    .set("background", "var(--lumo-contrast-10pct)")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("white-space", "nowrap").set("max-width", "160px")
                    .set("overflow", "hidden").set("text-overflow", "ellipsis").set("display", "block");
            return badge;
        }).setHeader(getTranslation("transactions.category"))
                .setSortable(true)
                .setComparator(Comparator.comparing(t -> t.getCategory() != null ? t.getCategory().getFullName() : ""))
                .setAutoWidth(true);

        // 5. Tags
        grid.addComponentColumn(t -> {
            HorizontalLayout hl = new HorizontalLayout();
            hl.setSpacing(false);
            hl.getStyle().set("gap", "4px").set("flex-wrap", "wrap");
            if (t.getTags() != null && !t.getTags().isBlank()) {
                for (String tagName : t.getTags().split(",")) {
                    hl.add(TagColorUtil.createTagBadge(tagName.trim()));
                }
            }
            return hl;
        }).setHeader(getTranslation("dialog.tags")).setAutoWidth(true).setFlexGrow(0);

        // 6. Amount – single column, coloured and signed
        grid.addComponentColumn(t -> {
            Account selected = accountSelector.getValue();
            boolean isCredit = (t.getType() == Transaction.TransactionType.INCOME)
                    || (t.getType() == Transaction.TransactionType.TRANSFER
                        && selected != null && t.getToAccount() != null
                        && t.getToAccount().getId().equals(selected.getId()));

            String sign  = isCredit ? "+" : "−";
            String color = isCredit ? "var(--lumo-success-color)" : "var(--lumo-error-color)";
            if (t.getType() == Transaction.TransactionType.TRANSFER && selected == null)
                color = "var(--lumo-primary-color)";

            Span s = new Span(sign + formatCurrency(t.getAmount()));
            s.getStyle().set("font-weight", "700").set("font-size", "var(--lumo-font-size-s)")
                    .set("color", color).set("white-space", "nowrap");
            return s;
        }).setHeader(getTranslation("dialog.amount"))
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setSortable(true).setComparator(Comparator.comparing(Transaction::getAmount))
                .setAutoWidth(true).setFlexGrow(0);

        // 7. Balance
        grid.addComponentColumn(t -> {
            BigDecimal bal = balanceCache.getOrDefault(t.getId(), BigDecimal.ZERO);
            Span s = new Span(formatCurrency(bal));
            s.getStyle()
                    .set("font-size", "var(--lumo-font-size-s)").set("font-weight", "500")
                    .set("color", bal.compareTo(BigDecimal.ZERO) >= 0
                            ? "var(--lumo-body-text-color)" : "var(--lumo-error-color)");
            return s;
        }).setHeader(getTranslation("accounts.balance"))
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setSortable(true).setAutoWidth(true).setFlexGrow(0);

        // 8. Memo — truncated
        grid.addComponentColumn(t -> {
            if (t.getMemo() == null || t.getMemo().isBlank()) return new Span();
            Span s = new Span(t.getMemo());
            s.getStyle()
                    .set("font-size", "var(--lumo-font-size-xs)")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("max-width", "180px").set("overflow", "hidden")
                    .set("text-overflow", "ellipsis").set("white-space", "nowrap")
                    .set("display", "block");
            s.getElement().setAttribute("title", t.getMemo());
            return s;
        }).setHeader(getTranslation("dialog.memo"))
                .setAutoWidth(true).setFlexGrow(0);

        // 9. Actions – reorder + edit + delete
        grid.addComponentColumn(t -> {
            HorizontalLayout hl = new HorizontalLayout();
            hl.setSpacing(false);
            hl.setAlignItems(Alignment.CENTER);
            hl.getStyle().set("gap", "var(--lumo-space-xs)");

            Account selected = accountSelector.getValue();
            if (selected != null) {
                LocalDate date = t.getTransactionDate().toLocalDate();
                List<Transaction> sameDay = allAccountTransactions.stream()
                        .filter(tr -> tr.getTransactionDate().toLocalDate().equals(date))
                        .sorted(Comparator.comparing(Transaction::getSortOrder).reversed()
                                .thenComparing(Transaction::getId))
                        .collect(Collectors.toList());

                if (sameDay.size() > 1) {
                    int index = -1;
                    for (int i = 0; i < sameDay.size(); i++) {
                        if (sameDay.get(i).getId().equals(t.getId())) { index = i; break; }
                    }
                    if (index >= 0) {
                        final int idx = index;
                        Button upBtn = new Button(VaadinIcon.ARROW_UP.create(), e -> moveTransaction(t, -1));
                        upBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                        upBtn.setEnabled(idx > 0);
                        upBtn.setTooltipText(getTranslation("transactions.move_up"));
                        Button downBtn = new Button(VaadinIcon.ARROW_DOWN.create(), e -> moveTransaction(t, 1));
                        downBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                        downBtn.setEnabled(idx < sameDay.size() - 1);
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
    }

    private void confirmDelete(Transaction t) {
        Dialog confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle(getTranslation("dialog.confirm_delete"));

        String message = t.getPayee() != null && !t.getPayee().isEmpty()
                ? t.getPayee() + "  —  " + formatCurrency(t.getAmount())
                : formatCurrency(t.getAmount());

        Div body = new Div();
        body.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "var(--lumo-space-s)");
        Span msg = new Span(getTranslation("dialog.confirm_delete_message") + "?");
        msg.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)");
        Span detail = new Span(message);
        detail.getStyle().set("font-weight", "700").set("font-size", "var(--lumo-font-size-m)");
        body.add(msg, detail);
        confirmDialog.add(body);

        Button cancelBtn = new Button(getTranslation("dialog.cancel"), e -> confirmDialog.close());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button deleteBtn = new Button(getTranslation("transactions.delete"), VaadinIcon.TRASH.create(), e -> {
            transactionService.deleteTransaction(t);
            confirmDialog.close();
            refreshGrid();
            Notification n = Notification.show(getTranslation("transactions.deleted"), 2000, Notification.Position.BOTTOM_END);
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);
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
        dialog.setWidth("min(700px, 96vw)");
        dialog.setResizable(false);
        dialog.getElement().getStyle()
                .set("--lumo-border-radius-l", "20px")
                .set("padding", "0")
                .set("overflow-x", "hidden");

        // ── Type selector: coloured pill buttons ─────────────────────
        Button expenseBtn  = new Button(getTranslation("transaction.type.expense"));
        Button incomeBtn   = new Button(getTranslation("transaction.type.income"));
        Button transferBtn = new Button(getTranslation("transaction.type.transfer"));

        // Keep a logical "selected tab" reference for compatibility with existing save logic
        Tabs hiddenTabs = new Tabs();
        Tab expenseTab  = new Tab(getTranslation("transaction.type.expense"));
        Tab incomeTab   = new Tab(getTranslation("transaction.type.income"));
        Tab transferTab = new Tab(getTranslation("transaction.type.transfer"));
        hiddenTabs.add(expenseTab, incomeTab, transferTab);
        hiddenTabs.setVisible(false);

        // Colour tokens per type
        String[] TYPE_COLORS = {
            "var(--lumo-error-color)",    // expense
            "var(--lumo-success-color)",  // income
            "var(--lumo-primary-color)"   // transfer
        };
        Button[] typeBtns = {expenseBtn, incomeBtn, transferBtn};
        Tab[]    typeTabs = {expenseTab, incomeTab, transferTab};

        // Accent div that changes color based on selected type
        Div accentBar = new Div();
        accentBar.setWidthFull();
        accentBar.setHeight("4px");
        accentBar.getStyle().set("border-radius", "20px 20px 0 0").set("transition", "background 0.2s");

        Runnable[] selectType = {null};
        selectType[0] = () -> {
            int sel = 0;
            for (int i = 0; i < typeTabs.length; i++) {
                if (hiddenTabs.getSelectedTab() == typeTabs[i]) { sel = i; break; }
            }
            final int finalSel = sel;
            for (int i = 0; i < typeBtns.length; i++) {
                Button b = typeBtns[i];
                boolean active = (i == finalSel);
                b.getElement().getStyle()
                        .set("background", active ? TYPE_COLORS[i] : "var(--lumo-contrast-5pct)")
                        .set("color", active ? "white" : "var(--lumo-secondary-text-color)")
                        .set("border", "none")
                        .set("border-radius", "99px")
                        .set("font-weight", active ? "700" : "500")
                        .set("font-size", "var(--lumo-font-size-s)")
                        .set("padding", "var(--lumo-space-xs) var(--lumo-space-m)")
                        .set("cursor", "pointer")
                        .set("transition", "all 0.15s");
            }
            accentBar.getStyle().set("background", TYPE_COLORS[finalSel]);
        };

        expenseBtn.addClickListener(e  -> { hiddenTabs.setSelectedTab(expenseTab);  selectType[0].run(); });
        incomeBtn.addClickListener(e   -> { hiddenTabs.setSelectedTab(incomeTab);   selectType[0].run(); });
        transferBtn.addClickListener(e -> { hiddenTabs.setSelectedTab(transferTab); selectType[0].run(); });

        // Initialise selected type
        if (currentFormTransaction[0].getType() == Transaction.TransactionType.INCOME)
            hiddenTabs.setSelectedTab(incomeTab);
        else if (currentFormTransaction[0].getType() == Transaction.TransactionType.TRANSFER)
            hiddenTabs.setSelectedTab(transferTab);
        else
            hiddenTabs.setSelectedTab(expenseTab);

        HorizontalLayout typeRow = new HorizontalLayout(expenseBtn, incomeBtn, transferBtn);
        typeRow.setSpacing(false);
        typeRow.getStyle().set("gap", "var(--lumo-space-xs)").set("padding", "var(--lumo-space-m) var(--lumo-space-l)").set("flex-wrap", "wrap");

        // ── Hero: Amount field ────────────────────────────────────────
        BigDecimalField amountField = new BigDecimalField();
        amountField.setWidthFull();
        amountField.setRequiredIndicatorVisible(true);
        amountField.setValue(currentFormTransaction[0].getAmount() != null ? currentFormTransaction[0].getAmount() : BigDecimal.ZERO);
        amountField.getStyle()
                .set("font-size", "var(--lumo-font-size-xxl)")
                .set("font-weight", "800")
                .set("--vaadin-text-field-default-width", "100%");
        amountField.getElement().getStyle().set("font-size", "var(--lumo-font-size-xxl)").set("font-weight", "800");

        Span amountLabel = new Span(getTranslation("dialog.amount").toUpperCase());
        amountLabel.getStyle()
                .set("font-size", "10px").set("font-weight", "700").set("letter-spacing", "0.08em")
                .set("color", "var(--lumo-secondary-text-color)");

        // Split toggle button — next to amount
        Button splitToggleBtn = new Button(VaadinIcon.PIE_CHART.create());
        splitToggleBtn.setTooltipText(getTranslation("transactions.split_transaction"));
        splitToggleBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        splitToggleBtn.getStyle().set("flex-shrink", "0");

        HorizontalLayout amountRow = new HorizontalLayout(amountField, splitToggleBtn);
        amountRow.setWidthFull();
        amountRow.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
        amountRow.setSpacing(false);
        amountRow.getStyle().set("gap", "var(--lumo-space-xs)");
        amountRow.expand(amountField);

        Div heroSection = new Div(amountLabel, amountRow);
        heroSection.setWidthFull();
        heroSection.getStyle()
                .set("padding", "var(--lumo-space-m) var(--lumo-space-l) var(--lumo-space-l)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)")
                .set("box-sizing", "border-box");

        // ── Date picker ───────────────────────────────────────────────
        DatePicker datePicker = new DatePicker(getTranslation("dialog.date"));
        datePicker.setLocale(getLocale());
        if (currentUser.getLocale().equals("de-DE")) {
            DatePicker.DatePickerI18n i18n = new DatePicker.DatePickerI18n();
            i18n.setDateFormat("dd.MM.yyyy");
            i18n.setMonthNames(List.of("Januar", "Februar", "März", "April", "Mai", "Juni",
                                     "Juli", "August", "September", "Oktober", "November", "Dezember"));
            i18n.setWeekdays(List.of("Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"));
            i18n.setWeekdaysShort(List.of("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"));
            i18n.setToday("Heute"); i18n.setCancel("Abbrechen"); i18n.setFirstDayOfWeek(1);
            datePicker.setI18n(i18n);
        }
        datePicker.setValue(currentFormTransaction[0].getTransactionDate() != null
                ? currentFormTransaction[0].getTransactionDate().toLocalDate() : LocalDateTime.now().toLocalDate());
        datePicker.setWidthFull();

        // ── Account fields ────────────────────────────────────────────
        List<Account> userAccounts = accountService.getAccountsByUser(currentUser);
        ComboBox<Account> accountCombo = new ComboBox<>(getTranslation("dialog.account"));
        accountCombo.setItems(userAccounts);
        accountCombo.setItemLabelGenerator(Account::getAccountName);
        accountCombo.setRequired(true);
        accountCombo.setWidthFull();
        if (currentFormTransaction[0].getId() == null && accountSelector.getValue() != null) {
            accountCombo.setValue(accountSelector.getValue());
        }

        ComboBox<Account> toAccountCombo = new ComboBox<>(getTranslation("dialog.to"));
        toAccountCombo.setItems(userAccounts);
        toAccountCombo.setItemLabelGenerator(Account::getAccountName);
        toAccountCombo.setWidthFull();

        // ── Payee + Category ──────────────────────────────────────────
        ComboBox<String> payeeCombo = new ComboBox<>(getTranslation("transactions.payee"));
        List<String> existingPayees = payeeService.getAllPayees().stream().map(Payee::getName).distinct().toList();
        payeeCombo.setItems(existingPayees);
        payeeCombo.setAllowCustomValue(true);
        payeeCombo.setValue(currentFormTransaction[0].getPayee());
        payeeCombo.addCustomValueSetListener(e -> payeeCombo.setValue(e.getDetail()));
        payeeCombo.setWidthFull();
        payeeCombo.setPrefixComponent(VaadinIcon.USER.create());

        ComboBox<Category> categoryCombo = new ComboBox<>(getTranslation("transactions.category"));
        categoryCombo.setItemLabelGenerator(Category::getFullName);
        categoryCombo.setAllowCustomValue(true);
        categoryCombo.setWidthFull();
        categoryCombo.addCustomValueSetListener(e -> {
            String newCatName = e.getDetail();
            Category.CategoryType categoryType = hiddenTabs.getSelectedTab() == incomeTab
                    ? Category.CategoryType.INCOME : Category.CategoryType.EXPENSE;
            Category saved;
            if (newCatName != null && newCatName.contains(":")) {
                String[] parts = newCatName.split(":", 2);
                String parentName = parts[0].trim();
                String childName  = parts[1].trim();
                Category parentCategory = categoryService.getAllCategories().stream()
                        .filter(c -> c.getName().equals(parentName) && c.getParent() == null && c.getType() == categoryType)
                        .findFirst().orElse(null);
                if (parentCategory == null) {
                    parentCategory = categoryService.saveCategory(Category.builder()
                            .name(parentName).type(categoryType).user(currentUser).parent(null).build());
                    Notification.show(getTranslation("categories.parent_created") + ": " + parentName, 3000, Notification.Position.MIDDLE);
                }
                saved = categoryService.saveCategory(Category.builder()
                        .name(childName).type(categoryType).parent(parentCategory).user(currentUser).build());
            } else {
                saved = categoryService.saveCategory(Category.builder()
                        .name(newCatName).type(categoryType).user(currentUser).build());
            }
            categoryCombo.setItems(getFilteredCategoriesFromTabs(hiddenTabs, expenseTab, incomeTab));
            categoryCombo.setValue(saved);
        });

        // ── Payment + Number (secondary) ─────────────────────────────
        ComboBox<Transaction.PaymentMethod> paymentCombo = new ComboBox<>(getTranslation("dialog.payment_method"));
        paymentCombo.setItems(Transaction.PaymentMethod.values());
        paymentCombo.setItemLabelGenerator(pm -> pm == Transaction.PaymentMethod.NONE ? getTranslation("dialog.none") : pm.getLabel());
        paymentCombo.setValue(currentFormTransaction[0].getPaymentMethod() != null
                ? currentFormTransaction[0].getPaymentMethod() : Transaction.PaymentMethod.NONE);
        paymentCombo.setWidthFull();

        TextField numberField = new TextField(getTranslation("dialog.number"));
        numberField.setValue(currentFormTransaction[0].getNumber() != null ? currentFormTransaction[0].getNumber() : "");
        numberField.setWidthFull();

        // ── Tags + Memo ───────────────────────────────────────────────
        MultiSelectComboBox<Tag> tagsCombo = new MultiSelectComboBox<>(getTranslation("dialog.tags"));
        tagsCombo.setItems(tagService.getAllTags());
        tagsCombo.setItemLabelGenerator(Tag::getName);
        tagsCombo.setAllowCustomValue(true);
        tagsCombo.setWidthFull();
        if (currentFormTransaction[0].getTags() != null && !currentFormTransaction[0].getTags().isEmpty()) {
            Set<String> tagNames = new HashSet<>(Arrays.asList(currentFormTransaction[0].getTags().split(",")));
            tagsCombo.setValue(tagService.getAllTags().stream()
                    .filter(t -> tagNames.contains(t.getName())).collect(Collectors.toSet()));
        }
        tagsCombo.addCustomValueSetListener(e -> {
            Tag newTag = Tag.builder().name(e.getDetail()).build();
            tagService.saveTag(newTag);
            tagsCombo.setItems(tagService.getAllTags());
            Set<Tag> sel = new HashSet<>(tagsCombo.getValue()); sel.add(newTag);
            tagsCombo.setValue(sel);
        });

        TextArea memoField = new TextArea(getTranslation("dialog.memo"));
        memoField.setValue(currentFormTransaction[0].getMemo() != null ? currentFormTransaction[0].getMemo() : "");
        memoField.setWidthFull();
        memoField.setMinHeight("60px");
        memoField.setMaxHeight("100px");

        // ── Asset section (conditional) ───────────────────────────────
        ComboBox<Asset> assetCombo = new ComboBox<>(getTranslation("dialog.asset"));
        assetCombo.setItems(assetService.getAllAssets());
        assetCombo.setItemLabelGenerator(Asset::getSymbol);
        assetCombo.setValue(currentFormTransaction[0].getAsset());
        assetCombo.setWidthFull();

        BigDecimalField unitsField = new BigDecimalField(getTranslation("dialog.units"));
        unitsField.setValue(currentFormTransaction[0].getUnits() != null ? currentFormTransaction[0].getUnits() : BigDecimal.ZERO);
        unitsField.setWidthFull();

        BigDecimalField unitPriceField = new BigDecimalField(getTranslation("dialog.unit_price"));
        unitPriceField.setWidthFull();

        Div assetSection = createFormSection(getTranslation("dialog.asset_details"));
        HorizontalLayout assetRow = new HorizontalLayout(assetCombo, unitsField, unitPriceField);
        assetRow.setWidthFull(); assetRow.setSpacing(false);
        assetRow.getStyle().set("gap", "var(--lumo-space-s)").set("flex-wrap", "wrap");
        assetCombo.getStyle().set("flex", "1 1 160px"); unitsField.getStyle().set("flex", "1 1 100px"); unitPriceField.getStyle().set("flex", "1 1 100px");
        assetSection.add(assetRow);

        // ── Splits section ────────────────────────────────────────────
        List<TransactionSplit> currentSplits = new ArrayList<>(
                currentFormTransaction[0].getSplits() != null ? currentFormTransaction[0].getSplits() : new ArrayList<>());

        com.vaadin.flow.component.grid.Grid<TransactionSplit> splitGrid =
                new com.vaadin.flow.component.grid.Grid<>(TransactionSplit.class, false);
        splitGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COMPACT);
        splitGrid.addColumn(TransactionSplit::getAmount)
                .setHeader(getTranslation("dialog.amount")).setAutoWidth(true);
        splitGrid.addColumn(s -> s.getCategory() != null ? s.getCategory().getFullName() : "")
                .setHeader(getTranslation("transactions.category")).setAutoWidth(true);
        splitGrid.addColumn(TransactionSplit::getMemo)
                .setHeader(getTranslation("dialog.memo")).setAutoWidth(true);

        BigDecimalField splitAmountField    = new BigDecimalField(getTranslation("dialog.amount"));
        ComboBox<Category> splitCategoryCombo = new ComboBox<>(getTranslation("transactions.category"));
        splitCategoryCombo.setItemLabelGenerator(Category::getFullName);
        splitCategoryCombo.setItems(getFilteredCategoriesFromTabs(hiddenTabs, expenseTab, incomeTab));
        TextField splitMemoField = new TextField(getTranslation("dialog.memo"));

        splitGrid.addComponentColumn(s -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create(), ev -> {
                currentSplits.remove(s); splitGrid.setItems(currentSplits);
                splitAmountField.setValue(s.getAmount());
                splitCategoryCombo.setValue(s.getCategory());
                splitMemoField.setValue(s.getMemo() != null ? s.getMemo() : "");
                updateTotalAmount(currentSplits, amountField, categoryCombo);
                if (currentSplits.isEmpty()) splitGrid.setVisible(false);
            });
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), ev -> {
                currentSplits.remove(s); splitGrid.setItems(currentSplits);
                updateTotalAmount(currentSplits, amountField, categoryCombo);
                if (currentSplits.isEmpty()) splitGrid.setVisible(false);
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            HorizontalLayout hl = new HorizontalLayout(editBtn, deleteBtn);
            hl.setSpacing(false); return hl;
        }).setAutoWidth(true);
        splitGrid.setItems(currentSplits);
        splitGrid.setAllRowsVisible(true);
        splitGrid.setVisible(!currentSplits.isEmpty());

        splitAmountField.getStyle().set("flex", "1 1 90px").set("min-width", "0");
        splitCategoryCombo.getStyle().set("flex", "2 1 130px").set("min-width", "0");
        splitMemoField.getStyle().set("flex", "1 1 90px").set("min-width", "0");
        HorizontalLayout splitInputRow = new HorizontalLayout(splitAmountField, splitCategoryCombo, splitMemoField);
        splitInputRow.setWidthFull(); splitInputRow.setSpacing(false);
        splitInputRow.getStyle().set("gap", "var(--lumo-space-s)").set("flex-wrap", "wrap");
        splitInputRow.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.BASELINE);

        Button addSplitBtn = new Button(getTranslation("dialog.add"), VaadinIcon.PLUS.create(), ev -> {
            if (splitAmountField.getValue() != null && splitCategoryCombo.getValue() != null) {
                currentSplits.add(TransactionSplit.builder()
                        .amount(splitAmountField.getValue())
                        .category(splitCategoryCombo.getValue())
                        .memo(splitMemoField.getValue()).build());
                splitGrid.setItems(currentSplits); splitGrid.setVisible(true);
                updateTotalAmount(currentSplits, amountField, categoryCombo);
                splitAmountField.clear(); splitCategoryCombo.clear(); splitMemoField.clear();
            }
        });
        addSplitBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        HorizontalLayout splitAddRow = new HorizontalLayout(splitInputRow, addSplitBtn);
        splitAddRow.setWidthFull(); splitAddRow.setSpacing(false);
        splitAddRow.getStyle().set("gap", "var(--lumo-space-s)");
        splitAddRow.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.BASELINE);
        splitAddRow.expand(splitInputRow);

        Div splitSection = createFormSection(getTranslation("transactions.split_transaction"));
        splitSection.add(splitGrid, splitAddRow);
        splitSection.setVisible(!currentSplits.isEmpty());

        splitToggleBtn.addClickListener(e -> splitSection.setVisible(!splitSection.isVisible()));

        // ── Visibility logic ──────────────────────────────────────────
        Runnable updateVisibility = () -> {
            Tab selected = hiddenTabs.getSelectedTab();
            boolean isTransfer = selected == transferTab;
            toAccountCombo.setVisible(isTransfer);
            paymentCombo.setVisible(!isTransfer);
            accountCombo.setLabel(isTransfer ? getTranslation("dialog.from") : getTranslation("dialog.account"));
            Category currentCat = categoryCombo.getValue();
            categoryCombo.setItems(getFilteredCategoriesFromTabs(hiddenTabs, expenseTab, incomeTab));
            splitCategoryCombo.setItems(getFilteredCategoriesFromTabs(hiddenTabs, expenseTab, incomeTab));
            if (currentCat != null) categoryCombo.setValue(currentCat);
            Account acc   = accountCombo.getValue();
            Account toAcc = toAccountCombo.getValue();
            boolean assetVisible = (acc != null && acc.getAccountType() == Account.AccountType.ASSET)
                    || (isTransfer && toAcc != null && toAcc.getAccountType() == Account.AccountType.ASSET);
            assetSection.setVisible(assetVisible);
            selectType[0].run();
        };

        hiddenTabs.addSelectedChangeListener(e -> updateVisibility.run());
        accountCombo.addValueChangeListener(e -> updateVisibility.run());
        toAccountCombo.addValueChangeListener(e -> updateVisibility.run());

        // Pre-fill edit mode
        if (currentFormTransaction[0].getId() != null) {
            categoryCombo.setItems(getFilteredCategoriesFromTabs(hiddenTabs, expenseTab, incomeTab));
            if (currentFormTransaction[0].getType() == Transaction.TransactionType.INCOME) {
                accountCombo.setValue(currentFormTransaction[0].getToAccount());
            } else {
                accountCombo.setValue(currentFormTransaction[0].getFromAccount());
                if (currentFormTransaction[0].getType() == Transaction.TransactionType.TRANSFER)
                    toAccountCombo.setValue(currentFormTransaction[0].getToAccount());
            }
            categoryCombo.setValue(currentFormTransaction[0].getCategory());
            if (currentFormTransaction[0].getUnits() != null
                    && currentFormTransaction[0].getUnits().compareTo(BigDecimal.ZERO) != 0) {
                unitPriceField.setValue(currentFormTransaction[0].getAmount()
                        .divide(currentFormTransaction[0].getUnits(), 4, RoundingMode.HALF_UP));
            }
        }
        updateVisibility.run();
        updateTotalAmount(currentSplits, amountField, categoryCombo);

        // ── Assemble sections ─────────────────────────────────────────
        // Section 1: Core — date, account, payee, category
        Div coreSection = createFormSection(null);
        // wraps to single column on mobile
        HorizontalLayout row1 = new HorizontalLayout(datePicker, accountCombo);
        row1.setWidthFull(); row1.setSpacing(false);
        row1.getStyle().set("gap", "var(--lumo-space-m)").set("flex-wrap", "wrap");
        row1.getChildren().forEach(c -> c.getElement().getStyle().set("flex", "1 1 200px").set("min-width", "0"));
        // wraps to single column on mobile
        HorizontalLayout row2 = new HorizontalLayout(payeeCombo, categoryCombo);
        row2.setWidthFull(); row2.setSpacing(false);
        row2.getStyle().set("gap", "var(--lumo-space-m)").set("flex-wrap", "wrap");
        row2.getChildren().forEach(c -> c.getElement().getStyle().set("flex", "1 1 200px").set("min-width", "0"));
        // wraps to single column on mobile
        HorizontalLayout row3 = new HorizontalLayout(toAccountCombo, paymentCombo);
        row3.setWidthFull(); row3.setSpacing(false);
        row3.getStyle().set("gap", "var(--lumo-space-m)").set("flex-wrap", "wrap");
        row3.getChildren().forEach(c -> c.getElement().getStyle().set("flex", "1 1 200px").set("min-width", "0"));
        coreSection.add(row1, row2, row3, tagsCombo, memoField, splitSection, assetSection, hiddenTabs);

        // Secondary: payment details (number)
        Div extraSection = createFormSection(null);
        extraSection.add(numberField);
        extraSection.setVisible(false);

        Button moreBtn = new Button(getTranslation("dialog.more_details"), VaadinIcon.ANGLE_DOWN.create());
        moreBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        moreBtn.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("color", "var(--lumo-secondary-text-color)");
        moreBtn.addClickListener(e -> {
            boolean v = !extraSection.isVisible();
            extraSection.setVisible(v);
            moreBtn.setIcon(v ? VaadinIcon.ANGLE_UP.create() : VaadinIcon.ANGLE_DOWN.create());
        });

        // ── Full body ─────────────────────────────────────────────────
        Div body = new Div(accentBar, typeRow, heroSection, coreSection, moreBtn, extraSection);
        body.setWidthFull();
        body.getStyle()
                .set("display", "flex").set("flex-direction", "column")
                .set("overflow-x", "hidden").set("box-sizing", "border-box");

        // ── Footer buttons ────────────────────────────────────────────
        Button saveButton = new Button(
                transaction.getId() == null ? getTranslation("dialog.add") : getTranslation("dialog.save"),
                transaction.getId() == null ? VaadinIcon.CHECK.create() : VaadinIcon.CHECK.create(),
                e -> {
                    if (accountCombo.isEmpty()) {
                        Notification.show(getTranslation("accounts.name_required"), 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
                        return;
                    }
                    if (amountField.getValue() == null || amountField.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                        Notification.show(getTranslation("dialog.amount_positive"), 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
                        return;
                    }
                    Transaction saveTx = currentFormTransaction[0];
                    saveTx.getSplits().clear();
                    for (TransactionSplit s : currentSplits) saveTx.addSplit(s);
                    saveFromTabs(saveTx, hiddenTabs, expenseTab, incomeTab, transferTab, datePicker, amountField, accountCombo, toAccountCombo, paymentCombo, numberField, payeeCombo, categoryCombo, assetCombo, unitsField, memoField, tagsCombo);
                    refreshGrid(); dialog.close();
                    Notification n = Notification.show(getTranslation("transactions.saved"), 2000, Notification.Position.BOTTOM_END);
                    n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
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
            Transaction keepTx = currentFormTransaction[0];
            keepTx.getSplits().clear();
            for (TransactionSplit s : currentSplits) keepTx.addSplit(s);
            saveFromTabs(keepTx, hiddenTabs, expenseTab, incomeTab, transferTab, datePicker, amountField, accountCombo, toAccountCombo, paymentCombo, numberField, payeeCombo, categoryCombo, assetCombo, unitsField, memoField, tagsCombo);
            refreshGrid();
            Notification n = Notification.show(getTranslation("transactions.added"), 2000, Notification.Position.BOTTOM_END);
            n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            currentFormTransaction[0] = new Transaction();
            currentSplits.clear();
            updateTotalAmount(currentSplits, amountField, categoryCombo);
        });
        addKeepButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        if (transaction.getId() != null) addKeepButton.setVisible(false);

        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        dialog.add(body);
        dialog.getFooter().add(cancelButton, addKeepButton, saveButton);
        dialog.open();
    }

    /** Creates a padded section container with an optional all-caps label. */
    private Div createFormSection(String labelKey) {
        Div section = new Div();
        section.setWidthFull();
        section.getStyle()
                .set("display", "flex").set("flex-direction", "column")
                .set("gap", "var(--lumo-space-s)")
                .set("padding", "var(--lumo-space-m) var(--lumo-space-l)")
                .set("box-sizing", "border-box");
        if (labelKey != null && !labelKey.isBlank()) {
            Span label = new Span(labelKey.toUpperCase());
            label.getStyle()
                    .set("font-size", "10px").set("font-weight", "700").set("letter-spacing", "0.08em")
                    .set("color", "var(--lumo-secondary-text-color)");
            section.add(label);
        }
        return section;
    }

    private List<Category> getFilteredCategoriesFromTabs(Tabs tabs, Tab exp, Tab inc) {
        if (tabs.getSelectedTab() == exp) return categoryService.getCategoriesByType(Category.CategoryType.EXPENSE);
        else if (tabs.getSelectedTab() == inc) return categoryService.getCategoriesByType(Category.CategoryType.INCOME);
        return Stream.concat(
                        categoryService.getCategoriesByType(Category.CategoryType.EXPENSE).stream(),
                        categoryService.getCategoriesByType(Category.CategoryType.INCOME).stream())
                .distinct()
                .sorted(Comparator.comparing(Category::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void updateTotalAmount(List<TransactionSplit> splits, BigDecimalField amountField, ComboBox<Category> categoryCombo) {
        if (!splits.isEmpty()) {
            BigDecimal total = splits.stream().map(TransactionSplit::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            amountField.setValue(total);
            amountField.setReadOnly(true);
            categoryCombo.setReadOnly(true);
            // Hide the component if splits exist, but keep value for fallback if removed. Wait, it's just locked.
        } else {
            amountField.setReadOnly(false);
            categoryCombo.setReadOnly(false);
        }
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
