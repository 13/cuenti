package com.cuenti.homebanking.views;

import com.cuenti.homebanking.service.UserService;
import com.cuenti.homebanking.service.AccountService;
import com.cuenti.homebanking.service.GlobalSettingService;
import com.cuenti.homebanking.model.Account;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.Lumo;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Registration view for new users.
 * Default language set to English.
 */
@Route("register")
@PageTitle("Register | Cuenti")
@AnonymousAllowed
@Slf4j
public class RegisterView extends VerticalLayout implements BeforeEnterObserver {

    private final UserService userService;
    private final GlobalSettingService globalSettingService;

    private final TextField usernameField = new TextField();
    private final EmailField emailField = new EmailField();
    private final PasswordField passwordField = new PasswordField();
    private final PasswordField confirmPasswordField = new PasswordField();
    private final TextField firstNameField = new TextField();
    private final TextField lastNameField = new TextField();

    public RegisterView(UserService userService,
                        GlobalSettingService globalSettingService) {
        this.userService = userService;
        this.globalSettingService = globalSettingService;

        configureLayout();
        add(buildContent());
    }

    private void configureLayout() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.START);
        setPadding(true);
        setSpacing(true);

        getStyle().set("overflow-y", "auto");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        UI ui = event.getUI();

        ui.setLocale(Locale.ENGLISH);
        ui.getElement().setAttribute("theme", Lumo.DARK);

        if (!globalSettingService.isRegistrationEnabled()) {
            Notification n = Notification.show(
                "Registration is currently disabled by the administrator.",
                4000,
                Notification.Position.TOP_CENTER
            );
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);

            event.rerouteTo(LoginView.class);
        }
    }

    private VerticalLayout buildContent() {
        H2 title = new H2("Register for Cuenti");

        configureFields();

        FormLayout form = new FormLayout(
            usernameField,
            emailField,
            firstNameField,
            lastNameField,
            passwordField,
            confirmPasswordField
        );

        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("500px", 2)
        );

        Button registerButton = new Button("Register", e -> register());
        registerButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        registerButton.setWidthFull();

        RouterLink loginLink =
            new RouterLink("Already have an account? Login here", LoginView.class);

        VerticalLayout content = new VerticalLayout(
            title,
            form,
            registerButton,
            loginLink
        );

        content.setMaxWidth("420px");
        content.setWidthFull();
        content.setAlignItems(Alignment.STRETCH);
        content.setPadding(true);

        return content;
    }

    private void configureFields() {
        usernameField.setLabel("Username");
        emailField.setLabel("Email");
        passwordField.setLabel("Password");
        confirmPasswordField.setLabel("Confirm Password");
        firstNameField.setLabel("First Name");
        lastNameField.setLabel("Last Name");

        emailField.setClearButtonVisible(true);
    }

    private void register() {
        try {
            validateForm();

            userService.registerUser(
                usernameField.getValue().trim(),
                emailField.getValue().trim(),
                passwordField.getValue(),
                firstNameField.getValue().trim(),
                lastNameField.getValue().trim()
            );

            Notification success = Notification.show(
                "Registration successful! Please login.",
                3000,
                Notification.Position.TOP_CENTER
            );
            success.addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            getUI().ifPresent(ui -> ui.navigate(LoginView.class));

        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        } catch (Exception e) {
            log.error("Registration error", e);
            showError("Registration failed. Please try again.");
        }
    }

    private void validateForm() {
        if (usernameField.isEmpty() || emailField.isEmpty()
            || passwordField.isEmpty() || confirmPasswordField.isEmpty()
            || firstNameField.isEmpty() || lastNameField.isEmpty()) {
            throw new IllegalArgumentException("All fields are required");
        }

        if (!passwordField.getValue().equals(confirmPasswordField.getValue())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        if (passwordField.getValue().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
    }

    private void showError(String message) {
        Notification n = Notification.show(
            message,
            3000,
            Notification.Position.TOP_CENTER
        );
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
