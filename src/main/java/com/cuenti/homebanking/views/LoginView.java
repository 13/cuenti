package com.cuenti.homebanking.views;

import com.cuenti.homebanking.service.GlobalSettingService;
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

/** Login view for user authentication. Default language set to English. */
@Route("login")
@PageTitle("Login | Cuenti")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

  private final LoginForm loginForm = new LoginForm();
  private final RouterLink registerLink = new RouterLink();
  private final GlobalSettingService globalSettingService;

  public LoginView(GlobalSettingService globalSettingService) {
    this.globalSettingService = globalSettingService;

    configureLayout();
    configureLoginForm();
    configureRegisterLink();

    Image logo = createLogo();

    add(logo, loginForm, registerLink);
  }

  private void configureLayout() {
    setSizeFull();
    setAlignItems(Alignment.CENTER);
    setJustifyContentMode(JustifyContentMode.START);
    setPadding(true);
    setSpacing(true);

    // Allow scrolling on small screens
    getStyle().set("overflow-y", "auto");
  }

  private void configureLoginForm() {
    LoginI18n i18n = LoginI18n.createDefault();
    i18n.getForm().setTitle("");
    i18n.getForm().setUsername(getTranslation("login.username"));
    i18n.getForm().setPassword(getTranslation("login.password"));
    i18n.getForm().setSubmit(getTranslation("login.submit"));
    i18n.getErrorMessage().setTitle(getTranslation("login.error.title"));
    i18n.getErrorMessage().setMessage(getTranslation("login.error.message"));

    loginForm.setI18n(i18n);
    loginForm.setAction("login"); // Spring Security endpoint
    loginForm.setForgotPasswordButtonVisible(false);

    // Disable auto-focus on username field
    loginForm.getElement().setAttribute("no-autofocus", true);

  }

  private void configureRegisterLink() {
    registerLink.setRoute(RegisterView.class);
    registerLink.setText(getTranslation("login.register_link"));
    registerLink.getElement().setAttribute("aria-label", "Register new account");
  }

  private Image createLogo() {
    Image logo = new Image("images/Cuenti.png", "Cuenti");
    logo.getElement().setAttribute("srcset",
            "images/CuentiText.png 120w, images/Cuenti.png 800w");
    // When viewport is <=480px use ~120px image, otherwise use up to 200px.
    logo.getElement().setAttribute("sizes",
            "(max-width: 480px) 120px, 200px");
    // Keep responsive CSS as well
    logo.getStyle().set("width", "clamp(56px, 40%, 200px)");
    logo.getStyle().set("max-width", "200px");
    logo.getStyle().set("margin", "40px 0 30px 0");
    logo.getElement().setAttribute("alt", "Cuenti");
    return logo;
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    UI ui = event.getUI();

    // Force English for unauthenticated users
    ui.setLocale(Locale.ENGLISH);
    VaadinSession.getCurrent().setLocale(Locale.ENGLISH);

    // Apply dark theme globally
    ui.getElement().setAttribute("theme", Lumo.DARK);

    // Handle login error (?error)
    boolean error = event.getLocation().getQueryParameters().getParameters().containsKey("error");
    loginForm.setError(error);

    // Feature toggle: registration enabled
    registerLink.setVisible(globalSettingService.isRegistrationEnabled());
  }
}
