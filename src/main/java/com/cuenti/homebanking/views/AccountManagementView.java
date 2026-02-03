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
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.dnd.GridDropLocation;
import com.vaadin.flow.component.grid.dnd.GridDropMode;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Route(value = "manage-accounts", layout = MainLayout.class)
@PageTitle("Manage Accounts | Cuenti Homebanking")
@PermitAll
public class AccountManagementView extends VerticalLayout {

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
        setPadding(true);
        setSpacing(true);
        getStyle().set("background-color", "var(--lumo-contrast-5pct)");

        setupUI();
        updateList();
    }

    private void setupUI() {
        H2 title = new H2(getTranslation("accounts.title"));
        title.getStyle().set("margin-top", "0").set("color", "var(--lumo-primary-text-color)");

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
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(Account::getAccountName).setHeader(getTranslation("accounts.name")).setSortable(true).setAutoWidth(true);
        grid.addColumn(Account::getAccountNumber).setHeader(getTranslation("accounts.number")).setSortable(true).setAutoWidth(true);
        grid.addColumn(acc -> getTranslation("account.type." + acc.getAccountType().name().toLowerCase())).setHeader(getTranslation("accounts.type")).setSortable(true).setAutoWidth(true);
        grid.addColumn(Account::getAccountGroup).setHeader(getTranslation("accounts.group")).setSortable(true).setAutoWidth(true);
        grid.addColumn(Account::getInstitution).setHeader(getTranslation("accounts.institution")).setSortable(true).setAutoWidth(true);
        grid.addColumn(Account::getCurrency).setHeader(getTranslation("accounts.currency")).setAutoWidth(true);
        grid.addColumn(Account::getBalance).setHeader(getTranslation("accounts.balance")).setSortable(true).setAutoWidth(true);

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
                Notification.show("Sorting is disabled while searching");
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

        dialog.setWidth("480px");
        dialog.setMaxWidth("95vw");

        dialog.setHeaderTitle(
                account.getId() == null
                        ? getTranslation("accounts.add")
                        : getTranslation("accounts.edit")
        );

        FormLayout formLayout = new FormLayout();

        TextField nameField = new TextField(getTranslation("accounts.name"));
        nameField.setPrefixComponent(VaadinIcon.USER.create());
        nameField.setRequired(true);

        ComboBox<Account.AccountType> typeCombo = new ComboBox<>(getTranslation("accounts.type"));
        typeCombo.setPrefixComponent(VaadinIcon.LIST.create());
        typeCombo.setItems(Account.AccountType.values());
        typeCombo.setItemLabelGenerator(
                type -> getTranslation("account.type." + type.name().toLowerCase())
        );

        ComboBox<String> groupCombo = new ComboBox<>(getTranslation("accounts.group"));
        groupCombo.setPrefixComponent(VaadinIcon.FOLDER.create());
        List<String> existingGroups = accounts.stream()
                .map(Account::getAccountGroup)
                .filter(g -> g != null && !g.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        groupCombo.setItems(existingGroups);
        groupCombo.setAllowCustomValue(true);
        groupCombo.addCustomValueSetListener(e -> groupCombo.setValue(e.getDetail()));

        TextField institutionField = new TextField(getTranslation("accounts.institution"));
        institutionField.setPrefixComponent(VaadinIcon.BUILDING.create());

        TextField numberField = new TextField(getTranslation("accounts.number"));
        numberField.setPrefixComponent(VaadinIcon.BARCODE.create());

        BigDecimalField startBalanceField =
                new BigDecimalField(getTranslation("accounts.start_balance"));
        startBalanceField.setPrefixComponent(VaadinIcon.EURO.create());
        startBalanceField.setValue(BigDecimal.ZERO);

        ComboBox<Currency> currencyCombo = new ComboBox<>(getTranslation("accounts.currency"));
        currencyCombo.setPrefixComponent(VaadinIcon.MONEY.create());
        currencyCombo.setItems(currencyService.getAllCurrencies());
        currencyCombo.setItemLabelGenerator(
                c -> c.getCode() + " (" + c.getSymbol() + ")"
        );

        // Update startBalanceField format when currency changes
        currencyCombo.addValueChangeListener(e -> {
            Currency selectedCurrency = e.getValue();
            if (selectedCurrency != null) {
                int fracDigits = selectedCurrency.getFracDigits();

                // Create pattern based on fractional digits
                String pattern = "#,##0";
                if (fracDigits > 0) {
                    pattern += "." + "0".repeat(fracDigits);
                }

                DecimalFormat decimalFormat = new DecimalFormat(pattern);
                decimalFormat.setDecimalSeparatorAlwaysShown(fracDigits > 0);

                // Update the current value to match the new precision
                BigDecimal currentValue = startBalanceField.getValue();
                if (currentValue != null) {
                    startBalanceField.setValue(
                        currentValue.setScale(fracDigits, java.math.RoundingMode.HALF_UP)
                    );
                }
            }
        });

        Checkbox excludeFromSummaryCheckbox =
                new Checkbox(getTranslation("accounts.exclude_from_summary"));

        Checkbox excludeFromReportsCheckbox =
                new Checkbox(getTranslation("accounts.exclude_from_reports"));

        formLayout.add(
                nameField,
                typeCombo,
                groupCombo,
                institutionField,
                numberField,
                startBalanceField,
                currencyCombo,
                excludeFromSummaryCheckbox,
                excludeFromReportsCheckbox
        );
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1)
        );

        Binder<Account> binder = new Binder<>(Account.class);

        binder.forField(nameField)
                .asRequired(getTranslation("accounts.name_required"))
                .bind(Account::getAccountName, Account::setAccountName);

        binder.bind(typeCombo, Account::getAccountType, Account::setAccountType);
        binder.bind(groupCombo, Account::getAccountGroup, Account::setAccountGroup);
        binder.bind(institutionField, Account::getInstitution, Account::setInstitution);
        binder.bind(numberField, Account::getAccountNumber, Account::setAccountNumber);
        binder.bind(startBalanceField, Account::getStartBalance, Account::setStartBalance);

        binder.bind(
                currencyCombo,
                acc -> currencyService.getAllCurrencies().stream()
                        .filter(c -> c.getCode().equals(acc.getCurrency()))
                        .findFirst()
                        .orElse(null),
                (acc, currency) ->
                        acc.setCurrency(currency != null ? currency.getCode() : "EUR")
        );

        binder.bind(excludeFromSummaryCheckbox,
                Account::isExcludeFromSummary,
                Account::setExcludeFromSummary);

        binder.bind(excludeFromReportsCheckbox,
                Account::isExcludeFromReports,
                Account::setExcludeFromReports);

        account.setUser(currentUser);
        binder.setBean(account);

        // Set initial format for startBalanceField based on account's currency
        if (account.getCurrency() != null) {
            currencyService.getAllCurrencies().stream()
                    .filter(c -> c.getCode().equals(account.getCurrency()))
                    .findFirst()
                    .ifPresent(currency -> {
                        int fracDigits = currency.getFracDigits();

                        if (account.getStartBalance() != null) {
                            startBalanceField.setValue(
                                account.getStartBalance().setScale(fracDigits, java.math.RoundingMode.HALF_UP)
                            );
                        }
                    });
        }

        Button saveButton = new Button(getTranslation("dialog.save"), e -> {
            if (binder.validate().isOk()) {
                accountService.saveAccount(account);
                updateList();
                dialog.close();
                Notification.show(getTranslation("accounts.saved"));
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton =
                new Button(getTranslation("dialog.cancel"), e -> dialog.close());

        dialog.add(formLayout);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void deleteAccount(Account account) {
        accountService.deleteAccount(account);
        updateList();
        Notification.show(getTranslation("accounts.deleted"));
    }
}
