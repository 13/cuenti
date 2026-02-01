package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.Currency;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.CurrencyService;
import com.cuenti.homebanking.service.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "currencies", layout = MainLayout.class)
@PageTitle("Manage Currencies | Cuenti")
@PermitAll
public class CurrencyManagementView extends VerticalLayout {

    private final CurrencyService currencyService;
    private final UserService userService;
    private final SecurityUtils securityUtils;
    private final User currentUser;

    private final Grid<Currency> grid = new Grid<>(Currency.class, false);

    public CurrencyManagementView(CurrencyService currencyService, UserService userService, SecurityUtils securityUtils) {
        this.currencyService = currencyService;
        this.userService = userService;
        this.securityUtils = securityUtils;

        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));
        this.currentUser = userService.findByUsername(username);

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("background-color", "var(--lumo-contrast-5pct)");
        getStyle().set("overflow", "hidden");

        setupUI();
        refreshGrid();
    }

    private void setupUI() {
        H2 title = new H2(getTranslation("currencies.title"));
        title.getStyle().set("margin-top", "0").set("color", "var(--lumo-primary-text-color)");

        Button addButton = new Button(getTranslation("currencies.add"), VaadinIcon.PLUS.create(), e -> openCurrencyDialog(new Currency()));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(addButton);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(JustifyContentMode.END);

        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(Currency::getCode).setHeader(getTranslation("currencies.code")).setSortable(true).setAutoWidth(true);
        grid.addColumn(Currency::getName).setHeader(getTranslation("currencies.name")).setSortable(true).setAutoWidth(true);
        grid.addColumn(Currency::getSymbol).setHeader(getTranslation("currencies.symbol")).setAutoWidth(true);
        grid.addColumn(Currency::getDecimalChar).setHeader("Decimal Char").setAutoWidth(true);
        grid.addColumn(Currency::getFracDigits).setHeader("Frac Digits").setAutoWidth(true);
        grid.addColumn(Currency::getGroupingChar).setHeader("Grouping Char").setAutoWidth(true);

        grid.addComponentColumn(currency -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> openCurrencyDialog(currency));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e -> {
                currencyService.deleteCurrency(currency);
                refreshGrid();
                Notification.show(getTranslation("currencies.deleted"));
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader(getTranslation("transactions.actions")).setFrozenToEnd(true).setAutoWidth(true);

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

    private void openCurrencyDialog(Currency currency) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(currency.getId() == null ? getTranslation("currencies.add") : getTranslation("currencies.edit"));

        FormLayout formLayout = new FormLayout();
        TextField code = new TextField(getTranslation("currencies.code"));
        TextField name = new TextField(getTranslation("currencies.name"));
        TextField symbol = new TextField(getTranslation("currencies.symbol"));
        TextField decimalChar = new TextField("Decimal Char");
        IntegerField fracDigits = new IntegerField("Frac Digits");
        TextField groupingChar = new TextField("Grouping Char");

        Binder<Currency> binder = new Binder<>(Currency.class);
        binder.forField(code).asRequired().bind(Currency::getCode, Currency::setCode);
        binder.forField(name).asRequired().bind(Currency::getName, Currency::setName);
        binder.forField(symbol).asRequired().bind(Currency::getSymbol, Currency::setSymbol);
        binder.bind(decimalChar, Currency::getDecimalChar, Currency::setDecimalChar);
        binder.bind(fracDigits, Currency::getFracDigits, Currency::setFracDigits);
        binder.bind(groupingChar, Currency::getGroupingChar, Currency::setGroupingChar);
        binder.setBean(currency);

        formLayout.add(code, name, symbol, decimalChar, fracDigits, groupingChar);
        formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("500px", 2));

        Button saveButton = new Button(getTranslation("dialog.save"), e -> {
            if (binder.validate().isOk()) {
                currencyService.saveCurrency(currency);
                refreshGrid();
                dialog.close();
                Notification.show(getTranslation("currencies.saved"));
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());

        dialog.getFooter().add(cancelButton, saveButton);
        dialog.add(formLayout);
        dialog.open();
    }

    private void refreshGrid() {
        grid.setItems(currencyService.getAllCurrencies());
    }
}
