package com.cuenti.homebanking.views.login;

import com.cuenti.homebanking.security.AuthenticatedUser;
import com.cuenti.homebanking.services.GlobalSettingService;
import com.cuenti.homebanking.views.register.RegisterView;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.login.LoginOverlay;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.router.internal.RouteUtil;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@AnonymousAllowed
@PageTitle("Login")
@Route(value = "login")
public class LoginView extends LoginOverlay implements BeforeEnterObserver {

    private final AuthenticatedUser authenticatedUser;
    private final GlobalSettingService globalSettingService;

    public LoginView(AuthenticatedUser authenticatedUser, GlobalSettingService globalSettingService) {
        this.authenticatedUser = authenticatedUser;
        this.globalSettingService = globalSettingService;
        setAction(RouteUtil.getRoutePath(VaadinService.getCurrent().getContext(), getClass()));

        LoginI18n i18n = LoginI18n.createDefault();
        i18n.setHeader(new LoginI18n.Header());
        i18n.getHeader().setTitle("Cuenti");
        i18n.getHeader().setDescription("Personal Finance Manager");

        // Add registration link info if enabled
        if (globalSettingService.isRegistrationEnabled()) {
            i18n.setAdditionalInformation("Don't have an account? Click 'Register' below.");
        } else {
            i18n.setAdditionalInformation(null);
        }

        setI18n(i18n);
        setForgotPasswordButtonVisible(false);
        setOpened(true);

        // Add register link if registration is enabled
        if (globalSettingService.isRegistrationEnabled()) {
            RouterLink registerLink = new RouterLink("Register", RegisterView.class);
            registerLink.getStyle()
                    .set("margin-top", "var(--lumo-space-m)")
                    .set("display", "block")
                    .set("text-align", "center");
            getFooter().add(registerLink);
        }
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (authenticatedUser.get().isPresent()) {
            // Already logged in
            setOpened(false);
            event.forwardTo("");
        }

        setError(event.getLocation().getQueryParameters().getParameters().containsKey("error"));
    }
}
