package com.cuenti.homebanking.views;

import com.cuenti.homebanking.data.User;
import com.cuenti.homebanking.security.AuthenticatedUser;
import com.cuenti.homebanking.services.UserService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Footer;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import com.vaadin.flow.theme.lumo.Lumo;
import com.vaadin.flow.theme.lumo.LumoUtility;
import java.util.List;
import java.util.Optional;

/**
 * The main view is a top-level placeholder for other views.
 */
@Layout
@AnonymousAllowed
public class MainLayout extends AppLayout implements AfterNavigationObserver {

    private H4 viewTitle;
    private Button themeToggle;

    private final AuthenticatedUser authenticatedUser;
    private final AccessAnnotationChecker accessChecker;
    private final UserService userService;
    private User currentUser;

    public MainLayout(AuthenticatedUser authenticatedUser, AccessAnnotationChecker accessChecker, UserService userService) {
        this.authenticatedUser = authenticatedUser;
        this.accessChecker = accessChecker;
        this.userService = userService;
        this.currentUser = authenticatedUser.get().orElse(null);

        setPrimarySection(Section.DRAWER);
        addDrawerContent();
        addHeaderContent();
        applyTheme();
    }

    private void applyTheme() {
        boolean isDark = currentUser == null || currentUser.isDarkMode();
        String theme = isDark ? Lumo.DARK : Lumo.LIGHT;
        UI.getCurrent().getElement().executeJs(
            "document.documentElement.setAttribute('theme', $0)", theme
        );
        updateThemeToggleIcon();
    }

    private void toggleTheme() {
        if (currentUser != null) {
            boolean newDarkMode = !currentUser.isDarkMode();
            currentUser.setDarkMode(newDarkMode);
            userService.updateDarkMode(currentUser, newDarkMode);
            applyTheme();
        } else {
            // For anonymous users, just toggle without persisting
            UI ui = UI.getCurrent();
            ui.getElement().executeJs(
                "return document.documentElement.getAttribute('theme')"
            ).then(String.class, currentTheme -> {
                String newTheme = Lumo.DARK.equals(currentTheme) ? Lumo.LIGHT : Lumo.DARK;
                ui.getElement().executeJs(
                    "document.documentElement.setAttribute('theme', $0)", newTheme
                );
                updateThemeToggleIcon();
            });
        }
    }

    private void addHeaderContent() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.setAriaLabel("Menu toggle");

        viewTitle = new H4();
        viewTitle.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);

        // Create right-side controls
        HorizontalLayout rightControls = new HorizontalLayout();
        rightControls.setAlignItems(FlexComponent.Alignment.CENTER);
        rightControls.setSpacing(true);

        // Dark/Light mode toggle button
        themeToggle = new Button(VaadinIcon.ADJUST.create());
        themeToggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        themeToggle.setTooltipText("Toggle dark/light mode");
        themeToggle.addClickListener(e -> toggleTheme());

        rightControls.add(themeToggle);

        // Logout button (only if authenticated)
        Optional<User> maybeUser = authenticatedUser.get();
        if (maybeUser.isPresent()) {
            Button logoutButton = new Button("", VaadinIcon.SIGN_OUT.create(), e -> {
                authenticatedUser.logout();
            });
            logoutButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            rightControls.add(logoutButton);
        }

        // Spacer to push controls to the right
        Div spacer = new Div();
        spacer.getStyle().set("flex-grow", "1");

        addToNavbar(true, toggle, viewTitle, spacer, rightControls);
    }

    private void updateThemeToggleIcon() {
        if (themeToggle == null) return;
        boolean isDark = currentUser == null || currentUser.isDarkMode();
        themeToggle.setIcon(isDark ? VaadinIcon.SUN_O.create() : VaadinIcon.MOON_O.create());
    }

    private void addDrawerContent() {
        Span appName = new Span("Cuenti");
        appName.addClassNames(LumoUtility.FontWeight.SEMIBOLD, LumoUtility.FontSize.LARGE);
        Header header = new Header(appName);

        Scroller scroller = new Scroller(createNavigation());

        addToDrawer(header, scroller, createFooter());
    }

    private SideNav createNavigation() {
        SideNav nav = new SideNav();

        List<MenuEntry> menuEntries = MenuConfiguration.getMenuEntries();
        menuEntries.forEach(entry -> {
            if (entry.icon() != null) {
                nav.addItem(new SideNavItem(entry.title(), entry.path(), new SvgIcon(entry.icon())));
            } else {
                nav.addItem(new SideNavItem(entry.title(), entry.path()));
            }
        });

        return nav;
    }

    private Footer createFooter() {
        Footer layout = new Footer();

        Optional<User> maybeUser = authenticatedUser.get();
        if (maybeUser.isPresent()) {
            User user = maybeUser.get();

            MenuBar userMenu = new MenuBar();
            userMenu.setThemeName("tertiary-inline contrast");

            MenuItem userName = userMenu.addItem("");
            Div div = new Div();
            div.add(user.getName());
            div.add(new Icon("lumo", "dropdown"));
            div.addClassNames(LumoUtility.Display.FLEX, LumoUtility.AlignItems.CENTER, LumoUtility.Gap.SMALL);
            userName.add(div);
            userName.getSubMenu().addItem("Sign out", e -> {
                authenticatedUser.logout();
            });

            layout.add(userMenu);
        } else {
            Anchor loginLink = new Anchor("login", "Sign in");
            layout.add(loginLink);
        }

        return layout;
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        viewTitle.setText(getCurrentPageTitle());
    }

    private String getCurrentPageTitle() {
        return MenuConfiguration.getPageHeader(getContent()).orElse("");
    }
}
