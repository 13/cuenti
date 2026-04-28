package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.Asset;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.AssetService;
import com.cuenti.homebanking.service.ExchangeRateService;
import com.cuenti.homebanking.service.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
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
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Route(value = "assets", layout = MainLayout.class)
@PermitAll
public class AssetManagementView extends VerticalLayout implements HasDynamicTitle {

    @Override
    public String getPageTitle() {
        return getTranslation("assets.title") + " | " + getTranslation("app.name");
    }


    private final AssetService assetService;
    private final UserService userService;
    private final ExchangeRateService exchangeRateService;
    private final SecurityUtils securityUtils;
    private final User currentUser;

    private final Grid<Asset> grid = new Grid<>(Asset.class, false);
    private final TextField searchField = new TextField();

    public AssetManagementView(AssetService assetService, UserService userService, 
                               ExchangeRateService exchangeRateService, SecurityUtils securityUtils) {
        this.assetService = assetService;
        this.userService = userService;
        this.exchangeRateService = exchangeRateService;
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
        Span title = new Span(getTranslation("assets.title"));
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-xxl)")
                .set("font-weight", "700")
                .set("color", "var(--lumo-header-text-color)");
        
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
            Notification.show(getTranslation("settings.saved"));
        });
        updatePricesButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout toolbar = new HorizontalLayout(searchField, addButton, updatePricesButton);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.expand(searchField);
        toolbar.setSpacing(false);
        toolbar.getStyle()
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "12px")
                .set("gap", "var(--lumo-space-s)");

        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        grid.setSizeFull();
        
        grid.addColumn(Asset::getSymbol).setHeader(getTranslation("assets.symbol")).setSortable(true).setAutoWidth(true);
        grid.addColumn(Asset::getName).setHeader(getTranslation("assets.name")).setSortable(true).setAutoWidth(true);
        
        grid.addComponentColumn(asset -> {
            String color = switch (asset.getType()) {
                case STOCK  -> "var(--lumo-primary-color)";
                case ETF    -> "var(--lumo-success-color)";
                case CRYPTO -> "#f39c12";
                default     -> "var(--lumo-secondary-text-color)";
            };
            Span span = new Span(asset.getType().name());
            span.getStyle().set("font-size","10px").set("font-weight","700").set("letter-spacing","0.05em")
                    .set("padding","2px 8px").set("border-radius","99px")
                    .set("background", color + "1a").set("color", color);
            return span;
        }).setHeader(getTranslation("assets.type")).setSortable(true).setAutoWidth(true);

        grid.addComponentColumn(asset -> {
            if (asset.getCurrentPrice() == null) { Span s = new Span("—"); s.getStyle().set("color","var(--lumo-disabled-text-color)"); return s; }
            BigDecimal displayPrice = exchangeRateService.convert(asset.getCurrentPrice(),
                    asset.getCurrency(), currentUser.getDefaultCurrency());
            Span price = new Span(formatCurrency(displayPrice, currentUser.getDefaultCurrency()));
            price.getStyle().set("font-weight","700").set("font-size","var(--lumo-font-size-s)").set("color","var(--lumo-primary-color)");
            return price;
        }).setHeader(getTranslation("assets.price") + " (" + currentUser.getDefaultCurrency() + ")").setSortable(true).setAutoWidth(true);

        grid.addColumn(asset -> asset.getLastUpdate() != null ? 
                asset.getLastUpdate().format(getDateTimeFormatter()) : "-")
                .setHeader(getTranslation("transactions.date")).setAutoWidth(true);

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
                    Notification.show(getTranslation("error.generic"), 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
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

    private void openAssetDialog(Asset asset) {
        Dialog dialog = new Dialog();
        dialog.setWidth("min(440px, 96vw)");
        dialog.setResizable(false);
        dialog.getElement().getStyle()
                .set("--lumo-border-radius-l", "20px")
                .set("overflow-x", "hidden");
        dialog.setHeaderTitle(asset.getId() == null ? getTranslation("assets.add") : getTranslation("assets.edit"));

        TextField symbol = new TextField(getTranslation("assets.symbol"));
        symbol.setPrefixComponent(VaadinIcon.STOCK.create()); symbol.setWidthFull();
        TextField name = new TextField(getTranslation("assets.name"));
        name.setWidthFull();
        ComboBox<Asset.AssetType> type = new ComboBox<>(getTranslation("assets.type"));
        type.setItems(Asset.AssetType.values()); type.setWidthFull();

        Binder<Asset> binder = new Binder<>(Asset.class);
        binder.forField(symbol).asRequired().bind(Asset::getSymbol, Asset::setSymbol);
        binder.forField(name).asRequired().bind(Asset::getName, Asset::setName);
        binder.forField(type).asRequired().bind(Asset::getType, Asset::setType);
        binder.setBean(asset);

        HorizontalLayout symRow = new HorizontalLayout(symbol, type);
        symRow.setWidthFull(); symRow.setSpacing(false);
        symRow.getStyle().set("gap","var(--lumo-space-m)").set("flex-wrap","wrap");
        symbol.getElement().getStyle().set("flex","1 1 100px").set("min-width","0");
        type.getElement().getStyle().set("flex","2 1 160px").set("min-width","0");

        Div body = new Div();
        body.setWidthFull();
        body.getStyle().set("display","flex").set("flex-direction","column").set("gap","var(--lumo-space-s)")
                .set("padding","var(--lumo-space-m) var(--lumo-space-l)").set("box-sizing","border-box");
        body.add(symRow, name);
        dialog.add(body);

        Button saveButton = new Button(getTranslation("dialog.save"), VaadinIcon.CHECK.create(), e -> {
            if (binder.validate().isOk()) {
                assetService.saveAsset(asset); refreshGrid(); dialog.close();
                Notification.show(getTranslation("assets.saved"), 2000, Notification.Position.BOTTOM_END)
                    .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_SUCCESS);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void refreshGrid() {
        grid.setItems(assetService.searchAssets(searchField.getValue()));
    }

    private DateTimeFormatter getDateTimeFormatter() {
        String pattern = currentUser.getLocale().equals("de-DE") ? "dd.MM.yyyy HH:mm" : "MM/dd/yyyy HH:mm";
        return DateTimeFormatter.ofPattern(pattern);
    }

    @Override
    public Locale getLocale() {
        return Locale.forLanguageTag(currentUser.getLocale());
    }

     private String formatCurrency(BigDecimal amount, String currencyCode) {
         if (amount == null) return "";
         NumberFormat formatter = NumberFormat.getCurrencyInstance(getLocale());
         try {
             java.util.Currency currency = java.util.Currency.getInstance(currencyCode);
             formatter.setCurrency(currency);
         } catch (Exception e) {}
         return formatter.format(amount);
     }

     private String getAssetTypeLabel(Asset.AssetType type) {
         return switch (type) {
             case STOCK -> getTranslation("asset.type.stock");
             case ETF -> getTranslation("asset.type.etf");
             case CRYPTO -> getTranslation("asset.type.crypto");
         };
     }
 }
