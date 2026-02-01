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
    private final AccountService accountService;
    private final GlobalSettingService globalSettingService;

    private final TextField usernameField = new TextField("Username");
    private final EmailField emailField = new EmailField("Email");
    private final PasswordField passwordField = new PasswordField("Password");
    private final PasswordField confirmPasswordField = new PasswordField("Confirm Password");
    private final TextField firstNameField = new TextField("First Name");
    private final TextField lastNameField = new TextField("Last Name");
    private final Button registerButton = new Button("Register", event -> register());

    public RegisterView(UserService userService, AccountService accountService, GlobalSettingService globalSettingService) {
        this.userService = userService;
        this.accountService = accountService;
        this.globalSettingService = globalSettingService;

        // Force English for unauthenticated views
        UI.getCurrent().setLocale(Locale.ENGLISH);
        VaadinSession.getCurrent().setLocale(Locale.ENGLISH);

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        setupUI();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!globalSettingService.isRegistrationEnabled()) {
            event.rerouteTo(LoginView.class);
            UI_Notification_Fix();
        }
    }

    private void UI_Notification_Fix() {
        getUI().ifPresent(ui -> ui.access(() -> {
            Notification n = Notification.show("Registration is currently disabled by the administrator.", 5000, Notification.Position.TOP_CENTER);
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }));
    }

    private void setupUI() {
        H2 title = new H2("Register for Cuenti");

        FormLayout formLayout = new FormLayout();
        formLayout.add(
            usernameField,
            emailField,
            firstNameField,
            lastNameField,
            passwordField,
            confirmPasswordField
        );
        formLayout.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("500px", 2)
        );

        registerButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        RouterLink loginLink = new RouterLink("Already have an account? Login here", LoginView.class);

        VerticalLayout content = new VerticalLayout(
            title,
            formLayout,
            registerButton,
            loginLink
        );
        content.setWidth("600px");
        content.setPadding(true);

        add(content);
    }

    private void register() {
        if (!globalSettingService.isRegistrationEnabled()) {
            showError("Registration is disabled.");
            return;
        }

        try {
            if (usernameField.isEmpty() || emailField.isEmpty() || 
                passwordField.isEmpty() || firstNameField.isEmpty() || 
                lastNameField.isEmpty()) {
                showError("All fields are required");
                return;
            }

            if (!passwordField.getValue().equals(confirmPasswordField.getValue())) {
                showError("Passwords do not match");
                return;
            }

            if (passwordField.getValue().length() < 6) {
                showError("Password must be at least 6 characters");
                return;
            }

            // Check if this will be the first user (admin)
            boolean isFirstUser = userService.findAll().isEmpty();

            var user = userService.registerUser(
                usernameField.getValue().trim(),
                emailField.getValue().trim(),
                passwordField.getValue(),
                firstNameField.getValue().trim(),
                lastNameField.getValue().trim()
            );

            Account defaultAccount = Account.builder()
                    .user(user)
                    .accountName("Main Account")
                    .accountType(Account.AccountType.CURRENT)
                    .startBalance(new BigDecimal("1000.00"))
                    .currency("EUR")
                    .build();
            accountService.saveAccount(defaultAccount);

            if (isFirstUser) {
                showSuccess("🎉 Registration successful! You are the first user and have been granted administrator privileges. Please login.");
            } else {
                showSuccess("Registration successful! Please login.");
            }

            getUI().ifPresent(ui -> ui.navigate(LoginView.class));

        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        } catch (Exception e) {
            log.error("Registration error", e);
            showError("Registration failed. Please try again.");
        }
    }

    private void showError(String message) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }
}
