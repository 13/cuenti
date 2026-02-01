package com.cuenti.homebanking.views;

import com.cuenti.homebanking.service.GlobalSettingService;
import com.cuenti.homebanking.service.UserService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.Lumo;

import java.util.Locale;

/**
 * Login view for user authentication.
 * Default language set to English.
 */
@Route("login")
@PageTitle("Login | Cuenti")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm loginForm = new LoginForm();
    private final RouterLink registerLink = new RouterLink("", RegisterView.class);
    private final GlobalSettingService globalSettingService;

    public LoginView(UserService userService, GlobalSettingService globalSettingService) {
        this.globalSettingService = globalSettingService;
        
        // Force English for unauthenticated views
        UI.getCurrent().setLocale(Locale.ENGLISH);
        VaadinSession.getCurrent().setLocale(Locale.ENGLISH);
        
        // Set Dark Mode theme variant
        getElement().setAttribute("theme", Lumo.DARK);
        
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        // Configure i18n
        LoginI18n i18n = LoginI18n.createDefault();
        i18n.getForm().setTitle("");
        i18n.getForm().setUsername(getTranslation("login.username"));
        i18n.getForm().setPassword(getTranslation("login.password"));
        i18n.getForm().setSubmit(getTranslation("login.submit"));
        i18n.getErrorMessage().setTitle(getTranslation("login.error.title"));
        i18n.getErrorMessage().setMessage(getTranslation("login.error.message"));
        loginForm.setI18n(i18n);

        loginForm.setAction("login");
        loginForm.setForgotPasswordButtonVisible(false);

        // Logo branding
        Image logo = new Image("images/Cuenti.png", "Cuenti");
        logo.setWidth("200px");
        logo.getStyle().set("margin-bottom", "30px");

        registerLink.setText(getTranslation("login.register_link"));
        registerLink.getStyle().set("color", "var(--lumo-primary-color)");

        add(
            logo,
            loginForm,
            registerLink
        );
        
        updateRegisterLinkVisibility();
    }

    private void updateRegisterLinkVisibility() {
        registerLink.setVisible(globalSettingService.isRegistrationEnabled());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (event.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            loginForm.setError(true);
        }
        updateRegisterLinkVisibility();
    }
}
