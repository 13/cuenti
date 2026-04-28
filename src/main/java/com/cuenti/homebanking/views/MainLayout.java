package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.AssetService;
import com.cuenti.homebanking.service.UserService;
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
                updateAssetPricesAsync();
            } catch (Exception e) {
                securityUtils.logout();
                return;
            }
        }

        applyTheme();
        injectNavStyles();
        setPrimarySection(Section.DRAWER);
        createHeader();
        createDrawer();
    }

    // ── Theme ──────────────────────────────────────────────────────────────────

    private void updateAssetPricesAsync() {
        if (currentUser != null) {
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
        UI.getCurrent().getElement().executeJs(
                "document.documentElement.setAttribute('theme', $0)",
                isDark ? Lumo.DARK : Lumo.LIGHT
        );
    }

    private void toggleTheme() {
        if (currentUser != null) {
            boolean newDark = !currentUser.isDarkMode();
            currentUser.setDarkMode(newDark);
            userService.updateDarkMode(currentUser, newDark);
            applyTheme();
        }
    }

    // ── Injected nav styles ────────────────────────────────────────────────────

    private void injectNavStyles() {
        UI.getCurrent().getElement().executeJs(
            "if (!document.getElementById('cuenti-nav-styles')) {" +
            "  const s = document.createElement('style');" +
            "  s.id = 'cuenti-nav-styles';" +
            "  s.textContent = '" +
            "    .nav-item {" +
            "      display: flex; align-items: center; gap: 10px;" +
            "      width: 100%; box-sizing: border-box;" +
            "      padding: 9px 16px; margin: 1px 0; border-radius: 10px;" +
            "      text-decoration: none; color: var(--lumo-secondary-text-color);" +
            "      font-size: var(--lumo-font-size-s); font-weight: 500;" +
            "      transition: background 0.12s, color 0.12s;" +
            "    }" +
            "    .nav-item:hover {" +
            "      background: var(--lumo-contrast-5pct);" +
            "      color: var(--lumo-body-text-color);" +
            "    }" +
            "    .nav-item[highlight] {" +
            "      background: var(--lumo-primary-color-10pct, rgba(26,119,242,0.1));" +
            "      color: var(--lumo-primary-color);" +
            "      font-weight: 600;" +
            "    }" +
            "    .nav-item vaadin-icon {" +
            "      width: 18px; height: 18px; flex-shrink: 0; color: currentColor;" +
            "    }" +
            "    .nav-section-label {" +
            "      display: block; font-size: 10px; font-weight: 700;" +
            "      letter-spacing: 0.09em; text-transform: uppercase;" +
            "      color: var(--lumo-tertiary-text-color);" +
            "      padding: 16px 16px 4px;" +
            "    }" +
            "    .nav-divider {" +
            "      height: 1px; background: var(--lumo-contrast-10pct); margin: 6px 16px;" +
            "    }" +
            "    vaadin-app-layout::part(navbar) {" +
            "      padding: 0;" +
            "      height: 52px;" +
            "      min-height: 52px;" +
            "      box-shadow: none;" +
            "      border-bottom: 1px solid var(--lumo-contrast-10pct);" +
            "    }" +
            "  ';" +
            "  document.head.appendChild(s);" +
            "}"
        );
    }

    // ── Header (top navbar) ────────────────────────────────────────────────────

    private void createHeader() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.getStyle().set("color", "var(--lumo-secondary-text-color)");

        // Theme toggle — icon reflects current mode
        boolean isDark = currentUser == null || currentUser.isDarkMode();
        Icon themeIcon = new Icon(isDark ? VaadinIcon.SUN_O : VaadinIcon.MOON);
        Button themeBtn = new Button(themeIcon);
        themeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        themeBtn.getElement().setProperty("title", "Toggle Dark / Light Mode");
        themeBtn.getStyle().set("color", "var(--lumo-secondary-text-color)");
        themeBtn.addClickListener(e -> {
            toggleTheme();
            boolean nowDark = currentUser != null && currentUser.isDarkMode();
            themeBtn.setIcon(new Icon(nowDark ? VaadinIcon.SUN_O : VaadinIcon.MOON));
        });

        // User avatar (initials circle)
        String uname    = currentUser != null ? currentUser.getUsername() : "?";
        String initials = uname.length() >= 2 ? uname.substring(0, 2).toUpperCase() : uname.toUpperCase();

        Span avatar = new Span(initials);
        avatar.getStyle()
                .set("width", "28px").set("height", "28px").set("border-radius", "50%")
                .set("background", "var(--lumo-primary-color)").set("color", "white")
                .set("font-size", "11px").set("font-weight", "700").set("letter-spacing", "0.03em")
                .set("display", "flex").set("align-items", "center")
                .set("justify-content", "center").set("flex-shrink", "0");

        Span userSpan = new Span(uname);
        userSpan.getStyle()
                .set("font-size", "var(--lumo-font-size-s)").set("font-weight", "500")
                .set("color", "var(--lumo-body-text-color)");

        Button logoutBtn = new Button(new Icon(VaadinIcon.SIGN_OUT));
        logoutBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        logoutBtn.getElement().setProperty("title", getTranslation("nav.logout"));
        logoutBtn.getStyle().set("color", "var(--lumo-secondary-text-color)");
        logoutBtn.addClickListener(e -> securityUtils.logout());

        HorizontalLayout right = new HorizontalLayout(themeBtn, avatar, userSpan, logoutBtn);
        right.setAlignItems(FlexComponent.Alignment.CENTER);
        right.setSpacing(false);
        right.getStyle().set("gap", "var(--lumo-space-xs)");

        HorizontalLayout header = new HorizontalLayout(toggle, right);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setPadding(false);
        header.getStyle()
                .set("padding", "0 var(--lumo-space-m)")
                .set("height", "100%")
                .set("box-sizing", "border-box");

        addToNavbar(header);
    }

    // ── Drawer ─────────────────────────────────────────────────────────────────

    private void createDrawer() {
        // Logo at the top of the drawer
        Image logo = new Image("images/Cuenti.png", "Cuenti");
        logo.setHeight("53px");
        logo.setWidth("auto");

        Span logoText = new Span("Cuenti");
        logoText.getStyle()
                .set("font-size", "var(--lumo-font-size-xxl)")
                .set("font-weight", "700")
                .set("color", "var(--lumo-header-text-color)");

        HorizontalLayout logoContent = new HorizontalLayout(logo, logoText);
        logoContent.setAlignItems(FlexComponent.Alignment.CENTER);
        logoContent.setSpacing(false);
        logoContent.getStyle().set("gap", "var(--lumo-space-s)");

        Div logoSection = new Div(logoContent);
        logoSection.getStyle()
                .set("padding", "0 var(--lumo-space-xs)")
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)")
                .set("display", "flex").set("align-items", "center")
                .set("height", "54px").set("flex-shrink", "0")
                .set("box-sizing", "border-box");

        // Scrollable nav area
        VerticalLayout nav = new VerticalLayout();
        nav.setPadding(false);
        nav.setSpacing(false);
        nav.getStyle()
                .set("overflow-y", "auto").set("flex-grow", "1")
                .set("padding-bottom", "var(--lumo-space-l)");

        // ── General ────────────────────────────────────────────────
        nav.add(sectionLabel(getTranslation("nav.general")));
        nav.add(navItem(VaadinIcon.DASHBOARD,     getTranslation("nav.dashboard"),    DashboardView.class));
        nav.add(navItem(VaadinIcon.LIST,           getTranslation("nav.transactions"), TransactionHistoryView.class));
        nav.add(navItem(VaadinIcon.CALENDAR_CLOCK, getTranslation("nav.scheduled"),   ScheduledTransactionsView.class));
        nav.add(navItem(VaadinIcon.CHART,          getTranslation("nav.statistics"),  StatisticsView.class));
        nav.add(navItem(VaadinIcon.TRENDING_UP,    getTranslation("nav.forecasts"),   ForecastsView.class));
        nav.add(navItem(VaadinIcon.CAR,            getTranslation("nav.vehicles"),    VehiclesView.class));

        nav.add(navDivider());

        // ── Management ─────────────────────────────────────────────
        nav.add(sectionLabel(getTranslation("nav.management")));
        nav.add(navItem(VaadinIcon.WALLET,   getTranslation("nav.manage_accounts"), AccountManagementView.class));
        nav.add(navItem(VaadinIcon.USERS,    getTranslation("nav.payees"),          PayeeManagementView.class));
        nav.add(navItem(VaadinIcon.SITEMAP,  getTranslation("nav.categories"),      CategoryManagementView.class));
        nav.add(navItem(VaadinIcon.TAGS,     getTranslation("nav.tags"),            TagManagementView.class));
        nav.add(navItem(VaadinIcon.MONEY,    getTranslation("nav.currencies"),      CurrencyManagementView.class));
        nav.add(navItem(VaadinIcon.CHART_3D, getTranslation("nav.assets"),          AssetManagementView.class));

        nav.add(navDivider());

        // ── Settings ───────────────────────────────────────────────
        nav.add(sectionLabel(getTranslation("nav.settings")));
        if (currentUser != null && currentUser.getRoles().contains("ROLE_ADMIN")) {
            nav.add(navItem(VaadinIcon.KEY, getTranslation("settings.administration"),
                    SettingsView.class, Map.of("section", "admin")));
        }
        nav.add(navItem(VaadinIcon.USER,     getTranslation("settings.user_title"),
                SettingsView.class, Map.of("section", "user")));
        nav.add(navItem(VaadinIcon.EXCHANGE, getTranslation("settings.import_export_title"),
                SettingsView.class, Map.of("section", "import-export")));

        nav.add(navDivider());

        // ── Info ────────────────────────────────────────────────────
        nav.add(sectionLabel(getTranslation("nav.info", "Information")));
        nav.add(navItem(VaadinIcon.QUESTION_CIRCLE, getTranslation("nav.help",  "Help"),  HelpView.class));
        nav.add(navItem(VaadinIcon.INFO_CIRCLE,     getTranslation("nav.about", "About"), AboutView.class));

        Div drawer = new Div(logoSection, nav);
        drawer.setHeightFull();
        drawer.getStyle()
                .set("display", "flex").set("flex-direction", "column")
                .set("background", "var(--lumo-base-color)");

        addToDrawer(drawer);
    }

    // ── Nav item helpers ───────────────────────────────────────────────────────

    private RouterLink navItem(VaadinIcon icon, String label,
                               Class<? extends Component> target) {
        return navItem(icon, label, target, Collections.emptyMap());
    }

    private RouterLink navItem(VaadinIcon icon, String label,
                               Class<? extends Component> target,
                               Map<String, String> queryParams) {
        Icon ico = icon.create();
        ico.getStyle()
                .set("width", "18px").set("height", "18px")
                .set("flex-shrink", "0").set("color", "currentColor");

        Span text = new Span(label);

        RouterLink link = new RouterLink();
        link.addClassName("nav-item");
        link.add(ico, text);
        link.setRoute(target);
        if (!queryParams.isEmpty()) {
            link.setQueryParameters(QueryParameters.simple(queryParams));
        }
        link.getElement().getStyle().set("width", "100%");
        link.setTabIndex(-1);

        // Highlight active item based on current URL
        link.addAttachListener(e -> link.getElement().executeJs(
            "const check = () => {" +
            "  const href = this.getAttribute('href') || '';" +
            "  const path = window.location.pathname;" +
            "  if (href && (path === href || path.startsWith(href + '?'))) {" +
            "    this.setAttribute('highlight', '');" +
            "  } else {" +
            "    this.removeAttribute('highlight');" +
            "  }" +
            "};" +
            "check();" +
            "window.addEventListener('vaadin-router-location-changed', check);"
        ));

        return link;
    }

    private Span sectionLabel(String title) {
        Span s = new Span(title);
        s.addClassName("nav-section-label");
        return s;
    }

    private Div navDivider() {
        Div d = new Div();
        d.addClassName("nav-divider");
        return d;
    }
}
