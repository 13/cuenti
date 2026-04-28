package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.Account;
import com.cuenti.homebanking.model.Currency;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.AccountService;
import com.cuenti.homebanking.service.CurrencyService;
import com.cuenti.homebanking.service.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.dnd.GridDropLocation;
import com.vaadin.flow.component.grid.dnd.GridDropMode;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Route(value = "manage-accounts", layout = MainLayout.class)
@PermitAll
public class AccountManagementView extends VerticalLayout implements HasDynamicTitle {

    @Override
    public String getPageTitle() {
        return getTranslation("accounts.title") + " | " + getTranslation("app.name");
    }


    private final AccountService accountService;
    private final UserService userService;
    private final CurrencyService currencyService;
    private final SecurityUtils securityUtils;
    private final User currentUser;

    private final Grid<Account> grid = new Grid<>(Account.class, false);
    private final TextField searchField = new TextField();
    private List<Account> accounts;
    private Account draggedItem;

    public AccountManagementView(AccountService accountService, UserService userService,
                                 CurrencyService currencyService, SecurityUtils securityUtils) {
        this.accountService = accountService;
        this.userService = userService;
        this.currencyService = currencyService;
        this.securityUtils = securityUtils;

        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));
        this.currentUser = userService.findByUsername(username);

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("padding", "var(--lumo-space-m)")
                .set("overflow", "hidden");

        setupUI();
        updateList();
    }

    private void setupUI() {
        Span title = new Span(getTranslation("accounts.title"));
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-xxl)")
                .set("font-weight", "700")
                .set("color", "var(--lumo-header-text-color)");

        searchField.setPlaceholder(getTranslation("transactions.search"));
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.EAGER);
        searchField.addValueChangeListener(e -> updateFilters());
        searchField.setWidth("300px");

        Button addButton = new Button(getTranslation("accounts.add"), new Icon(VaadinIcon.PLUS), e -> openAccountDialog(new Account()));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(searchField, addButton);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.expand(searchField);
        toolbar.setSpacing(false);
        toolbar.getStyle()
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "12px")
                .set("gap", "var(--lumo-space-s)");

        configureGrid();

        // Always use card layout
        Div card = new Div();
        card.setSizeFull();
        card.getStyle()
                .set("background-color", "var(--lumo-base-color)")
                .set("border-radius", "20px")
                .set("padding", "var(--lumo-space-l)")
                .set("box-shadow", "0 2px 12px rgba(0,0,0,0.06)")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-s)")
                .set("box-sizing", "border-box");
        card.add(toolbar, grid);
        add(title, card);
        expand(card);
    }

    private void updateFilters() {
        ListDataProvider<Account> dataProvider = (ListDataProvider<Account>) grid.getDataProvider();
        String filter = searchField.getValue().toLowerCase();
        dataProvider.setFilter(a -> {
            boolean nameMatch = a.getAccountName() != null && a.getAccountName().toLowerCase().contains(filter);
            boolean numberMatch = a.getAccountNumber() != null && a.getAccountNumber().toLowerCase().contains(filter);
            boolean institutionMatch = a.getInstitution() != null && a.getInstitution().toLowerCase().contains(filter);
            return filter.isEmpty() || nameMatch || numberMatch || institutionMatch;
        });
    }

    private void configureGrid() {
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);

        // Name + group stacked
        grid.addComponentColumn(acc -> {
            Span name = new Span(acc.getAccountName());
            name.getStyle().set("font-weight", "600").set("font-size", "var(--lumo-font-size-s)");
            Span group = new Span(acc.getAccountGroup() != null ? acc.getAccountGroup() : "");
            group.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("color", "var(--lumo-secondary-text-color)");
            Div stack = new Div(name, group);
            stack.getStyle().set("display","flex").set("flex-direction","column").set("gap","1px").set("padding","var(--lumo-space-xs) 0");
            return stack;
        }).setHeader(getTranslation("accounts.name")).setSortable(true)
                .setComparator(java.util.Comparator.comparing(Account::getAccountName)).setAutoWidth(true);

        grid.addComponentColumn(acc -> {
            Span badge = new Span(getTranslation("account.type." + acc.getAccountType().name().toLowerCase()));
            badge.getStyle().set("font-size","10px").set("font-weight","700").set("letter-spacing","0.05em")
                    .set("padding","2px 8px").set("border-radius","99px")
                    .set("background","var(--lumo-contrast-10pct)").set("color","var(--lumo-secondary-text-color)");
            return badge;
        }).setHeader(getTranslation("accounts.type")).setSortable(true)
                .setComparator(java.util.Comparator.comparing(acc -> acc.getAccountType().name())).setAutoWidth(true);

        grid.addComponentColumn(acc -> {
            Span s = new Span(acc.getInstitution() != null ? acc.getInstitution() : "");
            s.getStyle().set("font-size","var(--lumo-font-size-s)").set("color","var(--lumo-secondary-text-color)");
            return s;
        }).setHeader(getTranslation("accounts.institution")).setSortable(true)
                .setComparator(java.util.Comparator.comparing(acc -> acc.getInstitution() != null ? acc.getInstitution() : "")).setAutoWidth(true);

        grid.addComponentColumn(acc -> {
            Span s = new Span(acc.getCurrency());
            s.getStyle().set("font-size","var(--lumo-font-size-s)");
            return s;
        }).setHeader(getTranslation("accounts.currency")).setAutoWidth(true);

        grid.addComponentColumn(acc -> {
            Span s = new Span(acc.getBalance() != null ? acc.getBalance().toPlainString() : "0");
            boolean neg = acc.getBalance() != null && acc.getBalance().compareTo(java.math.BigDecimal.ZERO) < 0;
            s.getStyle().set("font-weight","700").set("font-size","var(--lumo-font-size-s)")
                    .set("color", neg ? "var(--lumo-error-color)" : "var(--lumo-body-text-color)");
            return s;
        }).setHeader(getTranslation("accounts.balance")).setSortable(true)
                .setComparator(java.util.Comparator.comparing(acc -> acc.getBalance() != null ? acc.getBalance() : java.math.BigDecimal.ZERO)).setAutoWidth(true);

        grid.addComponentColumn(account -> {
            Button editButton = new Button(new Icon(VaadinIcon.EDIT), e -> openAccountDialog(account));
            editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            editButton.setTooltipText(getTranslation("transactions.edit"));
            
            Button deleteButton = new Button(new Icon(VaadinIcon.TRASH), e -> deleteAccount(account));
            deleteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            deleteButton.setTooltipText(getTranslation("transactions.delete"));
            
            return new HorizontalLayout(editButton, deleteButton);
        }).setHeader(getTranslation("transactions.actions")).setFrozenToEnd(true).setAutoWidth(true);

        // Drag and Drop for sorting (only enabled when not filtering)
        grid.setRowsDraggable(true);
        grid.setDropMode(GridDropMode.BETWEEN);

        grid.addDragStartListener(e -> draggedItem = e.getDraggedItems().get(0));

        grid.addDropListener(e -> {
            if (!searchField.isEmpty()) {
                Notification.show(getTranslation("accounts.sorting_disabled"));
                return;
            }
            
            Account targetItem = e.getDropTargetItem().orElse(null);
            GridDropLocation dropLocation = e.getDropLocation();

            if (targetItem == null || targetItem.equals(draggedItem)) {
                return;
            }

            accounts.remove(draggedItem);
            int index = accounts.indexOf(targetItem);
            if (dropLocation == GridDropLocation.BELOW) {
                index++;
            }
            accounts.add(index, draggedItem);
            
            grid.getDataProvider().refreshAll();
            accountService.updateSortOrders(accounts);
        });

        grid.setSizeFull();
    }

    private void updateList() {
        accounts = new ArrayList<>(accountService.getAccountsByUser(currentUser));
        grid.setItems(accounts);
        updateFilters();
    }

    private void openAccountDialog(Account account) {
        Dialog dialog = new Dialog();
        dialog.setWidth("min(560px, 96vw)");
        dialog.setResizable(false);
        dialog.getElement().getStyle()
                .set("--lumo-border-radius-l", "20px")
                .set("overflow-x", "hidden");
        dialog.setHeaderTitle(account.getId() == null
                ? getTranslation("accounts.add") : getTranslation("accounts.edit"));

        // ── Fields ────────────────────────────────────────────────────
        TextField nameField = new TextField(getTranslation("accounts.name"));
        nameField.setPrefixComponent(VaadinIcon.WALLET.create());
        nameField.setWidthFull(); nameField.setRequired(true);

        ComboBox<Account.AccountType> typeCombo = new ComboBox<>(getTranslation("accounts.type"));
        typeCombo.setItems(Account.AccountType.values());
        typeCombo.setItemLabelGenerator(t -> getTranslation("account.type." + t.name().toLowerCase()));
        typeCombo.setWidthFull();

        ComboBox<String> groupCombo = new ComboBox<>(getTranslation("accounts.group"));
        groupCombo.setPrefixComponent(VaadinIcon.FOLDER.create());
        groupCombo.setItems(accounts.stream().map(Account::getAccountGroup)
                .filter(g -> g != null && !g.isEmpty()).distinct().collect(Collectors.toList()));
        groupCombo.setAllowCustomValue(true);
        groupCombo.addCustomValueSetListener(e -> groupCombo.setValue(e.getDetail()));
        groupCombo.setWidthFull();

        TextField institutionField = new TextField(getTranslation("accounts.institution"));
        institutionField.setPrefixComponent(VaadinIcon.BUILDING.create());
        institutionField.setWidthFull();

        TextField numberField = new TextField(getTranslation("accounts.number"));
        numberField.setPrefixComponent(VaadinIcon.BARCODE.create());
        numberField.setWidthFull();

        ComboBox<Currency> currencyCombo = new ComboBox<>(getTranslation("accounts.currency"));
        currencyCombo.setItems(currencyService.getAllCurrencies());
        currencyCombo.setItemLabelGenerator(c -> c.getCode() + " — " + c.getSymbol());
        currencyCombo.setWidthFull();

        BigDecimalField startBalanceField = new BigDecimalField(getTranslation("accounts.start_balance"));
        startBalanceField.setPrefixComponent(VaadinIcon.MONEY.create());
        startBalanceField.setValue(BigDecimal.ZERO);
        startBalanceField.setWidthFull();

        currencyCombo.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                int fd = e.getValue().getFracDigits();
                BigDecimal cur = startBalanceField.getValue();
                if (cur != null) startBalanceField.setValue(cur.setScale(fd, java.math.RoundingMode.HALF_UP));
            }
        });

        Checkbox excludeFromSummaryCheckbox = new Checkbox(getTranslation("accounts.exclude_from_summary"));
        Checkbox excludeFromReportsCheckbox = new Checkbox(getTranslation("accounts.exclude_from_reports"));

        // ── Layout ────────────────────────────────────────────────────
        Div coreSection = dialogSection(null);
        coreSection.add(nameField);
        coreSection.add(dialogRow(typeCombo, groupCombo));
        coreSection.add(dialogRow(institutionField, numberField));
        coreSection.add(dialogRow(currencyCombo, startBalanceField));

         Div flagsSection = dialogSection(getTranslation("accounts.options"));
        Div flagsRow = new Div(excludeFromSummaryCheckbox, excludeFromReportsCheckbox);
        flagsRow.getStyle().set("display","flex").set("flex-wrap","wrap").set("gap","var(--lumo-space-l)").set("padding","var(--lumo-space-xs) 0");
        flagsSection.add(flagsRow);

        // ── Binder ────────────────────────────────────────────────────
        Binder<Account> binder = new Binder<>(Account.class);
        binder.forField(nameField).asRequired(getTranslation("accounts.name_required")).bind(Account::getAccountName, Account::setAccountName);
        binder.bind(typeCombo, Account::getAccountType, Account::setAccountType);
        binder.bind(groupCombo, Account::getAccountGroup, Account::setAccountGroup);
        binder.bind(institutionField, Account::getInstitution, Account::setInstitution);
        binder.bind(numberField, Account::getAccountNumber, Account::setAccountNumber);
        binder.bind(startBalanceField, Account::getStartBalance, Account::setStartBalance);
        binder.bind(currencyCombo,
                acc -> currencyService.getAllCurrencies().stream().filter(c -> c.getCode().equals(acc.getCurrency())).findFirst().orElse(null),
                (acc, c) -> acc.setCurrency(c != null ? c.getCode() : "EUR"));
        binder.bind(excludeFromSummaryCheckbox, Account::isExcludeFromSummary, Account::setExcludeFromSummary);
        binder.bind(excludeFromReportsCheckbox, Account::isExcludeFromReports, Account::setExcludeFromReports);
        account.setUser(currentUser);
        binder.setBean(account);

        if (account.getCurrency() != null) {
            currencyService.getAllCurrencies().stream().filter(c -> c.getCode().equals(account.getCurrency())).findFirst()
                    .ifPresent(c -> { if (account.getStartBalance() != null)
                        startBalanceField.setValue(account.getStartBalance().setScale(c.getFracDigits(), java.math.RoundingMode.HALF_UP)); });
        }

        Div body = new Div(coreSection, flagsSection);
        body.getStyle().set("display","flex").set("flex-direction","column").set("overflow-x","hidden").set("box-sizing","border-box");
        dialog.add(body);

        Button saveButton = new Button(getTranslation("dialog.save"), VaadinIcon.CHECK.create(), e -> {
            if (!binder.validate().isOk()) return;
            accountService.adjustStartBalance(account, startBalanceField.getValue());
            accountService.saveAccount(account);
            updateList(); dialog.close();
            Notification.show(getTranslation("accounts.saved"), 2000, Notification.Position.BOTTOM_END)
                    .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_SUCCESS);
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private Div dialogSection(String label) {
        Div s = new Div(); s.setWidthFull();
        s.getStyle().set("display","flex").set("flex-direction","column").set("gap","var(--lumo-space-s)")
                .set("padding","var(--lumo-space-m) var(--lumo-space-l)").set("box-sizing","border-box");
        if (label != null && !label.isBlank()) {
            Span lbl = new Span(label.toUpperCase());
            lbl.getStyle().set("font-size","10px").set("font-weight","700").set("letter-spacing","0.08em").set("color","var(--lumo-secondary-text-color)");
            s.add(lbl);
        }
        return s;
    }

    private HorizontalLayout dialogRow(com.vaadin.flow.component.Component a, com.vaadin.flow.component.Component b) {
        HorizontalLayout row = new HorizontalLayout(a, b);
        row.setWidthFull(); row.setSpacing(false);
        row.getStyle().set("gap","var(--lumo-space-m)").set("flex-wrap","wrap");
        a.getElement().getStyle().set("flex","1 1 160px").set("min-width","0");
        b.getElement().getStyle().set("flex","1 1 160px").set("min-width","0");
        return row;
    }
    private void deleteAccount(Account account) {
        accountService.deleteAccount(account);
        updateList();
        Notification.show(getTranslation("accounts.deleted"));
    }
}
