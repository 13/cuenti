package com.cuenti.app.views;

import com.cuenti.app.model.User;
import com.cuenti.app.security.SecurityUtils;
import com.cuenti.app.service.AssetService;
import com.cuenti.app.service.UserService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.VaadinSession;
import com.cuenti.app.views.ThemePreference;
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

    // ── Theme ──────────────────────────────────────────────────────────────────

    private void updateAssetPricesAsync() {
        if (currentUser != null) {
            try {
                assetService.updateUserAssetPricesThrottled(currentUser);
            } catch (Exception e) {
                log.error("Error updating asset prices for user: {}", currentUser.getUsername(), e);
            }
        }
    }

    private void applyTheme() {
        boolean isDark = currentUser == null || currentUser.isDarkMode();
        ThemePreference.applyTheme(UI.getCurrent(), isDark);
    }

    private void toggleTheme() {
        if (currentUser != null) {
            boolean newDark = !currentUser.isDarkMode();
            currentUser.setDarkMode(newDark);
            userService.updateDarkMode(currentUser, newDark);
            applyTheme();
        }
    }

    // ── Header (top navbar) ────────────────────────────────────────────────────

    private void createHeader() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.getStyle().set("color", "var(--vaadin-text-color-secondary)");

        // Theme toggle — icon reflects current mode
        boolean isDark = currentUser == null || currentUser.isDarkMode();
        Icon themeIcon = new Icon(isDark ? VaadinIcon.SUN_O : VaadinIcon.MOON);
        Button themeBtn = new Button(themeIcon);
        themeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        themeBtn.getElement().setProperty("title", getTranslation("layout.toggle_theme"));
        themeBtn.getElement().setAttribute("aria-label", getTranslation("layout.toggle_theme"));
        themeBtn.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        themeBtn.addClickListener(e -> {
            toggleTheme();
            boolean nowDark = currentUser != null && currentUser.isDarkMode();
            themeBtn.setIcon(new Icon(nowDark ? VaadinIcon.SUN_O : VaadinIcon.MOON));
        });

        // User avatar (initials circle)
        String uname    = currentUser != null ? currentUser.getUsername() : "?";
        String initials = uname.length() >= 2 ? uname.substring(0, 2).toUpperCase() : uname.toUpperCase();

        Span avatar = new Span(initials);
        avatar.addClassName("user-avatar");

        Span userSpan = new Span(uname);
        userSpan.getStyle()
                .set("font-size", "var(--aura-font-size-s)").set("font-weight", "500")
                .set("color", "var(--vaadin-text-color)");

        Button logoutBtn = new Button(new Icon(VaadinIcon.SIGN_OUT));
        logoutBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        logoutBtn.getElement().setProperty("title", getTranslation("nav.logout"));
        logoutBtn.getElement().setAttribute("aria-label", getTranslation("nav.logout"));
        logoutBtn.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        logoutBtn.addClickListener(e -> {
            // Persist current theme and locale to cookies so the login page can apply the same settings after logout
            boolean isDarkNow = currentUser == null || currentUser.isDarkMode();
            ThemePreference.persistThemeCookie(UI.getCurrent(), isDarkNow);
            String localeTag = UI.getCurrent().getLocale() != null
                    ? UI.getCurrent().getLocale().toLanguageTag()
                    : (currentUser != null && currentUser.getLocale() != null ? currentUser.getLocale() : "en");
            ThemePreference.persistLocaleCookie(UI.getCurrent(), localeTag);
            securityUtils.logout();
        });

        RouterLink userLink = new RouterLink();
        userLink.setRoute(SettingsUserView.class);
        userLink.addClassName("user-link");
        userLink.getElement().setAttribute("aria-label", getTranslation("settings.user_title"));
        userLink.add(avatar, userSpan);

        HorizontalLayout right = new HorizontalLayout(themeBtn, userLink, logoutBtn);
        right.setAlignItems(FlexComponent.Alignment.CENTER);
        right.setSpacing(false);
        right.getStyle().set("gap", "var(--vaadin-gap-xs)");

        HorizontalLayout header = new HorizontalLayout(toggle, right);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setPadding(false);
        header.getStyle()
                .set("padding", "0 var(--vaadin-gap-m)")
                .set("height", "100%")
                .set("box-sizing", "border-box");

        addToNavbar(header);
    }

    // ── Drawer ─────────────────────────────────────────────────────────────────

    private void createDrawer() {
        // Logo at the top of the drawer
        Image logo = new Image("images/Cuenti.png", getTranslation("app.name"));
        logo.setHeight("32px");
        logo.setWidth("auto");
        logo.getStyle().set("margin", "0 2px 0 6px");

        Span logoText = new Span(getTranslation("app.name"));
        logoText.getStyle()
                .set("font-size", "var(--cuenti-font-size-xxl)")
                .set("font-weight", "700")
                .set("color", "var(--vaadin-text-color)");

        HorizontalLayout logoContent = new HorizontalLayout(logo, logoText);
        logoContent.setAlignItems(FlexComponent.Alignment.CENTER);
        logoContent.setSpacing(false);
        logoContent.getStyle().set("gap", "var(--vaadin-gap-s)");

        Div logoSection = new Div(logoContent);
        logoSection.getStyle()
                .set("padding", "0 var(--vaadin-gap-xs)")
                .set("border-bottom", "1px solid var(--vaadin-border-color-secondary)")
                .set("display", "flex").set("align-items", "center")
                .set("height", "52px").set("flex-shrink", "0")
                .set("box-sizing", "border-box");

        SideNav general = navSection(getTranslation("nav.general"), true,
                new SideNavItem(getTranslation("nav.dashboard"),    DashboardView.class,             VaadinIcon.DASHBOARD.create()),
                new SideNavItem(getTranslation("nav.transactions"), TransactionHistoryView.class,    VaadinIcon.LIST.create()),
                new SideNavItem(getTranslation("nav.scheduled"),    ScheduledTransactionsView.class, VaadinIcon.CALENDAR_CLOCK.create()),
                new SideNavItem(getTranslation("nav.statistics"),   StatisticsView.class,            VaadinIcon.CHART.create()),
                new SideNavItem(getTranslation("nav.forecasts"),    ForecastsView.class,             VaadinIcon.TRENDING_UP.create()),
                new SideNavItem(getTranslation("nav.vehicles"),     VehiclesView.class,              VaadinIcon.CAR.create()));

        SideNav management = navSection(getTranslation("nav.management"), false,
                new SideNavItem(getTranslation("nav.manage_accounts"), AccountManagementView.class,  VaadinIcon.WALLET.create()),
                new SideNavItem(getTranslation("nav.payees"),          PayeeManagementView.class,    VaadinIcon.USERS.create()),
                new SideNavItem(getTranslation("nav.categories"),      CategoryManagementView.class, VaadinIcon.SITEMAP.create()),
                new SideNavItem(getTranslation("nav.tags"),            TagManagementView.class,      VaadinIcon.TAGS.create()),
                new SideNavItem(getTranslation("nav.currencies"),      CurrencyManagementView.class, VaadinIcon.MONEY.create()),
                new SideNavItem(getTranslation("nav.assets"),          AssetManagementView.class,    VaadinIcon.CHART_3D.create()));

        SideNav settings = navSection(getTranslation("nav.settings"), false);
        if (currentUser != null && currentUser.getRoles().contains("ROLE_ADMIN")) {
            settings.addItem(new SideNavItem(getTranslation("settings.administration"),
                    SettingsAdminView.class, VaadinIcon.KEY.create()));
        }
        settings.addItem(new SideNavItem(getTranslation("settings.user_title"),
                SettingsUserView.class, VaadinIcon.USER.create()));
        settings.addItem(new SideNavItem(getTranslation("settings.import_export_title"),
                SettingsImportExportView.class, VaadinIcon.EXCHANGE.create()));

        SideNav info = navSection(getTranslation("nav.info"), false,
                new SideNavItem(getTranslation("nav.help"),  HelpView.class,  VaadinIcon.QUESTION_CIRCLE.create()),
                new SideNavItem(getTranslation("nav.about"), AboutView.class, VaadinIcon.INFO_CIRCLE.create()));

        Div nav = new Div(general, management, settings, info);
        nav.addClassName("drawer-nav");

        Div drawer = new Div(logoSection, nav);
        drawer.setHeightFull();
        drawer.getStyle()
                .set("display", "flex").set("flex-direction", "column")
                .set("background", "var(--aura-surface-color-solid)");

        addToDrawer(drawer);
    }

    private SideNav navSection(String label, boolean expanded, SideNavItem... items) {
        SideNav nav = new SideNav(label);
        nav.setCollapsible(true);
        nav.setExpanded(expanded);
        nav.setWidthFull();
        for (SideNavItem item : items) {
            nav.addItem(item);
        }
        return nav;
    }
}
