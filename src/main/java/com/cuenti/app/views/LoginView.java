package com.cuenti.app.views;

import com.cuenti.app.repository.UserRepository;
import com.cuenti.app.service.GlobalSettingService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Two-stage login in the StarPass style: a compact branded card with a
 * primary demo sign-in, and an expandable username/password form behind
 * "Other sign-in options".
 */
@Route("login")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

  @Override
  public String getPageTitle() {
    return getTranslation("login.title") + " | " + getTranslation("app.name");
  }

  private final LoginForm loginForm = new LoginForm();
  private final RouterLink registerLink = new RouterLink();
  private final GlobalSettingService globalSettingService;

  private final Div primaryStage = new Div();
  private final Div formStage = new Div();

  public LoginView(GlobalSettingService globalSettingService, UserRepository userRepository) {
    this.globalSettingService = globalSettingService;

    configureLayout();
    configureLoginForm();
    configureRegisterLink();

    boolean demoAvailable = userRepository.existsByUsername("demo");

    // ── Brand ────────────────────────────────────────────────────────
    Image logo = new Image("images/Cuenti.png", getTranslation("app.name"));
    logo.getElement().setAttribute("alt", getTranslation("app.name"));
    Div logoTile = new Div(logo);
    logoTile.addClassName("auth-logo-tile");

    Span logoText = new Span(getTranslation("app.name"));
    logoText.addClassName("auth-brand-text");

    Div brand = new Div(logoTile, logoText);
    brand.addClassName("auth-brand");

    // ── Stage 1: primary options ─────────────────────────────────────
    primaryStage.addClassName("auth-stage");
    primaryStage.setId("login-primary-stage");

    if (demoAvailable) {
      Button demoButton = new Button(getTranslation("login.demo_user"), VaadinIcon.LOCK.create(),
              e -> submitDemoLogin());
      demoButton.addClassName("auth-primary-btn");
      demoButton.setWidthFull();
      primaryStage.add(demoButton);
    }

    Button otherOptions = new Button(getTranslation("login.other_options"),
            e -> showForm(true));
    otherOptions.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    primaryStage.add(otherOptions);

    // Without a demo account the form IS the primary option
    if (!demoAvailable) {
      primaryStage.addClassName("stage-hidden");
    }

    // ── Stage 2: username/password form ──────────────────────────────
    formStage.addClassName("auth-stage");
    formStage.setId("login-form-stage");

    Button backButton = new Button(getTranslation("login.back"),
            VaadinIcon.ARROW_CIRCLE_LEFT_O.create(), e -> showForm(false));
    backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    backButton.getStyle().set("align-self", "flex-start");
    if (!demoAvailable) {
      backButton.setVisible(false);
    }

    Span demoHint = new Span(getTranslation("login.demo_hint"));
    demoHint.addClassName("auth-footnote");
    demoHint.setVisible(demoAvailable);

    formStage.add(backButton, loginForm, registerLink, demoHint);
    if (demoAvailable) {
      formStage.addClassName("stage-hidden");
    }

    Div card = new Div(brand, primaryStage, formStage);
    card.addClassName("auth-card");
    add(card);
  }

  private void showForm(boolean form) {
    if (form) {
      formStage.removeClassName("stage-hidden");
      primaryStage.addClassName("stage-hidden");
    } else {
      formStage.addClassName("stage-hidden");
      primaryStage.removeClassName("stage-hidden");
    }
  }

  /** Reveals the form, fills demo credentials and submits (client fill via JS). */
  private void submitDemoLogin() {
    showForm(true);
    loginForm.getElement().executeJs(
        "let attempts = 60;" +
        "const tryFill = () => {" +
        "  const u = this.querySelector('input[name=\"username\"]');" +
        "  const p = this.querySelector('input[name=\"password\"]');" +
        "  const b = this.querySelector('vaadin-button[slot=\"submit\"]');" +
        "  if (u && p && b) {" +
        "    u.value = 'demo';" +
        "    p.value = 'demo123';" +
        "    u.dispatchEvent(new Event('input', {bubbles: true}));" +
        "    p.dispatchEvent(new Event('input', {bubbles: true}));" +
        "    b.click();" +
        "  } else if (attempts-- > 0) {" +
        "    requestAnimationFrame(tryFill);" +
        "  }" +
        "};" +
        "tryFill();"
    );
  }

  private void configureLayout() {
    addClassName("auth-view");
    setSizeFull();
    setAlignItems(Alignment.CENTER);
    setJustifyContentMode(JustifyContentMode.CENTER);
    setPadding(true);
    setSpacing(false);
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
    registerLink.getStyle().set("align-self", "center");
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    UI ui = event.getUI();

    // Apply last used locale (fallbacks to English) and theme from cookie
    ThemePreference.applyLocaleFromCookie(ui);
    ThemePreference.applyThemeFromCookie(ui);

    // Handle login error (?error): jump straight to the form so it's visible
    boolean error = event.getLocation().getQueryParameters().getParameters().containsKey("error");
    loginForm.setError(error);
    if (error) {
      showForm(true);
    }

    // Feature toggle: registration enabled
    registerLink.setVisible(globalSettingService.isRegistrationEnabled());
  }
}
