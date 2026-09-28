package com.cuenti.app.views;

import com.cuenti.app.model.*;
import com.cuenti.app.security.SecurityUtils;
import com.cuenti.app.service.*;
import com.cuenti.app.views.components.TagColorUtil;
import com.cuenti.app.views.components.TagField;
import com.cuenti.app.views.components.TransactionDialog;
import com.cuenti.app.views.components.TransactionDialogServices;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
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
        com.vaadin.flow.router.QueryParameters params = event.getLocation().getQueryParameters();
        if (Stream.of("q", "payee", "category", "tag").noneMatch(params.getParameters()::containsKey)) {
            return;
        }
        withFiltersBatched(() -> {
            // Deep link (quick search): replace all column filters with the given ones
            searchField.setValue(params.getSingleParameter("q").orElse(""));
            headerPayeeFilter.setValue(params.getSingleParameter("payee").orElse(""));
            headerCategoryFilter.setValue(params.getSingleParameter("category").orElse(null));
            setTagFilter(params.getSingleParameter("tag").orElse(null));
            // search the whole history, not only the default current month
            dateFrom.setValue(null);
            dateTo.setValue(null);
        });
        loadWindow(false);
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
    private final PayeeService payeeService;
    private final TagService tagService;
    private final com.cuenti.app.service.SavedViewService savedViewService;
    private final TransactionDialogServices dialogServices;
    private final SecurityUtils securityUtils;
    private final User currentUser;

    private final Grid<Transaction> grid = new Grid<>(Transaction.class, false);
    final TextField searchField = new TextField(); // package-visible for tests
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
    final ComboBox<String> headerCategoryFilter = new ComboBox<>(); // package-visible for tests
    final ComboBox<String> headerTagFilter = new ComboBox<>(); // package-visible for tests
    private final java.util.Set<Long> firstOfDayIds = new java.util.HashSet<>();
    private boolean dayGroupingActive = true;
    private boolean mixedCurrencies;
    private com.vaadin.flow.component.grid.FooterRow footerRow;
    private Runnable reapplyColumns = () -> {};
    private final Map<String, com.vaadin.flow.component.contextmenu.MenuItem> columnMenuItems = new HashMap<>();
    /** Transaction ids per day in manual sort order (top first), for the reorder buttons. */
    private final Map<LocalDate, List<Long>> sameDayOrder = new HashMap<>();
    /** Tag names for the tag filter; reloaded when data changes, not on every filter change. */
    private List<String> tagNames = List.of();
    /** While true, filter control changes don't reload or re-filter the grid. */
    private boolean batchingFilters;

    public TransactionHistoryView(TransactionService transactionService, AccountService accountService,
                                  UserService userService, ExchangeRateService exchangeRateService,
                                  CategoryService categoryService,
                                  PayeeService payeeService, TagService tagService, SecurityUtils securityUtils,
                                  com.cuenti.app.service.SavedViewService savedViewService,
                                  TransactionDialogServices dialogServices) {
        this.dialogServices = dialogServices;
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.userService = userService;
        this.exchangeRateService = exchangeRateService;
        this.categoryService = categoryService;
        this.payeeService = payeeService;
        this.tagService = tagService;
        this.securityUtils = securityUtils;
        this.savedViewService = savedViewService;

        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        this.currentUser = userService.findByUsername(username);

        addClassNames("page-scroll", "page-shell");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        
        // initial control values would each trigger a reload; load once afterwards
        withFiltersBatched(this::setupUI);
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
        accountSelector.addValueChangeListener(e -> reloadWindow());
        accountSelector.setWidth("200px");

        LocalDate now = LocalDate.now();
        dateFrom.setPlaceholder(getTranslation("dialog.from"));
        dateFrom.setClearButtonVisible(true);
        dateFrom.addValueChangeListener(e -> reloadWindow());
        dateFrom.setWidth("150px");
        dateFrom.setLocale(getLocale());

        if (currentUser.getLocale() != null && currentUser.getLocale().startsWith("de")) {
            dateFrom.setI18n(com.cuenti.app.views.components.LocalizedDatePicker.germanI18n());
        }
        dateFrom.setValue(now.with(TemporalAdjusters.firstDayOfMonth()));

        dateTo.setPlaceholder(getTranslation("dialog.to"));
        dateTo.setClearButtonVisible(true);
        dateTo.addValueChangeListener(e -> reloadWindow());
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

        Button viewsBtn = new Button(getTranslation("views.title"), VaadinIcon.BOOKMARK.create(),
                e -> openSavedViewsDialog());
        viewsBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        bulkBtn.setText(getTranslation("bulk.select"));
        bulkBtn.setIcon(VaadinIcon.CHECK_SQUARE_O.create());
        bulkBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        bulkBtn.addClickListener(e -> toggleBulkMode());

        HorizontalLayout actionsRow = new HorizontalLayout(viewsBtn, bulkBtn, columnsMenu, exportAnchor, addButton);
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
        card.addClassName("card--flex");
        buildBulkBar();
        card.add(toolbar, typeTabs, bulkBar, grid);
        add(card);
        expand(card);

        // Row selection opens the StarPass-style detail panel
        add(detailPanel);
        detailPanel.setCloseCallback(() -> {
            if (!bulkMode) {
                grid.asSingleSelect().clear();
            }
        });
        wireSingleSelection();
        // V25 grids don't select on row click by default (in bulk mode the
        // same click toggles the row into the multi-selection)
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
            reloadWindow();
        });
    }

    // ── Bulk actions ────────────────────────────────────────────────────────

    final Button bulkBtn = new Button(); // package-visible for tests
    final HorizontalLayout bulkBar = new HorizontalLayout(); // package-visible for tests
    private final Span bulkCount = new Span();
    private boolean bulkMode = false;

    private void wireSingleSelection() {
        grid.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() != null) {
                showTransactionDetail(e.getValue());
            } else {
                detailPanel.setVisible(false);
            }
        });
    }

    private void buildBulkBar() {
        bulkBar.setWidthFull();
        bulkBar.setAlignItems(Alignment.CENTER);
        bulkBar.setVisible(false);
        bulkBar.getStyle()
                .set("gap", "var(--vaadin-gap-s)")
                .set("flex-wrap", "wrap")
                .set("padding", "var(--vaadin-gap-xs) var(--vaadin-gap-s)")
                .set("border-radius", "var(--vaadin-radius-m)")
                .set("background", "var(--aura-accent-surface)");

        bulkCount.getStyle().set("font-weight", "600")
                .set("font-size", "var(--aura-font-size-s)");

        Button categoryBtn = new Button(getTranslation("bulk.set_category"),
                VaadinIcon.SITEMAP.create(), e -> openBulkCategoryDialog());
        categoryBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        Button tagBtn = new Button(getTranslation("bulk.add_tag"),
                VaadinIcon.TAG.create(), e -> openBulkTagDialog());
        tagBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        Button deleteBtn = new Button(getTranslation("bulk.delete"),
                VaadinIcon.TRASH.create(), e -> bulkDelete());
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL,
                ButtonVariant.LUMO_ERROR);

        Button doneBtn = new Button(getTranslation("bulk.done"), e -> toggleBulkMode());
        doneBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        Div spacer = new Div();
        bulkBar.add(bulkCount, categoryBtn, tagBtn, deleteBtn, spacer, doneBtn);
        bulkBar.expand(spacer);
    }

    void toggleBulkMode() {
        bulkMode = !bulkMode;
        bulkBar.setVisible(bulkMode);
        bulkBtn.setText(getTranslation(bulkMode ? "bulk.done" : "bulk.select"));
        if (bulkMode) {
            detailPanel.setVisible(false);
            grid.setSelectionMode(Grid.SelectionMode.MULTI);
            grid.addSelectionListener(e -> updateBulkCount());
            updateBulkCount();
        } else {
            grid.setSelectionMode(Grid.SelectionMode.SINGLE);
            wireSingleSelection();
        }
    }

    private void updateBulkCount() {
        bulkCount.setText(getTranslation("bulk.selected", grid.getSelectedItems().size()));
    }

    private void bulkDelete() {
        Set<Transaction> selection = new HashSet<>(grid.getSelectedItems());
        if (selection.isEmpty()) {
            return;
        }
        com.cuenti.app.views.components.DeleteConfirm.show(
                getTranslation("dialog.confirm_delete"),
                getTranslation("bulk.delete_message", selection.size()),
                getTranslation("dialog.delete"),
                getTranslation("dialog.cancel"),
                getTranslation("error.delete_failed"),
                () -> {
                    selection.forEach(transactionService::deleteTransaction);
                    grid.deselectAll();
                    refreshGrid();
                    com.cuenti.app.views.components.UiNotifier.success(
                            getTranslation("bulk.deleted", selection.size()));
                });
    }

    private void openBulkCategoryDialog() {
        Set<Transaction> selection = new HashSet<>(grid.getSelectedItems());
        if (selection.isEmpty()) {
            return;
        }
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("bulk.set_category"));
        dialog.setWidth("min(380px, 96vw)");

        ComboBox<Category> combo = new ComboBox<>(getTranslation("transactions.category"));
        combo.setItems(categoryService.getAllCategories().stream()
                .sorted(java.util.Comparator.comparing(Category::getFullName)).toList());
        combo.setItemLabelGenerator(Category::getFullName);
        combo.setWidthFull();

        Div body = new Div(combo);
        body.addClassName("dialog-body");
        dialog.add(body);

        Button save = new Button(getTranslation("dialog.save"), e -> {
            Category category = combo.getValue();
            if (category == null) {
                combo.setInvalid(true);
                return;
            }
            selection.forEach(t -> {
                t.setCategory(category);
                transactionService.saveTransaction(t);
            });
            grid.deselectAll();
            refreshGrid();
            dialog.close();
            com.cuenti.app.views.components.UiNotifier.success(
                    getTranslation("bulk.updated", selection.size()));
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(cancel, save);
        dialog.open();
        combo.focus();
    }

    private void openBulkTagDialog() {
        Set<Transaction> selection = new HashSet<>(grid.getSelectedItems());
        if (selection.isEmpty()) {
            return;
        }
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("bulk.add_tag"));
        dialog.setWidth("min(380px, 96vw)");

        TagField tagsField = new TagField(tagService, getTranslation("dialog.tags"));
        tagsField.setWidthFull();

        Div body = new Div(tagsField);
        body.addClassName("dialog-body");
        dialog.add(body);

        Button save = new Button(getTranslation("dialog.save"), e -> {
            List<String> added = com.cuenti.app.util.TagNames.parse(tagsField.getValue());
            if (added.isEmpty()) {
                tagsField.setInvalid(true);
                return;
            }
            selection.forEach(t -> {
                List<String> tags = new ArrayList<>(com.cuenti.app.util.TagNames.parse(t.getTags()));
                tags.addAll(added);
                t.setTags(com.cuenti.app.util.TagNames.join(tags));
                transactionService.saveTransaction(t);
            });
            grid.deselectAll();
            refreshGrid();
            dialog.close();
            com.cuenti.app.views.components.UiNotifier.success(
                    getTranslation("bulk.updated", selection.size()));
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(cancel, save);
        dialog.open();
        tagsField.getComboBox().focus();
    }

    // ── Saved filter views ──────────────────────────────────────────────────

    String serializeFilters() { // package-visible for tests
        Account selected = accountSelector.getValue();
        String account = (selected == null || isAllAccountsSelected(selected))
                ? "all" : String.valueOf(selected.getId());
        String type = selectedTypeFilter == null ? "ALL" : selectedTypeFilter.name();
        String from = dateFrom.getValue() != null ? dateFrom.getValue().toString() : "";
        String to = dateTo.getValue() != null ? dateTo.getValue().toString() : "";
        return "account=" + account + "|type=" + type + "|from=" + from + "|to=" + to
                + "|q=" + encodeParam(searchField.getValue())
                + "|payee=" + encodeParam(headerPayeeFilter.getValue())
                + "|category=" + encodeParam(headerCategoryFilter.getValue())
                + "|tag=" + encodeParam(headerTagFilter.getValue());
    }

    private static String encodeParam(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String decodeParam(Map<String, String> map, String key) {
        return java.net.URLDecoder.decode(map.getOrDefault(key, ""), java.nio.charset.StandardCharsets.UTF_8);
    }

    void applyFilterParams(String params) { // package-visible for tests
        Map<String, String> map = new HashMap<>();
        for (String pair : params.split("\\|")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                map.put(pair.substring(0, idx), pair.substring(idx + 1));
            }
        }
        withFiltersBatched(() -> {
            String account = map.getOrDefault("account", "all");
            accountSelector.getListDataView().getItems()
                    .filter(a -> "all".equals(account)
                            ? isAllAccountsSelected(a)
                            : a.getId() != null && a.getId().toString().equals(account))
                    .findFirst().ifPresent(accountSelector::setValue);

            String from = map.getOrDefault("from", "");
            dateFrom.setValue(from.isEmpty() ? null : LocalDate.parse(from));
            String to = map.getOrDefault("to", "");
            dateTo.setValue(to.isEmpty() ? null : LocalDate.parse(to));

            String type = map.getOrDefault("type", "ALL");
            int tabIndex = switch (type) {
                case "EXPENSE" -> 1;
                case "INCOME" -> 2;
                case "TRANSFER" -> 3;
                default -> 0;
            };
            typeTabs.setSelectedIndex(tabIndex);

            searchField.setValue(decodeParam(map, "q"));
            headerPayeeFilter.setValue(decodeParam(map, "payee"));
            String category = decodeParam(map, "category");
            headerCategoryFilter.setValue(category.isEmpty() ? null : category);
            setTagFilter(decodeParam(map, "tag"));
        });
        loadWindow(false);
    }

    private void openSavedViewsDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("views.title"));
        dialog.setWidth("min(420px, 96vw)");
        com.vaadin.flow.component.icon.Icon headerIcon = VaadinIcon.BOOKMARK.create();
        headerIcon.addClassName("dialog-header-icon");
        dialog.getHeader().add(headerIcon);

        Div listContainer = new Div();
        listContainer.getStyle().set("display", "flex").set("flex-direction", "column")
                .set("gap", "var(--vaadin-gap-xs)");
        renderSavedViewList(listContainer, dialog);

        TextField nameField = new TextField(getTranslation("views.name"));
        nameField.setWidthFull();
        nameField.setMaxLength(100);

        Button saveBtn = new Button(getTranslation("views.save_current"), VaadinIcon.PLUS.create(), e -> {
            String name = nameField.getValue() == null ? "" : nameField.getValue().trim();
            if (name.isEmpty()) {
                nameField.setInvalid(true);
                return;
            }
            savedViewService.save(currentUser, name, serializeFilters());
            nameField.clear();
            renderSavedViewList(listContainer, dialog);
            com.cuenti.app.views.components.UiNotifier.success(getTranslation("views.saved"));
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        HorizontalLayout saveRow = new HorizontalLayout(nameField, saveBtn);
        saveRow.setWidthFull();
        saveRow.setAlignItems(Alignment.END);
        saveRow.setSpacing(false);
        saveRow.getStyle().set("gap", "var(--vaadin-gap-s)");
        saveRow.expand(nameField);

        Div body = new Div(listContainer, saveRow);
        body.addClassName("dialog-body");
        dialog.add(body);

        Button close = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(close);
        dialog.open();
    }

    private void renderSavedViewList(Div container, Dialog dialog) {
        container.removeAll();
        java.util.List<com.cuenti.app.model.SavedView> views = savedViewService.getViews(currentUser);
        if (views.isEmpty()) {
            Span none = new Span(getTranslation("views.none"));
            none.getStyle().set("color", "var(--vaadin-text-color-secondary)")
                    .set("font-size", "var(--aura-font-size-s)");
            container.add(none);
            return;
        }
        for (com.cuenti.app.model.SavedView view : views) {
            Button apply = new Button(view.getName(), VaadinIcon.BOOKMARK_O.create(), e -> {
                applyFilterParams(view.getParams());
                dialog.close();
            });
            apply.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            apply.getStyle().set("justify-content", "flex-start");

            Button remove = new Button(VaadinIcon.TRASH.create(), e -> {
                savedViewService.delete(currentUser, view);
                renderSavedViewList(container, dialog);
                com.cuenti.app.views.components.UiNotifier.success(getTranslation("views.deleted"));
            });
            remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL,
                    ButtonVariant.LUMO_ERROR);
            remove.getElement().setAttribute("aria-label",
                    getTranslation("dialog.delete") + " " + view.getName());

            HorizontalLayout row = new HorizontalLayout(apply, remove);
            row.setWidthFull();
            row.setAlignItems(Alignment.CENTER);
            row.setJustifyContentMode(JustifyContentMode.BETWEEN);
            container.add(row);
        }
    }

    private void updateFilters() {
        if (batchingFilters) {
            return;
        }
        ListDataProvider<Transaction> dataProvider = (ListDataProvider<Transaction>) grid.getDataProvider();
        String filter = searchField.getValue().toLowerCase();

        String payeeFilter = headerPayeeFilter.getValue() != null
                ? headerPayeeFilter.getValue().toLowerCase() : "";
        String categoryFilter = headerCategoryFilter.getValue();
        String tagFilter = headerTagFilter.getValue();

        dataProvider.setFilter(t -> {
            boolean searchMatch = filter.isEmpty()
                    || (t.getPayee() != null && t.getPayee().toLowerCase().contains(filter))
                    || (t.getMemo() != null && t.getMemo().toLowerCase().contains(filter))
                    || (t.getCategory() != null && t.getCategory().getFullName().toLowerCase().contains(filter))
                    || (t.getTags() != null && t.getTags().toLowerCase().contains(filter));
            boolean payeeMatch = payeeFilter.isEmpty()
                    || (t.getPayee() != null && t.getPayee().toLowerCase().contains(payeeFilter));
            boolean categoryMatch = categoryFilter == null || matchesCategory(t, categoryFilter);
            boolean tagMatch = tagFilter == null || hasTag(t, tagFilter);
            return searchMatch && payeeMatch && categoryMatch && tagMatch;
        });
        updateTotalsFooter();
    }

    /**
     * A parent category includes its subcategories ("Bike" matches "Bike:Motor").
     * Split transactions match when any split is in the category.
     */
    static boolean matchesCategory(Transaction t, String fullName) {
        if (isSameOrSubcategory(t.getCategory(), fullName)) {
            return true;
        }
        return t.getSplits() != null
                && t.getSplits().stream().anyMatch(s -> isSameOrSubcategory(s.getCategory(), fullName));
    }

    private static boolean isSameOrSubcategory(com.cuenti.app.model.Category category, String fullName) {
        if (category == null) {
            return false;
        }
        String name = category.getFullName();
        return name.equals(fullName) || name.startsWith(fullName + ":");
    }

    /** Exact tag match on the comma-separated tag list, ignoring case and spaces. */
    static boolean hasTag(Transaction t, String tag) {
        return t.getTags() != null
                && Arrays.stream(t.getTags().split(",")).map(String::trim).anyMatch(tag.trim()::equalsIgnoreCase);
    }

    /** Reloads the tag filter choices (managed tags + tags used on transactions), keeping the selection. */
    private void refreshTagFilterItems() {
        String current = headerTagFilter.getValue();
        List<String> names = new ArrayList<>(tagNames);
        if (current != null && names.stream().noneMatch(current::equalsIgnoreCase)) {
            names.add(current);
        }
        withFiltersBatched(() -> {
            headerTagFilter.setItems(names);
            if (current != null && !current.equals(headerTagFilter.getValue())) {
                headerTagFilter.setValue(current);
            }
        });
    }

    /** Selects a tag in the header filter using the known spelling; blank clears it. */
    private void setTagFilter(String tag) {
        if (tag == null || tag.isBlank()) {
            headerTagFilter.clear();
            return;
        }
        String wanted = tag.trim();
        String match = tagNames.stream().filter(wanted::equalsIgnoreCase).findFirst().orElse(null);
        if (match == null) {
            List<String> names = new ArrayList<>(tagNames);
            names.add(wanted);
            headerTagFilter.setItems(names);
            match = wanted;
        }
        headerTagFilter.setValue(match);
    }

    /** Runs control changes without reloading or re-filtering per change; callers reload once afterwards. */
    private void withFiltersBatched(Runnable changes) {
        boolean outer = batchingFilters;
        batchingFilters = true;
        try {
            changes.run();
        } finally {
            batchingFilters = outer;
        }
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
                for (String tagName : com.cuenti.app.util.TagNames.parse(t.getTags())) {
                    hl.add(TagColorUtil.createTagBadge(tagName));
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
                List<Long> sameDay = sameDayOrder.getOrDefault(t.getTransactionDate().toLocalDate(), List.of());

                if (sameDay.size() > 1) {
                    int index = sameDay.indexOf(t.getId());
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

        headerTagFilter.setPlaceholder(getTranslation("dialog.tags"));
        headerTagFilter.setClearButtonVisible(true);
        tagNames = tagService.getAllTagNames();
        headerTagFilter.setItems(tagNames);
        headerTagFilter.addValueChangeListener(e -> updateFilters());
        headerTagFilter.setWidthFull();
        filterRow.getCell(tagsCol).setComponent(headerTagFilter);


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

    /** Reloads after data changed (save, delete, import): also refreshes the tag filter choices. */
    private void refreshGrid() {
        loadWindow(true);
    }

    /** Reload triggered by a filter control (account, dates, type); skipped while batching. */
    private void reloadWindow() {
        if (!batchingFilters) {
            loadWindow(false);
        }
    }

    private void loadWindow(boolean dataChanged) {
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
        List<Account> userAccounts = accountService.getAccountsByUser(currentUser);
        mixedCurrencies = userAccounts.stream()
                .map(Account::getCurrency)
                .filter(Objects::nonNull)
                .distinct()
                .count() > 1;

        // Running balance computed in the database over the full history;
        // only the visible window's offsets are shifted by start balances.
        BigDecimal offset;
        if (accountFilter == null) {
            offset = userAccounts.stream()
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
        sameDayOrder.clear();
        allAccountTransactions.stream()
                .collect(Collectors.groupingBy(t -> t.getTransactionDate().toLocalDate()))
                .forEach((day, sameDay) -> sameDayOrder.put(day, sameDay.stream()
                        .sorted(Comparator.comparing(Transaction::getSortOrder).reversed()
                                .thenComparing(Transaction::getId))
                        .map(Transaction::getId)
                        .toList()));

        grid.deselectAll();
        grid.setItems(allAccountTransactions);
        if (dataChanged) {
            tagNames = tagService.getAllTagNames();
            refreshTagFilterItems();
        }
        // applies the column filters and recomputes the footer
        updateFilters();
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

    void openTransactionDialog(Transaction transaction) { // package-visible for tests
        Account selected = accountSelector.getValue();
        TransactionDialog.Options options = new TransactionDialog.Options()
                .defaultAccount(selected != null && selected.getId() != null && !selected.getId().equals(-1L) ? selected : null)
                .onSaved(this::refreshGrid);
        new TransactionDialog(dialogServices, currentUser, transaction, options).open();
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
        String targetCurrency = currentUser.getDefaultCurrency();
        // Sum per source currency first, then convert once per currency
        Map<String, BigDecimal> netByCurrency = new HashMap<>();
        for (Transaction t : visible) {
            BigDecimal amount = t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO;
            Account currencySource = t.getType() == Transaction.TransactionType.INCOME
                    ? t.getToAccount() : t.getFromAccount();
            String currency = currencySource != null && currencySource.getCurrency() != null
                    ? currencySource.getCurrency() : targetCurrency;
            BigDecimal signed = BigDecimal.ZERO;
            if (t.getType() == Transaction.TransactionType.INCOME) {
                signed = amount;
            } else if (t.getType() == Transaction.TransactionType.EXPENSE) {
                signed = amount.negate();
            } else if (!allSelected && selected != null) {
                if (t.getToAccount() != null && t.getToAccount().getId().equals(selected.getId())) signed = signed.add(amount);
                if (t.getFromAccount() != null && t.getFromAccount().getId().equals(selected.getId())) signed = signed.subtract(amount);
            }
            netByCurrency.merge(currency, signed, BigDecimal::add);
        }
        BigDecimal net = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : netByCurrency.entrySet()) {
            net = net.add(exchangeRateService.convert(e.getValue(), e.getKey(), targetCurrency));
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
                    getTranslation("payment_method." + t.getPaymentMethod().name())));
        }

        if (t.getTags() != null && !t.getTags().isBlank()) {
            HorizontalLayout tags = new HorizontalLayout();
            tags.setSpacing(false);
            tags.getStyle().set("gap", "4px").set("flex-wrap", "wrap");
            for (String tagName : com.cuenti.app.util.TagNames.parse(t.getTags())) {
                tags.add(TagColorUtil.createTagBadge(tagName));
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
