package com.cuenti.app.views;

import com.cuenti.app.model.User;
import com.cuenti.app.security.SecurityUtils;
import com.cuenti.app.security.TwoFactorSession;
import com.cuenti.app.service.AuditService;
import com.cuenti.app.service.UserService;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;

/**
 * Second sign-in step for accounts with two-factor sign-in: asks for the
 * authenticator code (or a recovery code). {@link TwoFactorGate} sends every
 * other navigation here until it succeeds.
 */
@Route("two-factor")
@PermitAll
public class TwoFactorView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    /** Wrong codes allowed per sign-in before the password has to be entered again. */
    static final int MAX_ATTEMPTS = 5;
    private static final String ATTEMPTS = TwoFactorView.class.getName() + ".attempts";

    private final SecurityUtils securityUtils;
    private final UserService userService;
    private final AuditService auditService;
    private final TextField code = new TextField();
    private final Span error = new Span();

    public TwoFactorView(SecurityUtils securityUtils, UserService userService, AuditService auditService) {
        this.securityUtils = securityUtils;
        this.userService = userService;
        this.auditService = auditService;

        addClassName("auth-view");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        Image logo = new Image("images/Cuenti.png", getTranslation("app.name"));
        Div logoTile = new Div(logo);
        logoTile.addClassName("auth-logo-tile");
        Span logoText = new Span(getTranslation("app.name"));
        logoText.addClassName("auth-brand-text");
        Div brand = new Div(logoTile, logoText);
        brand.addClassName("auth-brand");

        Paragraph hint = new Paragraph(getTranslation("twofactor.hint"));
        hint.addClassName("auth-footnote");
        hint.getStyle().set("border-top", "none");

        code.setLabel(getTranslation("twofactor.code"));
        code.setWidthFull();
        code.setAutofocus(true);
        code.getElement().setAttribute("autocomplete", "one-time-code");
        code.getElement().setAttribute("autocapitalize", "characters");
        code.setMaxLength(20);

        error.addClassName("auth-notice");
        error.getElement().setAttribute("role", "alert");
        error.setVisible(false);

        Button verify = new Button(getTranslation("twofactor.verify"), e -> verify());
        verify.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        verify.addClassName("auth-primary-btn");
        verify.setWidthFull();
        verify.addClickShortcut(Key.ENTER);

        Button cancel = new Button(getTranslation("twofactor.cancel"), e -> securityUtils.logout());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Div card = new Div(brand, hint, error, code, verify, cancel);
        card.addClassName("auth-card");
        card.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "var(--vaadin-gap-s)");
        add(card);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("twofactor.title") + " | " + getTranslation("app.name");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        ThemePreference.applyLocaleFromCookie(event.getUI());
        ThemePreference.applyThemeFromCookie(event.getUI());
        // Nothing to do here without a pending second factor
        String username = securityUtils.getAuthenticatedUsername().orElse(null);
        if (username == null || TwoFactorSession.isVerified(username)
                || !userService.findByUsername(username).isTotpEnabled()) {
            event.forwardTo("");
        }
    }

    private void verify() {
        String username = securityUtils.getAuthenticatedUsername().orElse(null);
        if (username == null) {
            UI.getCurrent().getPage().setLocation("login");
            return;
        }
        User user = userService.findByUsername(username);
        if (userService.verifySecondFactor(user, code.getValue())) {
            VaadinSession.getCurrent().setAttribute(ATTEMPTS, null);
            TwoFactorSession.markVerified(username);
            auditService.logSecurity(username, "2FA_OK", "web");
            UI.getCurrent().navigate("");
            return;
        }

        auditService.logSecurity(username, "2FA_FAILED", "web");
        Integer attempts = (Integer) VaadinSession.getCurrent().getAttribute(ATTEMPTS);
        int failed = attempts == null ? 1 : attempts + 1;
        if (failed >= MAX_ATTEMPTS) {
            // back to the password: bounds guessing to the login rate limit
            VaadinSession.getCurrent().setAttribute(ATTEMPTS, null);
            securityUtils.logout();
            return;
        }
        VaadinSession.getCurrent().setAttribute(ATTEMPTS, failed);
        code.clear();
        code.focus();
        error.setText(getTranslation("twofactor.invalid"));
        error.setVisible(true);
    }
}
