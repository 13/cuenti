package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.UserService;
import com.cuenti.homebanking.service.AssetService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.theme.lumo.Lumo;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

@PermitAll
@Slf4j
public class MainLayout extends AppLayout {

    private final SecurityUtils securityUtils;
    private final UserService userService;
    private final AssetService assetService;
    private User currentUser;

    public MainLayout(SecurityUtils securityUtils, UserService userService, AssetService assetService) {
        this.securityUtils = securityUtils;
        this.userService = userService;
        this.assetService = assetService;

        String username = securityUtils.getAuthenticatedUsername().orElse(null);
        if (username != null) {
            try {
                this.currentUser = userService.findByUsername(username);
                Locale locale = Locale.forLanguageTag(currentUser.getLocale());
                UI.getCurrent().setLocale(locale);
                VaadinSession.getCurrent().setLocale(locale);

                // Update asset prices for the logged-in user asynchronously
                updateAssetPricesAsync();
            } catch (Exception e) {
                securityUtils.logout();
                return;
            }
        }

        applyTheme();
        setPrimarySection(Section.DRAWER);

        createHeader();
        createDrawer();
    }

    /**
     * Update asset prices asynchronously to avoid blocking the UI on login.
     */
    private void updateAssetPricesAsync() {
        if (currentUser != null) {
            // Run in a separate thread to avoid blocking the UI
            new Thread(() -> {
                try {
                    assetService.updateUserAssetPrices(currentUser);
                } catch (Exception e) {
                    log.error("Error updating asset prices for user: {}", currentUser.getUsername(), e);
                }
            }).start();
        }
    }

    private void applyTheme() {
        boolean isDark = currentUser == null || currentUser.isDarkMode();
        String theme = isDark ? Lumo.DARK : Lumo.LIGHT;
        
        UI.getCurrent().getElement().executeJs(
            "document.documentElement.setAttribute('theme', $0)", theme
        );
    }

    private void toggleTheme() {
        if (currentUser != null) {
            boolean newDarkMode = !currentUser.isDarkMode();
            currentUser.setDarkMode(newDarkMode);
            userService.updateDarkMode(currentUser, newDarkMode);
            applyTheme();
        }
    }

    private void createHeader() {
        Image logo = new Image("images/CuentiText.png", "Cuenti");
        logo.setHeight("32px");
        logo.setWidth("auto");
        logo.getStyle().set("flex-shrink", "0");

        Button themeToggle = new Button(new Icon(VaadinIcon.ADJUST));
        themeToggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        themeToggle.getElement().setProperty("title", "Toggle Dark/Light Mode");
        themeToggle.addClickListener(e -> toggleTheme());

        String username = currentUser != null ? currentUser.getUsername() : "Guest";
        Span userInfo = new Span(getTranslation("welcome.user", username));
        userInfo.getStyle().set("font-size", "var(--lumo-font-size-s)");

        Button logoutButton = new Button(new Icon(VaadinIcon.SIGN_OUT));
        logoutButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        logoutButton.getElement().setProperty("title", getTranslation("nav.logout"));
        logoutButton.addClickListener(e -> securityUtils.logout());

        HorizontalLayout left = new HorizontalLayout(new DrawerToggle(), logo);
        left.setAlignItems(FlexComponent.Alignment.CENTER);
        left.setSpacing(true);

        HorizontalLayout right = new HorizontalLayout(themeToggle, userInfo, logoutButton);
        right.setAlignItems(FlexComponent.Alignment.CENTER);
        right.setSpacing(true);

        HorizontalLayout header = new HorizontalLayout(left, right);
        header.setWidthFull();
        header.setPadding(true);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        addToNavbar(header);
    }

    private void createDrawer() {
        Tabs tabs = new Tabs();
        tabs.setOrientation(Tabs.Orientation.VERTICAL);

        tabs.add(
                createSectionTitle(getTranslation("nav.general")),
                createTab(VaadinIcon.DASHBOARD, getTranslation("nav.dashboard"), DashboardView.class),
                createTab(VaadinIcon.LIST, getTranslation("nav.transactions"), TransactionHistoryView.class),
                createTab(VaadinIcon.CALENDAR_CLOCK, "Scheduled", ScheduledTransactionsView.class),
                createTab(VaadinIcon.CHART, getTranslation("nav.statistics"), StatisticsView.class),
                createTab(VaadinIcon.CAR, getTranslation("nav.vehicles"), VehiclesView.class),

                createSectionTitle(getTranslation("nav.management")),
                createTab(VaadinIcon.WALLET, getTranslation("nav.manage_accounts"), AccountManagementView.class),
                createTab(VaadinIcon.USERS, getTranslation("nav.payees"), PayeeManagementView.class),
                createTab(VaadinIcon.SITEMAP, getTranslation("nav.categories"), CategoryManagementView.class),
                createTab(VaadinIcon.TAGS, getTranslation("nav.tags"), TagManagementView.class),
                createTab(VaadinIcon.MONEY, getTranslation("nav.currencies"), CurrencyManagementView.class),
                createTab(VaadinIcon.CHART_3D, getTranslation("nav.assets"), AssetManagementView.class),

                createSectionTitle(getTranslation("nav.settings"))
        );

        if (currentUser != null && currentUser.getRoles().contains("ROLE_ADMIN")) {
            tabs.add(createTab(VaadinIcon.KEY, getTranslation("settings.administration"), SettingsView.class, Map.of("section", "admin")));
        }

        tabs.add(
                createTab(VaadinIcon.USER, getTranslation("settings.user_title"), SettingsView.class, Map.of("section", "user")),
                createTab(VaadinIcon.EXCHANGE, getTranslation("settings.import_export_title"), SettingsView.class, Map.of("section", "import-export")),

                createSectionTitle(getTranslation("nav.info", "Information")),
                createTab(VaadinIcon.QUESTION_CIRCLE, getTranslation("nav.help", "Help"), HelpView.class),
                createTab(VaadinIcon.INFO_CIRCLE, getTranslation("nav.about", "About"), AboutView.class)
        );

        addToDrawer(tabs);
    }

    private Tab createSectionTitle(String title) {
        Span span = new Span(title);
        span.getStyle()
                .set("font-weight", "bold")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-top", "var(--lumo-space-m)")
                .set("margin-left", "var(--lumo-space-m)");

        Tab tab = new Tab(span);
        tab.setEnabled(false);
        tab.getElement().setAttribute("aria-hidden", "true");
        return tab;
    }

    private Tab createTab(VaadinIcon icon, String title, Class<? extends Component> navigationTarget) {
        return createTab(icon, title, navigationTarget, Collections.emptyMap());
    }

    private Tab createTab(VaadinIcon icon, String title, Class<? extends Component> navigationTarget, Map<String, String> queryParams) {
        Icon tabIcon = icon.create();
        tabIcon.getStyle().set("margin-right", "8px");

        RouterLink link = new RouterLink();
        link.add(tabIcon, new Span(title));
        link.setRoute(navigationTarget);
        if (!queryParams.isEmpty()) {
            link.setQueryParameters(QueryParameters.simple(queryParams));
        }
        link.setTabIndex(-1);

        return new Tab(link);
    }
}
