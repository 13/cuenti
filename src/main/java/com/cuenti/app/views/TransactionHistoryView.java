package com.cuenti.app.views;

import com.cuenti.app.model.*;
import com.cuenti.app.security.SecurityUtils;
import com.cuenti.app.service.*;
import com.cuenti.app.views.components.TagColorUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
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
import com.vaadin.flow.router.HasDynamicTitle;
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
@PermitAll
public class TransactionHistoryView extends VerticalLayout
        implements HasDynamicTitle, com.vaadin.flow.router.AfterNavigationObserver {

    @Override
    public void afterNavigation(com.vaadin.flow.router.AfterNavigationEvent event) {
        event.getLocation().getQueryParameters().getSingleParameter("q")
                .ifPresent(searchField::setValue);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("transactions.title") + " | " + getTranslation("app.name");
    }


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
    final com.cuenti.app.views.components.DetailPanel detailPanel =
            new com.cuenti.app.views.components.DetailPanel(); // package-visible for tests
    private final ComboBox<Account> accountSelector = new ComboBox<>();
    private final DatePicker dateFrom = new DatePicker();
    private final DatePicker dateTo = new DatePicker();
    private final Tabs typeTabs = new Tabs();
    private Transaction.TransactionType selectedTypeFilter = null;

    private List<Transaction> allAccountTransactions = new ArrayList<>();
    private Map<Long, BigDecimal> balanceCache = new HashMap<>();

    private com.vaadin.flow.component.grid.Grid.Column<Transaction> dateCol;
    private com.vaadin.flow.component.grid.Grid.Column<Transaction> payeeCol;
    private com.vaadin.flow.component.grid.Grid.Column<Transaction> categoryCol;
    private com.vaadin.flow.component.grid.Grid.Column<Transaction> amountCol;
    private com.vaadin.flow.component.grid.Grid.Column<Transaction> tagsCol;
    private com.vaadin.flow.component.grid.Grid.Column<Transaction> balanceCol;
    private com.vaadin.flow.component.grid.Grid.Column<Transaction> memoCol;
    private final Map<String, Boolean> colPrefs = new HashMap<>(Map.of(
            "category", true, "tags", true, "balance", true, "memo", true));
    final TextField headerPayeeFilter = new TextField(); // package-visible for tests
    private final ComboBox<String> headerCategoryFilter = new ComboBox<>();
    private final java.util.Set<Long> firstOfDayIds = new java.util.HashSet<>();
    private boolean dayGroupingActive = true;
    private boolean mixedCurrencies;
    private com.vaadin.flow.component.grid.FooterRow footerRow;
    private Runnable reapplyColumns = () -> {};
    private final Map<String, com.vaadin.flow.component.contextmenu.MenuItem> columnMenuItems = new HashMap<>();

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

        addClassNames("page-scroll", "page-shell");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle()
                .set("padding", "var(--vaadin-gap-m)")
                .set("gap", "var(--vaadin-gap-s)")
                .set("overflow", "hidden");
        
        setupUI();
        refreshGrid();
    }

    private void setupUI() {
        Span title = new Span(getTranslation("transactions.title"));
        title.addComponentAsFirst(VaadinIcon.LIST.create());
        title.addClassName("page-title");

        accountSelector.setPlaceholder(getTranslation("dialog.account"));
        // Build items including a pseudo "All Accounts" entry which shows transactions from all accounts
        List<Account> accounts = new ArrayList<>(accountService.getAccountsByUser(currentUser));
        Account allAccounts = Account.builder()
                .id(-1L)
                .accountName(getTranslation("accounts.all"))
                .build();
        accounts.add(0, allAccounts);
        accountSelector.setItems(accounts);
        accountSelector.setItemLabelGenerator(Account::getAccountName);
        // Select "All Accounts" by default so the view initially shows all transactions
        accountSelector.setValue(allAccounts);
        accountSelector.setClearButtonVisible(true);
        accountSelector.addValueChangeListener(e -> refreshGrid());
        accountSelector.setWidth("200px");

        LocalDate now = LocalDate.now();
        dateFrom.setPlaceholder(getTranslation("dialog.from"));
        dateFrom.setClearButtonVisible(true);
        dateFrom.addValueChangeListener(e -> refreshGrid());
        dateFrom.setWidth("150px");
        dateFrom.setLocale(getLocale());

        if (currentUser.getLocale() != null && currentUser.getLocale().startsWith("de")) {
            dateFrom.setI18n(com.cuenti.app.views.components.LocalizedDatePicker.germanI18n());
        }
        dateFrom.setValue(now.with(TemporalAdjusters.firstDayOfMonth()));

        dateTo.setPlaceholder(getTranslation("dialog.to"));
        dateTo.setClearButtonVisible(true);
        dateTo.addValueChangeListener(e -> refreshGrid());
        dateTo.setWidth("150px");
        dateTo.setLocale(getLocale());

        if (currentUser.getLocale() != null && currentUser.getLocale().startsWith("de")) {
            dateTo.setI18n(com.cuenti.app.views.components.LocalizedDatePicker.germanI18n());
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
        addButton.setTooltipText("Alt+N");
        // Alt+N opens the new-transaction dialog (Alt modifier so plain typing in filters is unaffected)
        com.vaadin.flow.component.Shortcuts.addShortcutListener(this,
                () -> openTransactionDialog(new Transaction()),
                com.vaadin.flow.component.Key.KEY_N, com.vaadin.flow.component.KeyModifier.ALT);

        setupTabs();

        // Filters row
        HorizontalLayout filtersRow = new HorizontalLayout(accountSelector, dateFrom, dateTo, searchField);
        filtersRow.setAlignItems(Alignment.BASELINE);
        filtersRow.setSpacing(false);
        filtersRow.getStyle().set("gap", "var(--vaadin-gap-s)").set("flex-wrap", "wrap");

        // CSV export of the currently filtered rows
        Anchor exportAnchor = new Anchor(
                com.vaadin.flow.server.streams.DownloadHandler.fromInputStream(event -> {
                    byte[] bytes = buildCsv().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    return new com.vaadin.flow.server.streams.DownloadResponse(
                            new java.io.ByteArrayInputStream(bytes), "transactions.csv",
                            "text/csv;charset=utf-8", bytes.length);
                }), "");
        Button exportButton = new Button(getTranslation("transactions.export"), VaadinIcon.DOWNLOAD.create());
        exportButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        exportAnchor.add(exportButton);
        exportAnchor.getElement().setAttribute("download", true);

        com.vaadin.flow.component.menubar.MenuBar columnsMenu = new com.vaadin.flow.component.menubar.MenuBar();
        columnsMenu.addThemeVariants(com.vaadin.flow.component.menubar.MenuBarVariant.LUMO_TERTIARY);
        com.vaadin.flow.component.contextmenu.MenuItem columnsRoot =
                columnsMenu.addItem(VaadinIcon.GRID_SMALL.create());
        columnsRoot.add(new Span(getTranslation("table.columns")));
        columnsRoot.getElement().setAttribute("aria-label", getTranslation("table.columns"));
        com.vaadin.flow.component.contextmenu.SubMenu columnsSub = columnsRoot.getSubMenu();
        java.util.LinkedHashMap<String, String> toggleable = new java.util.LinkedHashMap<>();
        toggleable.put("category", getTranslation("transactions.category"));
        toggleable.put("tags", getTranslation("dialog.tags"));
        toggleable.put("balance", getTranslation("accounts.balance"));
        toggleable.put("memo", getTranslation("dialog.memo"));
        toggleable.forEach((key, label) -> {
            com.vaadin.flow.component.contextmenu.MenuItem item = columnsSub.addItem(label);
            item.setCheckable(true);
            item.setChecked(colPrefs.getOrDefault(key, true));
            item.setKeepOpen(true);
            item.addClickListener(e -> {
                colPrefs.put(key, item.isChecked());
                reapplyColumns.run();
                String serialized = colPrefs.entrySet().stream()
                        .map(en -> en.getKey() + ":" + (en.getValue() ? "1" : "0"))
                        .collect(Collectors.joining(","));
                getUI().ifPresent(ui -> ui.getPage()
                        .executeJs("localStorage.setItem('cuenti.tx.cols', $0)", serialized));
            });
            columnMenuItems.put(key, item);
        });

        HorizontalLayout actionsRow = new HorizontalLayout(columnsMenu, exportAnchor, addButton);
        actionsRow.setAlignItems(Alignment.CENTER);
        actionsRow.setSpacing(false);
        actionsRow.getStyle().set("gap", "var(--vaadin-gap-s)");

        // Top toolbar: filters left, actions right
        HorizontalLayout toolbar = new HorizontalLayout(filtersRow, actionsRow);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        toolbar.addClassName("card-toolbar");
        toolbar.getStyle().set("flex-wrap", "wrap");

        // Type tabs row — full width below toolbar
        typeTabs.setWidthFull();
        typeTabs.getStyle()
                .set("border-bottom", "1px solid var(--vaadin-border-color-secondary)")
                .set("margin-bottom", "var(--vaadin-gap-xs)");

        setupGrid();

        add(title);

        Div card = new Div();
        card.setSizeFull();
        card.addClassName("card");
        card.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--vaadin-gap-s)");
        card.add(toolbar, typeTabs, grid);
        add(card);
        expand(card);

        // Row selection opens the StarPass-style detail panel
        add(detailPanel);
        detailPanel.setCloseCallback(() -> grid.asSingleSelect().clear());
        grid.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() != null) {
                showTransactionDetail(e.getValue());
            } else {
                detailPanel.setVisible(false);
            }
        });
        // V25 grids don't select on row click by default
        grid.addItemClickListener(e -> grid.select(e.getItem()));
    }

    private final Span allCount = tabBadge();
    private final Span expenseCount = tabBadge();
    private final Span incomeCount = tabBadge();
    private final Span transferCount = tabBadge();

    private static Span tabBadge() {
        Span badge = new Span();
        badge.addClassName("nav-badge");
        badge.getStyle().set("margin-left", "6px");
        badge.setVisible(false);
        return badge;
    }

    private void setupTabs() {
        Tab all = new Tab(new Span(getTranslation("nav.history")), allCount);
        Tab expenses = new Tab(new Span(getTranslation("transaction.type.expense")), expenseCount);
        Tab income = new Tab(new Span(getTranslation("transaction.type.income")), incomeCount);
        Tab transfers = new Tab(new Span(getTranslation("transaction.type.transfer")), transferCount);

        typeTabs.add(all, expenses, income, transfers);
        typeTabs.addSelectedChangeListener(event -> {
            Tab selectedTab = event.getSelectedTab();
            if (selectedTab == all) selectedTypeFilter = null;
            else if (selectedTab == expenses) selectedTypeFilter = Transaction.TransactionType.EXPENSE;
            else if (selectedTab == income) selectedTypeFilter = Transaction.TransactionType.INCOME;
            else if (selectedTab == transfers) selectedTypeFilter = Transaction.TransactionType.TRANSFER;
            refreshGrid();
        });
    }

    private void updateFilters() {
        ListDataProvider<Transaction> dataProvider = (ListDataProvider<Transaction>) grid.getDataProvider();
        String filter = searchField.getValue().toLowerCase();

        String payeeFilter = headerPayeeFilter.getValue() != null
                ? headerPayeeFilter.getValue().toLowerCase() : "";
        String categoryFilter = headerCategoryFilter.getValue();

        dataProvider.setFilter(t -> {
            boolean searchMatch = filter.isEmpty()
                    || (t.getPayee() != null && t.getPayee().toLowerCase().contains(filter))
                    || (t.getMemo() != null && t.getMemo().toLowerCase().contains(filter))
                    || (t.getCategory() != null && t.getCategory().getFullName().toLowerCase().contains(filter));
            boolean payeeMatch = payeeFilter.isEmpty()
                    || (t.getPayee() != null && t.getPayee().toLowerCase().contains(payeeFilter));
            boolean categoryMatch = categoryFilter == null
                    || (t.getCategory() != null && t.getCategory().getFullName().equals(categoryFilter));
            return searchMatch && payeeMatch && categoryMatch;
        });
        updateTotalsFooter();
    }

    private void setupGrid() {
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        com.vaadin.flow.component.button.Button emptyAdd =
                new com.vaadin.flow.component.button.Button(getTranslation("empty.hint"), e -> openTransactionDialog(new Transaction()));
        emptyAdd.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_TERTIARY);
        grid.setEmptyStateComponent(new com.cuenti.app.views.components.EmptyStateNotice(
                VaadinIcon.LIST, getTranslation("empty.title"), null, emptyAdd));
        grid.addItemDoubleClickListener(e -> openTransactionDialog(e.getItem()));
        grid.setSizeFull();

        // 1. Type + icon avatar
        grid.addComponentColumn(t -> {
            Div avatar = new Div();
            avatar.getStyle()
                    .set("width", "32px").set("height", "32px").set("border-radius", "50%")
                    .set("display", "flex").set("align-items", "center").set("justify-content", "center")
                    .set("flex-shrink", "0");
            String bg;
            if (t.getType() == Transaction.TransactionType.INCOME)        bg = "color-mix(in srgb, var(--aura-green) 15%, transparent)";
            else if (t.getType() == Transaction.TransactionType.TRANSFER) bg = "color-mix(in srgb, var(--aura-accent-color) 15%, transparent)";
            else                                                           bg = "color-mix(in srgb, var(--aura-red) 15%, transparent)";
            avatar.getStyle().set("background", bg);
            Icon icon = getPaymentIcon(t);
            String iconColor;
            if (t.getType() == Transaction.TransactionType.INCOME)        iconColor = "var(--aura-green)";
            else if (t.getType() == Transaction.TransactionType.TRANSFER) iconColor = "var(--aura-accent-color)";
            else                                                           iconColor = "var(--aura-red)";
            icon.getStyle().set("font-size", "14px").set("color", iconColor);
            avatar.add(icon);
            return avatar;
        }).setHeader("").setWidth("48px").setFlexGrow(0);

        // 2. Date — shown once per day while sorted by date (visual day grouping)
        dateCol = grid.addComponentColumn(t -> {
            String formatted = t.getTransactionDate().format(getDateTimeFormatter());
            Span date = new Span(dayGroupingActive && !firstOfDayIds.contains(t.getId()) ? "" : formatted);
            date.getElement().setAttribute("title", formatted);
            date.getStyle()
                    .set("font-size", "var(--aura-font-size-s)")
                    .set("color", "var(--vaadin-text-color-secondary)");
            return date;
        }).setHeader(getTranslation("transactions.date"))
                .setSortable(true).setComparator(Transaction::getTransactionDate)
                .setAutoWidth(true).setFlexGrow(0);

        // 3. Payee + account stacked
        payeeCol = grid.addComponentColumn(t -> {
            Span payee = new Span(t.getPayee() != null ? t.getPayee() : "—");
            payee.getStyle().set("font-weight", "600").set("font-size", "var(--aura-font-size-s)");

            Account acc = t.getType() == Transaction.TransactionType.INCOME ? t.getToAccount() : t.getFromAccount();
            String accName = acc != null ? acc.getAccountName() : "";
            if (t.getType() == Transaction.TransactionType.TRANSFER && t.getFromAccount() != null && t.getToAccount() != null) {
                accName = t.getFromAccount().getAccountName() + " → " + t.getToAccount().getAccountName();
            }
            Span account = new Span(accName);
            account.getStyle()
                    .set("font-size", "var(--aura-font-size-xs)")
                    .set("color", "var(--vaadin-text-color-secondary)");

            Div stack = new Div(payee, account);
            stack.getStyle().set("display", "flex").set("flex-direction", "column")
                    .set("gap", "1px").set("padding", "var(--vaadin-gap-xs) 0");
            return stack;
        }).setHeader(getTranslation("transactions.payee"))
                .setSortable(true)
                .setComparator(Comparator.comparing(t -> t.getPayee() != null ? t.getPayee() : ""))
                .setAutoWidth(true);

        // 4. Category (plain text)
        categoryCol = grid.addComponentColumn(t -> {
            String cat;
            if (t.getSplits() != null && !t.getSplits().isEmpty()) {
                String s = getTranslation("transactions.split");
                cat = s.startsWith("!") ? "Split" : s;
            } else {
                cat = t.getCategory() != null ? t.getCategory().getFullName() : "";
            }
            if (cat.isBlank()) return new Span();
            Span text = new Span(cat);
            text.getStyle()
                    .set("font-size", "var(--aura-font-size-s)")
                    .set("color", "var(--vaadin-text-color)");
            return text;
        }).setHeader(getTranslation("transactions.category"))
                .setSortable(true)
                .setComparator(Comparator.comparing(t -> t.getCategory() != null ? t.getCategory().getFullName() : ""))
                .setAutoWidth(true);

        // 5. Tags
        tagsCol = grid.addComponentColumn(t -> {
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
        amountCol = grid.addComponentColumn(t -> {
            Account selected = accountSelector.getValue();
            boolean allSelected = (selected == null) || (selected.getId() != null && selected.getId().equals(-1L));
            boolean isCredit = (t.getType() == Transaction.TransactionType.INCOME)
                    || (t.getType() == Transaction.TransactionType.TRANSFER
                        && selected != null && t.getToAccount() != null
                        && t.getToAccount().getId().equals(selected.getId()));

            String sign  = isCredit ? "+" : "−";
            String color = isCredit ? "var(--aura-green)" : "var(--aura-red)";
            if (t.getType() == Transaction.TransactionType.TRANSFER && allSelected)
                color = "var(--aura-accent-color)";

            Span s = new Span(sign + formatCurrency(t.getAmount()));
            s.getStyle().set("font-weight", "700").set("font-size", "var(--aura-font-size-s)")
                    .set("color", color).set("white-space", "nowrap");
            return s;
        }).setHeader(getTranslation("dialog.amount"))
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setSortable(true).setComparator(Comparator.comparing(Transaction::getAmount))
                .setAutoWidth(true).setFlexGrow(0);

        // 7. Balance
        balanceCol = grid.addComponentColumn(t -> {
            Account selectedForBalance = accountSelector.getValue();
            if (mixedCurrencies && isAllAccountsSelected(selectedForBalance)) {
                Span na = new Span("—");
                na.getElement().setAttribute("title", getTranslation("transactions.balance_mixed"));
                na.getStyle().set("color", "var(--vaadin-text-color-disabled)");
                return na;
            }
            BigDecimal bal = balanceCache.getOrDefault(t.getId(), BigDecimal.ZERO);
            Span s = new Span(formatCurrency(bal));
            s.getStyle()
                    .set("font-size", "var(--aura-font-size-s)").set("font-weight", "500")
                    .set("color", bal.compareTo(BigDecimal.ZERO) >= 0
                            ? "var(--vaadin-text-color)" : "var(--aura-red)");
            return s;
        }).setHeader(getTranslation("accounts.balance"))
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setSortable(true).setAutoWidth(true).setFlexGrow(0);

        // 8. Memo — truncated
        memoCol = grid.addComponentColumn(t -> {
            if (t.getMemo() == null || t.getMemo().isBlank()) return new Span();
            Span s = new Span(t.getMemo());
            s.getStyle()
                    .set("font-size", "var(--aura-font-size-xs)")
                    .set("color", "var(--vaadin-text-color-secondary)")
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
            hl.getStyle().set("gap", "var(--vaadin-gap-xs)");

            Account selected = accountSelector.getValue();
            boolean allSelected = (selected == null) || (selected.getId() != null && selected.getId().equals(-1L));
            if (!allSelected) {
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
                        upBtn.getElement().setAttribute("aria-label", getTranslation("transactions.move_up"));Button downBtn = new Button(VaadinIcon.ARROW_DOWN.create(), e -> moveTransaction(t, 1));
                        downBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                        downBtn.setEnabled(idx < sameDay.size() - 1);
                        downBtn.setTooltipText(getTranslation("transactions.move_down"));
                        downBtn.getElement().setAttribute("aria-label", getTranslation("transactions.move_down"));hl.add(upBtn, downBtn);
                    }
                }
            }

            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> openTransactionDialog(t));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            editBtn.setTooltipText(getTranslation("transactions.edit"));

            editBtn.getElement().setAttribute("aria-label", getTranslation("transactions.edit"));Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e -> confirmDelete(t));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            deleteBtn.setTooltipText(getTranslation("transactions.delete"));

            deleteBtn.getElement().setAttribute("aria-label", getTranslation("transactions.delete"));hl.add(editBtn, deleteBtn);
            return hl;
        }).setHeader(getTranslation("transactions.actions")).setFrozenToEnd(true).setAutoWidth(true);

        // Compact card column for phones (hidden on desktop)
        com.vaadin.flow.component.grid.Grid.Column<Transaction> cardCol =
                grid.addComponentColumn(this::createMobileCard).setFlexGrow(1);
        cardCol.setVisible(false);

        java.util.List<com.vaadin.flow.component.grid.Grid.Column<Transaction>> desktopCols =
                new ArrayList<>(grid.getColumns());
        desktopCols.remove(cardCol);
        com.vaadin.flow.component.grid.Grid.Column<Transaction> actionsCol =
                desktopCols.get(desktopCols.size() - 1);


        grid.setMultiSort(true);

        // Per-column header filters
        com.vaadin.flow.component.grid.HeaderRow filterRow = grid.appendHeaderRow();
        headerPayeeFilter.setId("tx-payee-filter");
        headerPayeeFilter.setPlaceholder(getTranslation("transactions.payee"));
        headerPayeeFilter.setClearButtonVisible(true);
        headerPayeeFilter.setValueChangeMode(ValueChangeMode.LAZY);
        headerPayeeFilter.addValueChangeListener(e -> updateFilters());
        headerPayeeFilter.setWidthFull();
        headerPayeeFilter.addThemeVariants(com.vaadin.flow.component.textfield.TextFieldVariant.LUMO_SMALL);
        filterRow.getCell(payeeCol).setComponent(headerPayeeFilter);

        headerCategoryFilter.setPlaceholder(getTranslation("transactions.category"));
        headerCategoryFilter.setClearButtonVisible(true);
        headerCategoryFilter.setItems(categoryService.getAllCategories().stream()
                .map(c -> c.getFullName()).sorted().collect(Collectors.toList()));
        headerCategoryFilter.addValueChangeListener(e -> updateFilters());
        headerCategoryFilter.setWidthFull();
        filterRow.getCell(categoryCol).setComponent(headerCategoryFilter);


        // Filtered totals footer
        footerRow = grid.appendFooterRow();

        // Day grouping only makes sense while ordered by date
        grid.addSortListener(e -> {
            dayGroupingActive = e.getSortOrder().isEmpty()
                    || e.getSortOrder().get(0).getSorted() == dateCol;
            updateTotalsFooter();
        });

        // <520px: card layout · 520-767px: pruned table · >=768px: full table
        int[] lastWidth = {1400};
        Runnable reapply = () -> applyResponsiveColumns(lastWidth[0], cardCol, desktopCols, actionsCol);
        this.reapplyColumns = reapply;
        grid.addAttachListener(e -> {
            com.vaadin.flow.component.page.Page page = e.getUI().getPage();
            page.retrieveExtendedClientDetails(d -> {
                lastWidth[0] = d.getWindowInnerWidth();
                reapply.run();
            });
            page.addBrowserWindowResizeListener(re -> {
                lastWidth[0] = re.getWidth();
                reapply.run();
            });
            // restore user column preferences from the browser
            page.executeJs("return localStorage.getItem('cuenti.tx.cols') || ''")
                    .then(String.class, v -> {
                        if (v != null && !v.isEmpty()) {
                            for (String pair : v.split(",")) {
                                String[] kv = pair.split(":");
                                if (kv.length == 2) colPrefs.put(kv[0], "1".equals(kv[1]));
                            }
                            columnMenuItems.forEach((key, item) ->
                                    item.setChecked(colPrefs.getOrDefault(key, true)));
                            reapply.run();
                        }
                    });
        });

        grid.setHeightFull();
    }

    private void confirmDelete(Transaction t) {
        Dialog confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle(getTranslation("dialog.confirm_delete"));

        String message = t.getPayee() != null && !t.getPayee().isEmpty()
                ? t.getPayee() + "  —  " + formatCurrency(t.getAmount())
                : formatCurrency(t.getAmount());

        Div body = new Div();
        body.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "var(--vaadin-gap-s)");
        Span msg = new Span(getTranslation("dialog.confirm_delete_message") + "?");
        msg.getStyle().set("font-size", "var(--aura-font-size-s)").set("color", "var(--vaadin-text-color-secondary)");
        Span detail = new Span(message);
        detail.getStyle().set("font-weight", "700").set("font-size", "var(--aura-font-size-m)");
        body.add(msg, detail);
        confirmDialog.add(body);

        Button cancelBtn = new Button(getTranslation("dialog.cancel"), e -> confirmDialog.close());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button deleteBtn = new Button(getTranslation("transactions.delete"), VaadinIcon.TRASH.create(), e -> {
            transactionService.deleteTransaction(t);
            confirmDialog.close();
            refreshGrid();
            com.cuenti.app.views.components.UiNotifier.error(getTranslation("transactions.deleted"));
        });
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        confirmDialog.getFooter().add(cancelBtn, deleteBtn);
        confirmDialog.open();
    }

    private void moveTransaction(Transaction t, int visualDirection) {
        Account selected = accountSelector.getValue();
        if (selected == null || (selected.getId() != null && selected.getId().equals(-1L))) return;

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
        Account accountFilter = isAllAccountsSelected(selected) ? null : selected;

        LocalDateTime from = dateFrom.getValue() != null
                ? dateFrom.getValue().atStartOfDay() : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime to = dateTo.getValue() != null
                ? dateTo.getValue().atTime(23, 59, 59) : LocalDateTime.of(9999, 12, 31, 23, 59, 59);

        List<Transaction> window = transactionService.getTransactionsFiltered(
                currentUser, accountFilter, selectedTypeFilter, from, to);

        // The SQL running balance sums raw amounts; that's only meaningful in
        // one currency. Per-account view is always single-currency.
        mixedCurrencies = accountService.getAccountsByUser(currentUser).stream()
                .map(Account::getCurrency)
                .filter(Objects::nonNull)
                .distinct()
                .count() > 1;

        // Running balance computed in the database over the full history;
        // only the visible window's offsets are shifted by start balances.
        BigDecimal offset;
        if (accountFilter == null) {
            offset = accountService.getAccountsByUser(currentUser).stream()
                    .map(Account::getStartBalance)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            offset = accountFilter.getStartBalance() != null ? accountFilter.getStartBalance() : BigDecimal.ZERO;
        }
        balanceCache.clear();
        transactionService.getRunningBalances(currentUser, accountFilter, selectedTypeFilter, from, to)
                .forEach((id, bal) -> balanceCache.put(id, bal.add(offset)));

        updateTabCounts(window);
        allAccountTransactions = new ArrayList<>(window);
        allAccountTransactions.sort(Comparator.comparing(Transaction::getTransactionDate)
                .thenComparing(Transaction::getSortOrder)
                .reversed());
        grid.asSingleSelect().clear();
        grid.setItems(allAccountTransactions);
        updateFilters();
        updateTotalsFooter();
    }

    private void updateTabCounts(List<Transaction> window) {
        if (selectedTypeFilter != null) {
            // window only contains one type; per-type counts would mislead
            allCount.setVisible(false);
            expenseCount.setVisible(false);
            incomeCount.setVisible(false);
            transferCount.setVisible(false);
            return;
        }
        long expenses = window.stream().filter(t -> t.getType() == Transaction.TransactionType.EXPENSE).count();
        long income = window.stream().filter(t -> t.getType() == Transaction.TransactionType.INCOME).count();
        long transfers = window.stream().filter(t -> t.getType() == Transaction.TransactionType.TRANSFER).count();
        setTabCount(allCount, window.size());
        setTabCount(expenseCount, expenses);
        setTabCount(incomeCount, income);
        setTabCount(transferCount, transfers);
    }

    private void setTabCount(Span badge, long count) {
        badge.setText(String.valueOf(count));
        badge.setVisible(count > 0);
    }

    private boolean isAllAccountsSelected(Account selected) {
        return selected == null || (selected.getId() != null && selected.getId().equals(-1L));
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
        dialog.setCloseOnOutsideClick(false);
        dialog.setWidth("min(700px, 96vw)");
        dialog.setResizable(false);
        dialog.getElement().getStyle()
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
            "var(--aura-red)",    // expense
            "var(--aura-green)",  // income
            "var(--aura-accent-color)"   // transfer
        };
        Button[] typeBtns = {expenseBtn, incomeBtn, transferBtn};
        Tab[]    typeTabs = {expenseTab, incomeTab, transferTab};

        // Accent div that changes color based on selected type
        Div accentBar = new Div();
        accentBar.setWidthFull();
        accentBar.setHeight("4px");
        accentBar.getStyle()
                .set("border-radius", "var(--vaadin-radius-l) var(--vaadin-radius-l) 0 0")
                .set("transition", "background 0.2s");

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
                        .set("background", active ? TYPE_COLORS[i] : "var(--vaadin-background-container)")
                        .set("color", active ? "white" : "var(--vaadin-text-color-secondary)")
                        .set("border", "none")
                        .set("border-radius", "99px")
                        .set("font-weight", active ? "700" : "500")
                        .set("font-size", "var(--aura-font-size-s)")
                        .set("padding", "var(--vaadin-gap-xs) var(--vaadin-gap-m)")
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
        typeRow.getStyle().set("gap", "var(--vaadin-gap-xs)").set("padding", "var(--vaadin-gap-m) var(--vaadin-gap-l)").set("flex-wrap", "wrap");

        // ── Hero: Amount field ────────────────────────────────────────
        BigDecimalField amountField = new BigDecimalField();
        amountField.setWidthFull();
        amountField.setRequiredIndicatorVisible(true);
        amountField.setValue(currentFormTransaction[0].getAmount() != null ? currentFormTransaction[0].getAmount() : BigDecimal.ZERO);
        amountField.getStyle()
                .set("font-size", "var(--cuenti-font-size-xxl)")
                .set("font-weight", "800")
                .set("--vaadin-text-field-default-width", "100%");
        amountField.getElement().getStyle().set("font-size", "var(--cuenti-font-size-xxl)").set("font-weight", "800");

        Span amountLabel = new Span(getTranslation("dialog.amount").toUpperCase());
        amountLabel.getStyle()
                .set("font-size", "10px").set("font-weight", "700").set("letter-spacing", "0.08em")
                .set("color", "var(--vaadin-text-color-secondary)");

        // Split toggle button — next to amount
        Button splitToggleBtn = new Button(VaadinIcon.PIE_CHART.create());
        splitToggleBtn.setTooltipText(getTranslation("transactions.split_transaction"));
        splitToggleBtn.getElement().setAttribute("aria-label", getTranslation("transactions.split_transaction"));splitToggleBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        splitToggleBtn.getStyle().set("flex-shrink", "0");

        HorizontalLayout amountRow = new HorizontalLayout(amountField, splitToggleBtn);
        amountRow.setWidthFull();
        amountRow.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
        amountRow.setSpacing(false);
        amountRow.getStyle().set("gap", "var(--vaadin-gap-xs)");
        amountRow.expand(amountField);

        Div heroSection = new Div(amountLabel, amountRow);
        heroSection.setWidthFull();
        heroSection.getStyle()
                .set("padding", "var(--vaadin-gap-m) var(--vaadin-gap-l) var(--vaadin-gap-l)")
                .set("background", "var(--vaadin-background-container)")
                .set("border-bottom", "1px solid var(--vaadin-border-color-secondary)")
                .set("box-sizing", "border-box");

        // ── Date picker ───────────────────────────────────────────────
        DatePicker datePicker = new DatePicker(getTranslation("dialog.date"));
        datePicker.setLocale(getLocale());
        if (currentUser.getLocale() != null && currentUser.getLocale().startsWith("de")) {
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
        if (currentFormTransaction[0].getId() == null && accountSelector.getValue() != null
                && accountSelector.getValue().getId() != null && !accountSelector.getValue().getId().equals(-1L)) {
            accountCombo.setValue(accountSelector.getValue());
        }

        ComboBox<Account> toAccountCombo = new ComboBox<>(getTranslation("dialog.to"));
        toAccountCombo.setItems(userAccounts);
        toAccountCombo.setItemLabelGenerator(Account::getAccountName);
        toAccountCombo.setWidthFull();

        // ── Payee + Category ──────────────────────────────────────────
        ComboBox<String> payeeCombo = new ComboBox<>(getTranslation("transactions.payee"));
        List<Payee> allPayeesForAutofill = payeeService.getAllPayees();
        List<String> existingPayees = allPayeesForAutofill.stream().map(Payee::getName).distinct().toList();
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
        tagsCombo.setWidthFull();
        if (currentFormTransaction[0].getTags() != null && !currentFormTransaction[0].getTags().isEmpty()) {
            Set<String> tagNames = new HashSet<>(Arrays.asList(currentFormTransaction[0].getTags().split(",")));
            tagsCombo.setValue(tagService.getAllTags().stream()
                    .filter(t -> tagNames.contains(t.getName())).collect(Collectors.toSet()));
        }

        // Add a text field + button for creating new tags (separate from multi-select)
        TextField newTagField = new TextField();
        newTagField.setPlaceholder(getTranslation("dialog.tags"));
        newTagField.setWidth("100%");

        Button addNewTagBtn = new Button(VaadinIcon.PLUS.create(), ev -> {
            String newTagName = newTagField.getValue().trim();
            if (!newTagName.isEmpty()) {
                // Check if tag already exists
                boolean tagExists = tagService.getAllTags().stream()
                        .anyMatch(t -> t.getName().equalsIgnoreCase(newTagName));
                if (!tagExists) {
                    Tag newTag = Tag.builder().name(newTagName).build();
                    tagService.saveTag(newTag);
                }
                // Refresh combo items and add the tag to current selection
                Set<Tag> sel = new HashSet<>(tagsCombo.getValue());
                Tag newTag = tagService.getAllTags().stream()
                        .filter(t -> t.getName().equalsIgnoreCase(newTagName))
                        .findFirst()
                        .orElse(null);
                if (newTag != null) {
                    sel.add(newTag);
                    tagsCombo.setItems(tagService.getAllTags());
                    tagsCombo.setValue(sel);
                }
                newTagField.clear();
            }
        });
        addNewTagBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        HorizontalLayout tagsRow = new HorizontalLayout(tagsCombo, newTagField, addNewTagBtn);
        tagsRow.setWidthFull();
        tagsRow.setSpacing(false);
        tagsRow.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.END);
        tagsRow.getStyle().set("gap", "var(--vaadin-gap-s)");
        tagsCombo.getStyle().set("flex", "1 1 0");
        newTagField.getStyle().set("flex", "1 1 0");
        addNewTagBtn.getStyle().set("flex-shrink", "0");

        TextArea memoField = new TextArea(getTranslation("dialog.memo"));
        memoField.setValue(currentFormTransaction[0].getMemo() != null ? currentFormTransaction[0].getMemo() : "");
        memoField.setWidthFull();
        memoField.setMinHeight("60px");
        memoField.setMaxHeight("100px");

        // Autofill fields from payee defaults when a payee is selected
        payeeCombo.addValueChangeListener(e -> {
            if (!e.isFromClient()) return;
            String selectedName = e.getValue();
            if (selectedName == null || selectedName.isEmpty()) return;
            allPayeesForAutofill.stream()
                    .filter(p -> p.getName().equalsIgnoreCase(selectedName))
                    .findFirst()
                    .ifPresent(payee -> {
                        if (payee.getDefaultCategory() != null) {
                            categoryCombo.setItems(categoryService.getCategoriesByType(payee.getDefaultCategory().getType()));
                            categoryCombo.setValue(payee.getDefaultCategory());
                        }
                        if (payee.getDefaultPaymentMethod() != null
                                && payee.getDefaultPaymentMethod() != Transaction.PaymentMethod.NONE) {
                            paymentCombo.setValue(payee.getDefaultPaymentMethod());
                        }
                        if (payee.getDefaultMemo() != null && !payee.getDefaultMemo().isEmpty()) {
                            memoField.setValue(payee.getDefaultMemo());
                        }
                        if (payee.getDefaultTags() != null && !payee.getDefaultTags().isEmpty()) {
                            Set<String> tagNames = new HashSet<>(Arrays.asList(payee.getDefaultTags().split(",")));
                            tagsCombo.setValue(tagService.getAllTags().stream()
                                    .filter(t -> tagNames.contains(t.getName().trim()))
                                    .collect(Collectors.toSet()));
                        }
                    });
        });

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
        assetRow.getStyle().set("gap", "var(--vaadin-gap-s)").set("flex-wrap", "wrap");
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
        splitInputRow.getStyle().set("gap", "var(--vaadin-gap-s)").set("flex-wrap", "wrap");
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
        splitAddRow.getStyle().set("gap", "var(--vaadin-gap-s)");
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
        row1.getStyle().set("gap", "var(--vaadin-gap-m)").set("flex-wrap", "wrap");
        row1.getChildren().forEach(c -> c.getElement().getStyle().set("flex", "1 1 200px").set("min-width", "0"));
        // wraps to single column on mobile
        HorizontalLayout row2 = new HorizontalLayout(payeeCombo, categoryCombo);
        row2.setWidthFull(); row2.setSpacing(false);
        row2.getStyle().set("gap", "var(--vaadin-gap-m)").set("flex-wrap", "wrap");
        row2.getChildren().forEach(c -> c.getElement().getStyle().set("flex", "1 1 200px").set("min-width", "0"));
        // wraps to single column on mobile
        HorizontalLayout row3 = new HorizontalLayout(toAccountCombo, paymentCombo);
        row3.setWidthFull(); row3.setSpacing(false);
        row3.getStyle().set("gap", "var(--vaadin-gap-m)").set("flex-wrap", "wrap");
         row3.getChildren().forEach(c -> c.getElement().getStyle().set("flex", "1 1 200px").set("min-width", "0"));
         coreSection.add(row1, row2, row3, tagsRow, memoField, splitSection, assetSection, hiddenTabs);

        // Secondary: payment details (number)
        Div extraSection = createFormSection(null);
        extraSection.add(numberField);
        extraSection.setVisible(false);

        Button moreBtn = new Button(getTranslation("dialog.more_details"), VaadinIcon.ANGLE_DOWN.create());
        moreBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        moreBtn.getStyle().set("font-size", "var(--aura-font-size-xs)").set("color", "var(--vaadin-text-color-secondary)");
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
                        com.cuenti.app.views.components.UiNotifier.error(getTranslation("accounts.name_required"));
                        return;
                    }
                    if (amountField.getValue() == null || amountField.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                        com.cuenti.app.views.components.UiNotifier.error(getTranslation("dialog.amount_positive"));
                        return;
                    }
                    Transaction saveTx = currentFormTransaction[0];
                    saveTx.getSplits().clear();
                    for (TransactionSplit s : currentSplits) saveTx.addSplit(s);
                    saveFromTabs(saveTx, hiddenTabs, expenseTab, incomeTab, transferTab, datePicker, amountField, accountCombo, toAccountCombo, paymentCombo, numberField, payeeCombo, categoryCombo, assetCombo, unitsField, memoField, tagsCombo);
                    refreshGrid(); dialog.close();
                    com.cuenti.app.views.components.UiNotifier.success(getTranslation("transactions.saved"));
                });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        String addKeepLabel = transaction.getId() == null ? getTranslation("dialog.add_keep") : getTranslation("dialog.save_keep");
        Button addKeepButton = new Button(addKeepLabel, e -> {
            if (accountCombo.isEmpty()) {
                com.cuenti.app.views.components.UiNotifier.error(getTranslation("accounts.name_required"));
                return;
            }
            if (amountField.getValue() == null || amountField.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                com.cuenti.app.views.components.UiNotifier.error(getTranslation("dialog.amount_positive"));
                return;
            }
            Transaction keepTx = currentFormTransaction[0];
            keepTx.getSplits().clear();
            for (TransactionSplit s : currentSplits) keepTx.addSplit(s);
            saveFromTabs(keepTx, hiddenTabs, expenseTab, incomeTab, transferTab, datePicker, amountField, accountCombo, toAccountCombo, paymentCombo, numberField, payeeCombo, categoryCombo, assetCombo, unitsField, memoField, tagsCombo);
            refreshGrid();
            com.cuenti.app.views.components.UiNotifier.success(getTranslation("transactions.saved"));
            currentFormTransaction[0] = new Transaction();
            currentSplits.clear();
            updateTotalAmount(currentSplits, amountField, categoryCombo);
        });
        addKeepButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        dialog.add(body);
        dialog.getFooter().add(cancelButton, addKeepButton, saveButton);
        dialog.open();
        amountField.focus();
    }

    /** Creates a padded section container with an optional all-caps label. */
    private Div createFormSection(String labelKey) {
        Div section = new Div();
        section.setWidthFull();
        section.getStyle()
                .set("display", "flex").set("flex-direction", "column")
                .set("gap", "var(--vaadin-gap-s)")
                .set("padding", "var(--vaadin-gap-m) var(--vaadin-gap-l)")
                .set("box-sizing", "border-box");
        if (labelKey != null && !labelKey.isBlank()) {
            Span label = new Span(labelKey.toUpperCase());
            label.getStyle()
                    .set("font-size", "10px").set("font-weight", "700").set("letter-spacing", "0.08em")
                    .set("color", "var(--vaadin-text-color-secondary)");
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

    /** Recomputes the filtered-sum footer and the first-row-per-day set. */
    private void updateTotalsFooter() {
        if (footerRow == null) {
            return;
        }
        List<Transaction> visible = grid.getListDataView().getItems().collect(Collectors.toList());

        Account selected = accountSelector.getValue();
        boolean allSelected = isAllAccountsSelected(selected);
        BigDecimal net = BigDecimal.ZERO;
        String targetCurrency = currentUser.getDefaultCurrency();
        for (Transaction t : visible) {
            BigDecimal amount = t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO;
            Account currencySource = t.getType() == Transaction.TransactionType.INCOME
                    ? t.getToAccount() : t.getFromAccount();
            BigDecimal converted = currencySource != null
                    ? exchangeRateService.convert(amount, currencySource.getCurrency(), targetCurrency)
                    : amount;
            if (t.getType() == Transaction.TransactionType.INCOME) {
                net = net.add(converted);
            } else if (t.getType() == Transaction.TransactionType.EXPENSE) {
                net = net.subtract(converted);
            } else if (!allSelected && selected != null) {
                if (t.getToAccount() != null && t.getToAccount().getId().equals(selected.getId())) net = net.add(converted);
                if (t.getFromAccount() != null && t.getFromAccount().getId().equals(selected.getId())) net = net.subtract(converted);
            }
        }

        Span sum = new Span("Σ " + formatCurrency(net));
        sum.addClassName(net.compareTo(BigDecimal.ZERO) >= 0 ? "amount-positive" : "amount-negative");
        footerRow.getCell(amountCol).setComponent(sum);

        Span count = new Span(visible.size() + " ×");
        count.getStyle().set("color", "var(--vaadin-text-color-secondary)")
                .set("font-size", "var(--aura-font-size-xs)");
        footerRow.getCell(payeeCol).setComponent(count);

        // First visible row of each day (display order)
        firstOfDayIds.clear();
        java.time.LocalDate lastDay = null;
        for (Transaction t : visible) {
            java.time.LocalDate day = t.getTransactionDate().toLocalDate();
            if (!day.equals(lastDay)) {
                firstOfDayIds.add(t.getId());
                lastDay = day;
            }
        }
        grid.getDataProvider().refreshAll();
    }

    private void applyResponsiveColumns(int width,
            com.vaadin.flow.component.grid.Grid.Column<Transaction> cardCol,
            java.util.List<com.vaadin.flow.component.grid.Grid.Column<Transaction>> desktopCols,
            com.vaadin.flow.component.grid.Grid.Column<Transaction> actionsCol) {
        boolean phone = width < 520;
        boolean narrow = width < 768;
        cardCol.setVisible(phone);
        desktopCols.forEach(c -> c.setVisible(!phone));
        if (!phone) {
            categoryCol.setVisible(colPrefs.getOrDefault("category", true));
            tagsCol.setVisible(!narrow && colPrefs.getOrDefault("tags", true));
            balanceCol.setVisible(!narrow && colPrefs.getOrDefault("balance", true));
            memoCol.setVisible(!narrow && colPrefs.getOrDefault("memo", true));
        }
    }

    /** Stacked row card used below 520px: payee/account, date, signed amount. */
    private Div createMobileCard(Transaction t) {
        Account acc = t.getType() == Transaction.TransactionType.INCOME ? t.getToAccount() : t.getFromAccount();
        String accName = acc != null ? acc.getAccountName() : "";
        if (t.getType() == Transaction.TransactionType.TRANSFER && t.getFromAccount() != null && t.getToAccount() != null) {
            accName = t.getFromAccount().getAccountName() + " → " + t.getToAccount().getAccountName();
        }

        Span payee = new Span(t.getPayee() != null ? t.getPayee() : "—");
        payee.getStyle().set("font-weight", "600").set("font-size", "var(--aura-font-size-s)");

        Span meta = new Span(t.getTransactionDate().format(getDateTimeFormatter()) + " · " + accName);
        meta.getStyle().set("font-size", "var(--aura-font-size-xs)")
                .set("color", "var(--vaadin-text-color-secondary)");

        Div left = new Div(payee, meta);
        left.getStyle().set("display", "flex").set("flex-direction", "column")
                .set("gap", "2px").set("min-width", "0").set("flex", "1");

        boolean isCredit = t.getType() == Transaction.TransactionType.INCOME;
        String sign = isCredit ? "+" : "−";
        Span amount = new Span(sign + formatCurrency(t.getAmount()));
        amount.addClassName(t.getType() == Transaction.TransactionType.TRANSFER
                ? "amount-neutral" : (isCredit ? "amount-positive" : "amount-negative"));
        amount.getStyle().set("white-space", "nowrap").set("font-size", "var(--aura-font-size-s)");

        Button edit = new Button(VaadinIcon.EDIT.create(), e -> openTransactionDialog(t));
        edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        edit.getElement().setAttribute("aria-label", getTranslation("transactions.edit"));

        Button del = new Button(VaadinIcon.TRASH.create(), e -> confirmDelete(t));
        del.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        del.getElement().setAttribute("aria-label", getTranslation("transactions.delete"));

        Div card = new Div(left, amount, edit, del);
        card.getStyle().set("display", "flex").set("align-items", "center")
                .set("gap", "var(--vaadin-gap-xs)").set("padding", "var(--vaadin-gap-xs) 0")
                .set("width", "100%");
        return card;
    }

    private void showTransactionDetail(Transaction t) {
        boolean isCredit = t.getType() == Transaction.TransactionType.INCOME;
        String sign = isCredit ? "+" : (t.getType() == Transaction.TransactionType.TRANSFER ? "" : "−");

        detailPanel.setHeader(getTranslation("transactions.details"),
                t.getPayee() != null && !t.getPayee().isBlank() ? t.getPayee() : "#" + t.getId());

        Span pill = new Span(getTranslation("transaction.type." + t.getType().name().toLowerCase()));
        pill.addClassName("pill-tint");
        String pillColor = switch (t.getType()) {
            case INCOME -> "var(--cuenti-chart-income)";
            case EXPENSE -> "var(--cuenti-chart-expense)";
            case TRANSFER -> "var(--aura-accent-color)";
        };
        pill.getStyle().set("--pill-color", pillColor);
        detailPanel.setPill(pill);

        Div content = detailPanel.content();
        content.removeAll();

        Span amount = new Span(sign + formatCurrency(t.getAmount()));
        amount.addClassName(t.getType() == Transaction.TransactionType.TRANSFER
                ? "amount-neutral" : (isCredit ? "amount-positive" : "amount-negative"));
        amount.getStyle().set("font-size", "var(--cuenti-font-size-xxl)")
                .set("font-weight", "700").set("font-family", "var(--aura-font-family)");
        content.add(amount);

        content.add(new com.cuenti.app.views.components.FieldRow(VaadinIcon.CALENDAR,
                getTranslation("transactions.date"),
                t.getTransactionDate().format(getDateTimeFormatter())));

        if (t.getType() == Transaction.TransactionType.TRANSFER
                && t.getFromAccount() != null && t.getToAccount() != null) {
            content.add(new com.cuenti.app.views.components.FieldRow(VaadinIcon.EXCHANGE,
                    getTranslation("dialog.account"),
                    t.getFromAccount().getAccountName() + " → " + t.getToAccount().getAccountName()));
        } else {
            Account acc = isCredit ? t.getToAccount() : t.getFromAccount();
            content.add(new com.cuenti.app.views.components.FieldRow(VaadinIcon.WALLET,
                    getTranslation("dialog.account"),
                    acc != null ? acc.getAccountName() : null));
        }

        String category = t.getSplits() != null && !t.getSplits().isEmpty()
                ? getTranslation("transactions.split")
                : (t.getCategory() != null ? t.getCategory().getFullName() : null);
        content.add(new com.cuenti.app.views.components.FieldRow(VaadinIcon.SITEMAP,
                getTranslation("transactions.category"), category));

        if (t.getPaymentMethod() != null && t.getPaymentMethod() != Transaction.PaymentMethod.NONE) {
            content.add(new com.cuenti.app.views.components.FieldRow(VaadinIcon.CREDIT_CARD,
                    getTranslation("dialog.payment_method"),
                    t.getPaymentMethod().getLabel()));
        }

        if (t.getTags() != null && !t.getTags().isBlank()) {
            HorizontalLayout tags = new HorizontalLayout();
            tags.setSpacing(false);
            tags.getStyle().set("gap", "4px").set("flex-wrap", "wrap");
            for (String tagName : t.getTags().split(",")) {
                tags.add(TagColorUtil.createTagBadge(tagName.trim()));
            }
            content.add(new com.cuenti.app.views.components.FieldRow(VaadinIcon.TAGS,
                    getTranslation("dialog.tags"), tags));
        }

        if (t.getMemo() != null && !t.getMemo().isBlank()) {
            content.add(new com.cuenti.app.views.components.FieldRow(VaadinIcon.COMMENT,
                    getTranslation("dialog.memo"), t.getMemo()));
        }

        Div footer = detailPanel.footer();
        footer.removeAll();

        Button edit = new Button(getTranslation("transactions.edit"), e -> {
            detailPanel.closePanel();
            openTransactionDialog(t);
        });

        Button delete = new Button(getTranslation("transactions.delete"), e -> {
            detailPanel.closePanel();
            confirmDelete(t);
        });
        delete.addClassName("btn-danger-outline");

        footer.add(edit, delete);
        detailPanel.openPanel();
    }

    /** Current global search term. Package-visible for tests. */
    String searchFieldValue() {
        return searchField.getValue();
    }

    /** Exports the currently filtered and sorted rows. Package-visible for tests. */
    String buildCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Date,Type,Payee,Account,Category,Tags,Amount,Memo\n");
        grid.getListDataView().getItems().forEach(t -> {
            Account acc = t.getType() == Transaction.TransactionType.INCOME ? t.getToAccount() : t.getFromAccount();
            String category = t.getCategory() != null ? t.getCategory().getFullName() : "";
            sb.append(csv(t.getTransactionDate().format(getDateTimeFormatter()))).append(',')
              .append(csv(t.getType() != null ? t.getType().name() : "")).append(',')
              .append(csv(t.getPayee())).append(',')
              .append(csv(acc != null ? acc.getAccountName() : "")).append(',')
              .append(csv(category)).append(',')
              .append(csv(t.getTags())).append(',')
              .append(t.getAmount() != null ? t.getAmount().toPlainString() : "").append(',')
              .append(csv(t.getMemo())).append('\n');
        });
        return sb.toString();
    }

    private static String csv(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n"))
                ? "\"" + escaped + "\"" : escaped;
    }

    private String formatCurrency(BigDecimal amount) {
        return com.cuenti.app.util.CurrencyFormat.format(amount, currentUser.getDefaultCurrency(), getLocale());
    }
}
