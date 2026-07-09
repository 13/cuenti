package com.cuenti.app.views;

import com.cuenti.app.model.Asset;
import com.cuenti.app.model.User;
import com.cuenti.app.security.SecurityUtils;
import com.cuenti.app.service.AssetService;
import com.cuenti.app.service.ExchangeRateService;
import com.cuenti.app.service.UserService;
import com.vaadin.flow.component.button.Button;
import com.cuenti.app.views.components.DeleteConfirm;
import com.cuenti.app.views.components.UiNotifier;

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

        addClassNames("page-scroll", "page-shell");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle()
                .set("padding", "var(--vaadin-gap-m)")
                .set("overflow", "hidden");

        setupUI();
        refreshGrid();
    }

    private void setupUI() {
        Span title = new Span(getTranslation("assets.title"));
        title.addComponentAsFirst(VaadinIcon.CHART_3D.create());
        title.addClassName("page-title");
        
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
            com.cuenti.app.views.components.UiNotifier.success(getTranslation("assets.prices_updated"));
        });
        updatePricesButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout toolbar = new HorizontalLayout(updatePricesButton, addButton);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setSpacing(false);
        toolbar.addClassName("card-toolbar");
        toolbar.getStyle().set("gap", "var(--vaadin-gap-s)");

        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        com.vaadin.flow.component.button.Button emptyAdd =
                new com.vaadin.flow.component.button.Button(getTranslation("empty.hint"), e -> openAssetDialog(new Asset()));
        emptyAdd.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_TERTIARY);
        grid.setEmptyStateComponent(new com.cuenti.app.views.components.EmptyStateNotice(
                VaadinIcon.CHART_3D, getTranslation("empty.title"), null, emptyAdd));
        grid.addItemDoubleClickListener(e -> openAssetDialog(e.getItem()));

        // Demo-style per-column filter: search lives in the grid header
        searchField.setWidthFull();
        grid.addAttachListener(e -> {
            if (grid.getHeaderRows().size() < 2 && !grid.getColumns().isEmpty()) {
                grid.appendHeaderRow().getCell(grid.getColumns().get(0)).setComponent(searchField);
            }
        });
        grid.setSizeFull();
        
        grid.addColumn(Asset::getSymbol).setHeader(getTranslation("assets.symbol")).setSortable(true).setAutoWidth(true);
        grid.addColumn(Asset::getName).setHeader(getTranslation("assets.name")).setSortable(true).setAutoWidth(true);
        
        grid.addComponentColumn(asset -> {
            String color = switch (asset.getType()) {
                case STOCK  -> "var(--aura-accent-color)";
                case ETF    -> "var(--aura-green)";
                case CRYPTO -> "#f39c12";
                default     -> "var(--vaadin-text-color-secondary)";
            };
            Span span = new Span(asset.getType().name());
            span.getStyle().set("font-size","10px").set("font-weight","700").set("letter-spacing","0.05em")
                    .set("padding","2px 8px").set("border-radius","99px")
                    .set("background", color + "1a").set("color", color);
            return span;
        }).setHeader(getTranslation("assets.type")).setSortable(true).setAutoWidth(true);

        grid.addComponentColumn(asset -> {
            if (asset.getCurrentPrice() == null) { Span s = new Span("—"); s.getStyle().set("color","var(--vaadin-text-color-disabled)"); return s; }
            BigDecimal displayPrice = exchangeRateService.convert(asset.getCurrentPrice(),
                    asset.getCurrency(), currentUser.getDefaultCurrency());
            Span price = new Span(formatCurrency(displayPrice, currentUser.getDefaultCurrency()));
            price.getStyle().set("font-weight","700").set("font-size","var(--aura-font-size-s)").set("color","var(--aura-accent-color)");
            return price;
        }).setHeader(getTranslation("assets.price") + " (" + currentUser.getDefaultCurrency() + ")")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END).setSortable(true).setAutoWidth(true);

        grid.addColumn(asset -> asset.getLastUpdate() != null ? 
                asset.getLastUpdate().format(getDateTimeFormatter()) : "-")
                .setHeader(getTranslation("transactions.date")).setAutoWidth(true);

        grid.addComponentColumn(asset -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> openAssetDialog(asset));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            editBtn.setTooltipText(getTranslation("transactions.edit"));
            editBtn.getElement().setAttribute("aria-label", getTranslation("transactions.edit"));
            
            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e ->
                DeleteConfirm.show(
                    getTranslation("dialog.confirm_delete"),
                    getTranslation("dialog.confirm_delete_message") + " \"" + asset.getName() + "\"?",
                    getTranslation("dialog.delete"),
                    getTranslation("dialog.cancel"),
                    getTranslation("error.delete_failed"),
                    () -> {
                        try {
                            assetService.deleteAsset(asset);
                            refreshGrid();
                            UiNotifier.success(getTranslation("assets.deleted"));
                        } catch (IllegalStateException ex) {
                            UiNotifier.error(ex.getMessage());
                        }
                    }));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            deleteBtn.setTooltipText(getTranslation("transactions.delete"));
            deleteBtn.getElement().setAttribute("aria-label", getTranslation("transactions.delete"));
            
            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader(getTranslation("transactions.actions")).setFrozenToEnd(true).setAutoWidth(true);

        // Always use card layout
        Div card = new Div();
        card.setSizeFull();
        card.addClassName("card");
        card.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--vaadin-gap-s)")
                .set("box-sizing", "border-box");
        card.add(toolbar, grid);
        add(title, card);
        expand(card);
    }

    private void openAssetDialog(Asset asset) {
        Dialog dialog = new Dialog();
        dialog.setCloseOnOutsideClick(false);
        dialog.setWidth("min(440px, 96vw)");
        dialog.setResizable(false);
        dialog.getElement().getStyle()
                .set("overflow-x", "hidden");
        dialog.setHeaderTitle(asset.getId() == null ? getTranslation("assets.add") : getTranslation("assets.edit"));
        com.vaadin.flow.component.icon.Icon headerIcon = VaadinIcon.CHART_3D.create();
        headerIcon.addClassName("dialog-header-icon");
        dialog.getHeader().add(headerIcon);

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
        symRow.getStyle().set("gap","var(--vaadin-gap-m)").set("flex-wrap","wrap");
        symbol.getElement().getStyle().set("flex","1 1 100px").set("min-width","0");
        type.getElement().getStyle().set("flex","2 1 160px").set("min-width","0");

        Div body = new Div();
        body.setWidthFull();
        body.getStyle().set("display","flex").set("flex-direction","column").set("gap","var(--vaadin-gap-s)")
                .set("padding","var(--vaadin-gap-m) var(--vaadin-gap-l)").set("box-sizing","border-box");
        body.add(symRow, name);
        dialog.add(body);

        Button saveButton = new Button(getTranslation("dialog.save"), e -> {
            if (binder.validate().isOk()) {
                assetService.saveAsset(asset); refreshGrid(); dialog.close();
                com.cuenti.app.views.components.UiNotifier.success(getTranslation("assets.saved"));
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
        symbol.focus();
    }

    private void refreshGrid() {
        grid.setItems(assetService.searchAssets(searchField.getValue()));
    }

    private DateTimeFormatter getDateTimeFormatter() {
        String pattern = currentUser.getLocale() != null && currentUser.getLocale().startsWith("de")
                ? "dd.MM.yyyy HH:mm" : "MM/dd/yyyy HH:mm";
        return DateTimeFormatter.ofPattern(pattern);
    }

    @Override
    public Locale getLocale() {
        return Locale.forLanguageTag(currentUser.getLocale());
    }

     private String formatCurrency(BigDecimal amount, String currencyCode) {
         return com.cuenti.app.util.CurrencyFormat.format(amount, currencyCode, getLocale());
     }

     private String getAssetTypeLabel(Asset.AssetType type) {
         return switch (type) {
             case STOCK -> getTranslation("asset.type.stock");
             case ETF -> getTranslation("asset.type.etf");
             case CRYPTO -> getTranslation("asset.type.crypto");
         };
     }
 }
