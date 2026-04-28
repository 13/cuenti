package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.Currency;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.CurrencyService;
import com.cuenti.homebanking.service.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "currencies", layout = MainLayout.class)
@PermitAll
public class CurrencyManagementView extends VerticalLayout implements HasDynamicTitle {

    @Override
    public String getPageTitle() {
        return getTranslation("currencies.title") + " | " + getTranslation("app.name");
    }


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
        Span title = new Span(getTranslation("currencies.title"));
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-xxl)")
                .set("font-weight", "700")
                .set("color", "var(--lumo-header-text-color)");

        Button addButton = new Button(getTranslation("currencies.add"), VaadinIcon.PLUS.create(), e -> openCurrencyDialog(new Currency()));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(addButton);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(JustifyContentMode.END);

        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
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

    private void openCurrencyDialog(Currency currency) {
        Dialog dialog = new Dialog();
        dialog.setWidth("min(500px, 96vw)");
        dialog.setResizable(false);
        dialog.getElement().getStyle()
                .set("--lumo-border-radius-l", "20px")
                .set("overflow-x", "hidden");
        dialog.setHeaderTitle(currency.getId() == null ? getTranslation("currencies.add") : getTranslation("currencies.edit"));

         TextField code  = new TextField(getTranslation("currencies.code"));  code.setWidthFull();
         TextField name  = new TextField(getTranslation("currencies.name"));  name.setWidthFull();
         TextField symbol = new TextField(getTranslation("currencies.symbol")); symbol.setWidthFull();
         TextField decimalChar  = new TextField(getTranslation("currencies.decimal_char"));  decimalChar.setWidthFull();
         IntegerField fracDigits = new IntegerField(getTranslation("currencies.frac_digits")); fracDigits.setWidthFull();
         TextField groupingChar = new TextField(getTranslation("currencies.grouping_char")); groupingChar.setWidthFull();

        Binder<Currency> binder = new Binder<>(Currency.class);
        binder.forField(code).asRequired().bind(Currency::getCode, Currency::setCode);
        binder.forField(name).asRequired().bind(Currency::getName, Currency::setName);
        binder.forField(symbol).asRequired().bind(Currency::getSymbol, Currency::setSymbol);
        binder.bind(decimalChar, Currency::getDecimalChar, Currency::setDecimalChar);
        binder.bind(fracDigits, Currency::getFracDigits, Currency::setFracDigits);
        binder.bind(groupingChar, Currency::getGroupingChar, Currency::setGroupingChar);
        binder.setBean(currency);

        Div row1 = rowDiv(code, symbol, fracDigits);
        row1.getStyle().set("gap","var(--lumo-space-m)");
        code.getElement().getStyle().set("flex","2 1 120px").set("min-width","0");
        symbol.getElement().getStyle().set("flex","1 1 70px").set("min-width","0");
        fracDigits.getElement().getStyle().set("flex","1 1 80px").set("min-width","0");

        Div row2 = rowDiv(decimalChar, groupingChar);
        row2.getStyle().set("gap","var(--lumo-space-m)");
        decimalChar.getElement().getStyle().set("flex","1 1 120px").set("min-width","0");
        groupingChar.getElement().getStyle().set("flex","1 1 120px").set("min-width","0");

        Div body = new Div();
        body.setWidthFull();
        body.getStyle().set("display","flex").set("flex-direction","column").set("gap","var(--lumo-space-s)")
                .set("padding","var(--lumo-space-m) var(--lumo-space-l)").set("box-sizing","border-box");
        body.add(name, row1, row2);
        dialog.add(body);

        Button saveButton = new Button(getTranslation("dialog.save"), VaadinIcon.CHECK.create(), e -> {
            if (binder.validate().isOk()) {
                currencyService.saveCurrency(currency); refreshGrid(); dialog.close();
                Notification.show(getTranslation("currencies.saved"), 2000, Notification.Position.BOTTOM_END)
                    .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_SUCCESS);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private Div rowDiv(com.vaadin.flow.component.Component... children) {
        Div row = new Div(children);
        row.setWidthFull();
        row.getStyle().set("display","flex").set("flex-wrap","wrap").set("align-items","baseline");
        return row;
    }

    private void refreshGrid() {
        grid.setItems(currencyService.getAllCurrencies());
    }
}
