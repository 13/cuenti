package com.cuenti.homebanking.views.accounts;

import com.cuenti.homebanking.data.Account;
import com.cuenti.homebanking.data.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.services.AccountService;
import com.cuenti.homebanking.services.CurrencyService;
import com.cuenti.homebanking.services.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@PageTitle("Manage Accounts")
@Route("manage-accounts")
@Menu(order = 5, icon = LineAwesomeIconUrl.UNIVERSITY_SOLID)
@PermitAll
public class AccountManagementView extends VerticalLayout {

    private final AccountService accountService;
    private final UserService userService;
    private final SecurityUtils securityUtils;
    private final User currentUser;

    private final Grid<Account> grid = new Grid<>(Account.class, false);
    private final TextField searchField = new TextField();
    private List<Account> accounts;

    public AccountManagementView(AccountService accountService, UserService userService,
                                 CurrencyService currencyService, SecurityUtils securityUtils) {
        this.accountService = accountService;
        this.userService = userService;
        this.securityUtils = securityUtils;

        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));
        this.currentUser = userService.findByUsername(username);

        setSpacing(true);
        setPadding(true);
        setMaxWidth("1200px");
        getStyle().set("margin", "0 auto");

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

        Button addButton = new Button(getTranslation("accounts.add"), VaadinIcon.PLUS.create(), e -> openAccountDialog(new Account()));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(searchField, addButton);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.expand(searchField);

        configureGrid();

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
        grid.addColumn(Account::getAccountNumber).setHeader("Number").setSortable(true).setAutoWidth(true);
        grid.addColumn(acc -> acc.getAccountType().name()).setHeader(getTranslation("accounts.type")).setSortable(true).setAutoWidth(true);
        grid.addColumn(Account::getAccountGroup).setHeader("Group").setSortable(true).setAutoWidth(true);
        grid.addColumn(Account::getInstitution).setHeader("Institution").setSortable(true).setAutoWidth(true);
        grid.addColumn(Account::getCurrency).setHeader("Currency").setAutoWidth(true);
        grid.addColumn(Account::getBalance).setHeader(getTranslation("accounts.balance")).setSortable(true).setAutoWidth(true);

        grid.addComponentColumn(account -> {
            Button editButton = new Button(VaadinIcon.EDIT.create(), e -> openAccountDialog(account));
            editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            Button deleteButton = new Button(VaadinIcon.TRASH.create(), e -> deleteAccount(account));
            deleteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

            return new HorizontalLayout(editButton, deleteButton);
        }).setHeader("Actions").setFrozenToEnd(true).setAutoWidth(true);

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
        dialog.setHeaderTitle(account.getId() == null ? getTranslation("accounts.add") : getTranslation("accounts.edit"));

        FormLayout formLayout = new FormLayout();

        TextField nameField = new TextField(getTranslation("accounts.name"));
        TextField numberField = new TextField("Account Number");
        ComboBox<Account.AccountType> typeCombo = new ComboBox<>(getTranslation("accounts.type"));
        typeCombo.setItems(Account.AccountType.values());
        TextField groupField = new TextField("Group");
        TextField institutionField = new TextField("Institution");
        TextField currencyField = new TextField("Currency");
        currencyField.setValue("EUR");
        BigDecimalField startBalanceField = new BigDecimalField("Start Balance");
        startBalanceField.setValue(BigDecimal.ZERO);

        Binder<Account> binder = new Binder<>(Account.class);
        binder.forField(nameField).asRequired("Name is required").bind(Account::getAccountName, Account::setAccountName);
        binder.bind(numberField, Account::getAccountNumber, Account::setAccountNumber);
        binder.forField(typeCombo).asRequired().bind(Account::getAccountType, Account::setAccountType);
        binder.bind(groupField, Account::getAccountGroup, Account::setAccountGroup);
        binder.bind(institutionField, Account::getInstitution, Account::setInstitution);
        binder.bind(currencyField, Account::getCurrency, Account::setCurrency);
        binder.bind(startBalanceField, Account::getStartBalance, Account::setStartBalance);
        binder.setBean(account);

        formLayout.add(nameField, numberField, typeCombo, groupField, institutionField, currencyField, startBalanceField);
        formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        Button saveButton = new Button(getTranslation("dialog.save"), e -> {
            if (binder.validate().isOk()) {
                accountService.saveAccount(account);
                updateList();
                dialog.close();
                Notification.show("Account saved");
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());

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
