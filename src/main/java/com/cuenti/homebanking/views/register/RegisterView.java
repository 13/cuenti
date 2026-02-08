package com.cuenti.homebanking.views.register;

import com.cuenti.homebanking.services.GlobalSettingService;
import com.cuenti.homebanking.services.UserService;
import com.cuenti.homebanking.views.login.LoginView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.Lumo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Register view for creating new accounts.
 */
@Route("register")
@PageTitle("Register | Cuenti")
@AnonymousAllowed
public class RegisterView extends VerticalLayout implements BeforeEnterObserver {

    private static final Logger log = LoggerFactory.getLogger(RegisterView.class);

    private final UserService userService;
    private final GlobalSettingService globalSettingService;

    private final TextField firstName = new TextField();
    private final TextField lastName = new TextField();
    private final TextField username = new TextField();
    private final EmailField email = new EmailField();
    private final PasswordField password = new PasswordField();
    private final PasswordField confirmPassword = new PasswordField();
    private final Button submit = new Button();

    private final Binder<RegistrationModel> binder = new Binder<>(RegistrationModel.class);

    public RegisterView(UserService userService, GlobalSettingService globalSettingService) {
        this.userService = userService;
        this.globalSettingService = globalSettingService;

        configureLayout();
        configureForm();

        H2 title = new H2(t("register.title", "Register"));

        RouterLink loginLink = new RouterLink(
                t("register.already_registered", "Already registered? Login"),
                LoginView.class
        );

        HorizontalLayout footer = new HorizontalLayout(loginLink);
        footer.setWidthFull();
        footer.setJustifyContentMode(JustifyContentMode.CENTER);

        add(title, createFormLayout(), footer);
    }

    private void configureLayout() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.START);
        setPadding(true);
        setSpacing(true);
    }

    private void configureForm() {
        username.setLabel(t("register.username", "Username"));
        email.setLabel(t("register.email", "Email"));
        firstName.setLabel(t("register.firstname", "First name"));
        lastName.setLabel(t("register.lastname", "Last name"));
        password.setLabel(t("register.password", "Password"));
        confirmPassword.setLabel(t("register.confirm_password", "Confirm password"));

        submit.setText(t("register.submit", "Register"));
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        binder.forField(firstName)
                .asRequired(t("register.validation.firstname_required", "First name is required"))
                .withValidator(new StringLengthValidator(
                        t("register.validation.name_length", "Must be between 1 and 50 characters"), 1, 50))
                .bind(RegistrationModel::getFirstName, RegistrationModel::setFirstName);

        binder.forField(lastName)
                .asRequired(t("register.validation.lastname_required", "Last name is required"))
                .withValidator(new StringLengthValidator(
                        t("register.validation.name_length", "Must be between 1 and 50 characters"), 1, 50))
                .bind(RegistrationModel::getLastName, RegistrationModel::setLastName);

        binder.forField(username)
                .asRequired(t("register.validation.username_required", "Username is required"))
                .withValidator(new StringLengthValidator(
                        t("register.validation.username_length", "Username must be between 3 and 50 characters"), 3, 50))
                .bind(RegistrationModel::getUsername, RegistrationModel::setUsername);

        binder.forField(email)
                .asRequired(t("register.validation.email_required", "Email is required"))
                .bind(RegistrationModel::getEmail, RegistrationModel::setEmail);

        binder.forField(password)
                .asRequired(t("register.validation.password_required", "Password is required"))
                .withValidator(new StringLengthValidator(
                        t("register.validation.password_length", "Password must be between 6 and 128 characters"), 6, 128))
                .bind(RegistrationModel::getPassword, RegistrationModel::setPassword);

        binder.forField(confirmPassword)
                .asRequired(t("register.validation.password_required", "Password is required"))
                .withValidator(value -> value != null && value.equals(password.getValue()),
                        t("register.validation.password_mismatch", "Passwords do not match"))
                .bind(model -> null, (model, value) -> {});

        submit.addClickListener(e -> {
            RegistrationModel model = new RegistrationModel();
            try {
                binder.writeBean(model);
                handleRegister(model);
            } catch (ValidationException ex) {
                // Validation errors shown on form
            }
        });
    }

    private FormLayout createFormLayout() {
        FormLayout form = new FormLayout(
                username, email,
                firstName, lastName,
                password, confirmPassword,
                submit
        );

        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );

        form.setColspan(submit, 2);
        form.setWidth("min(720px, 90%)");
        form.getStyle().set("margin", "0 auto");

        return form;
    }

    private void handleRegister(RegistrationModel model) {
        try {
            userService.registerUser(
                    model.getUsername().trim(),
                    model.getEmail().trim(),
                    model.getPassword(),
                    model.getFirstName().trim(),
                    model.getLastName().trim()
            );

            Notification success = Notification.show(
                    t("register.success", "Registration successful! Please login."),
                    3000,
                    Notification.Position.TOP_CENTER
            );
            success.addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            UI.getCurrent().navigate(LoginView.class);

        } catch (IllegalArgumentException e) {
            Notification n = Notification.show(e.getMessage(), 3000, Notification.Position.TOP_CENTER);
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);

        } catch (Exception e) {
            log.error("Registration error", e);
            Notification n = Notification.show(
                    t("register.error", "Registration failed. Please try again."),
                    3000,
                    Notification.Position.TOP_CENTER
            );
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        UI ui = event.getUI();
        ui.setLocale(Locale.ENGLISH);
        VaadinSession.getCurrent().setLocale(Locale.ENGLISH);
        ui.getElement().setAttribute("theme", Lumo.DARK);

        if (!globalSettingService.isRegistrationEnabled()) {
            event.forwardTo(LoginView.class);
        }
    }

    public static class RegistrationModel {
        private String firstName;
        private String lastName;
        private String username;
        private String email;
        private String password;

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    private String t(String key, String defaultValue) {
        Locale locale = VaadinSession.getCurrent() != null
                ? VaadinSession.getCurrent().getLocale()
                : Locale.ENGLISH;

        try {
            ResourceBundle bundle = ResourceBundle.getBundle("messages", locale);
            return bundle.containsKey(key) ? bundle.getString(key) : defaultValue;
        } catch (MissingResourceException e) {
            return defaultValue;
        }
    }
}
