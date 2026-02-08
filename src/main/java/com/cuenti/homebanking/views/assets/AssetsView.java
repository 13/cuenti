package com.cuenti.homebanking.views.assets;

import com.cuenti.homebanking.data.Asset;
import com.cuenti.homebanking.data.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.services.AssetService;
import com.cuenti.homebanking.services.ExchangeRateService;
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
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@PageTitle("Assets")
@Route("assets")
@Menu(order = 10, icon = LineAwesomeIconUrl.CHART_BAR)
@PermitAll
public class AssetsView extends VerticalLayout {

    private final AssetService assetService;
    private final UserService userService;
    private final ExchangeRateService exchangeRateService;
    private final SecurityUtils securityUtils;
    private final User currentUser;

    private final Grid<Asset> grid = new Grid<>(Asset.class, false);
    private final TextField searchField = new TextField();

    public AssetsView(AssetService assetService, UserService userService,
                      ExchangeRateService exchangeRateService, SecurityUtils securityUtils) {
        this.assetService = assetService;
        this.userService = userService;
        this.exchangeRateService = exchangeRateService;
        this.securityUtils = securityUtils;

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
        H2 title = new H2(getTranslation("assets.title"));
        title.getStyle().set("margin-top", "0").set("color", "var(--lumo-primary-text-color)");

        searchField.setPlaceholder(getTranslation("transactions.search"));
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.EAGER);
        searchField.addValueChangeListener(e -> refreshGrid());

        Button addButton = new Button(getTranslation("assets.add"), VaadinIcon.PLUS.create(), e -> openAssetDialog(new Asset()));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button updatePricesButton = new Button(VaadinIcon.REFRESH.create(), e -> {
            assetService.updateCurrentUserAssetPrices();
            refreshGrid();
            Notification.show("Prices updated");
        });
        updatePricesButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout toolbar = new HorizontalLayout(searchField, addButton, updatePricesButton);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.expand(searchField);

        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();

        grid.addColumn(Asset::getSymbol).setHeader(getTranslation("assets.symbol")).setSortable(true).setAutoWidth(true);
        grid.addColumn(Asset::getName).setHeader(getTranslation("assets.name")).setSortable(true).setAutoWidth(true);

        grid.addComponentColumn(asset -> {
            Span span = new Span(asset.getType().name());
            span.getElement().getThemeList().add("badge pill");
            switch (asset.getType()) {
                case STOCK -> span.getElement().getThemeList().add("contrast");
                case ETF -> span.getElement().getThemeList().add("success");
                case CRYPTO -> span.getStyle().set("background-color", "#f39c12").set("color", "white");
            }
            return span;
        }).setHeader(getTranslation("assets.type")).setSortable(true).setAutoWidth(true);

        grid.addComponentColumn(asset -> {
            if (asset.getCurrentPrice() == null) return new Span("-");
            BigDecimal displayPrice = exchangeRateService.convert(asset.getCurrentPrice(),
                    asset.getCurrency(), currentUser.getDefaultCurrency());
            Span price = new Span(formatCurrency(displayPrice, currentUser.getDefaultCurrency()));
            price.getStyle().set("font-weight", "bold");
            return price;
        }).setHeader("Price").setSortable(true).setAutoWidth(true);

        grid.addColumn(Asset::getCurrency).setHeader("Currency").setAutoWidth(true);

        grid.addColumn(asset -> asset.getLastUpdate() != null ?
                asset.getLastUpdate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) : "-")
                .setHeader("Last Update").setAutoWidth(true);

        grid.addComponentColumn(asset -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> openAssetDialog(asset));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e -> {
                try {
                    assetService.deleteAsset(asset);
                    refreshGrid();
                    Notification.show(getTranslation("assets.deleted"), 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                } catch (IllegalStateException ex) {
                    Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                } catch (Exception ex) {
                    Notification.show("Error deleting asset", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Actions").setFrozenToEnd(true).setAutoWidth(true);

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

    private void openAssetDialog(Asset asset) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(asset.getId() == null ? getTranslation("assets.add") : getTranslation("assets.edit"));

        FormLayout formLayout = new FormLayout();
        TextField symbol = new TextField(getTranslation("assets.symbol"));
        TextField name = new TextField(getTranslation("assets.name"));
        ComboBox<Asset.AssetType> type = new ComboBox<>(getTranslation("assets.type"));
        type.setItems(Asset.AssetType.values());
        TextField currency = new TextField("Currency");
        currency.setValue("EUR");

        Binder<Asset> binder = new Binder<>(Asset.class);
        binder.forField(symbol).asRequired().bind(Asset::getSymbol, Asset::setSymbol);
        binder.forField(name).asRequired().bind(Asset::getName, Asset::setName);
        binder.forField(type).asRequired().bind(Asset::getType, Asset::setType);
        binder.bind(currency, Asset::getCurrency, Asset::setCurrency);
        binder.setBean(asset);

        formLayout.add(symbol, name, type, currency);
        formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        Button saveButton = new Button(getTranslation("dialog.save"), e -> {
            if (binder.validate().isOk()) {
                assetService.saveAsset(asset);
                refreshGrid();
                dialog.close();
                Notification.show(getTranslation("assets.saved"));
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());

        dialog.add(formLayout);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void refreshGrid() {
        String search = searchField.getValue();
        if (search == null || search.isEmpty()) {
            grid.setItems(assetService.getAllAssets());
        } else {
            grid.setItems(assetService.searchAssets(search));
        }
    }

    private String formatCurrency(BigDecimal amount, String currencyCode) {
        if (amount == null) return "";
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag(currentUser.getLocale()));
        try {
            java.util.Currency currency = java.util.Currency.getInstance(currencyCode);
            formatter.setCurrency(currency);
        } catch (Exception e) {
            // Use default
        }
        return formatter.format(amount);
    }
}
