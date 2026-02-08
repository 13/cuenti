package com.cuenti.homebanking.views.settings;

import com.cuenti.homebanking.data.Currency;
import com.cuenti.homebanking.data.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.services.*;
import com.cuenti.homebanking.views.dashboard.DashboardView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

/**
 * User settings view for profile, preferences, password, and danger zone.
 */
@PageTitle("Settings")
@Route("settings")
@Menu(order = 10, icon = LineAwesomeIconUrl.COG_SOLID)
@PermitAll
public class SettingsView extends VerticalLayout {

    private final UserService userService;
    private final CurrencyService currencyService;
    private final ProfileCleanupService profileCleanupService;
    private final SecurityUtils securityUtils;
    private final User currentUser;

    public SettingsView(UserService userService, CurrencyService currencyService,
                        ProfileCleanupService profileCleanupService,
                        SecurityUtils securityUtils) {
        this.userService = userService;
        this.currencyService = currencyService;
        this.profileCleanupService = profileCleanupService;
        this.securityUtils = securityUtils;

        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));
        this.currentUser = userService.findByUsername(username);

        setSpacing(true);
        setPadding(true);
        setMaxWidth("800px");
        getStyle().set("margin", "0 auto");

        setupUI();
    }

    private void setupUI() {
        H2 title = new H2(getTranslation("settings.title"));

        add(title);
        add(createUserProfileSection());
        add(createPreferencesSection());
        add(createPasswordSection());
        add(createDangerZoneSection());
    }

    private VerticalLayout createUserProfileSection() {
        VerticalLayout section = createSection(getTranslation("settings.user_profile"));

        TextField firstName = new TextField(getTranslation("settings.first_name"));
        firstName.setValue(currentUser.getFirstName() != null ? currentUser.getFirstName() : "");
        firstName.setWidthFull();

        TextField lastName = new TextField(getTranslation("settings.last_name"));
        lastName.setValue(currentUser.getLastName() != null ? currentUser.getLastName() : "");
        lastName.setWidthFull();

        EmailField email = new EmailField(getTranslation("settings.email"));
        email.setValue(currentUser.getEmail() != null ? currentUser.getEmail() : "");
        email.setWidthFull();

        Button updateButton = new Button(getTranslation("settings.update_profile"), e -> {
            userService.updateUserInfo(currentUser, firstName.getValue(), lastName.getValue(), email.getValue());
            Notification.show(getTranslation("settings.profile_updated"))
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        updateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        section.add(firstName, lastName, email, updateButton);
        return section;
    }

    private VerticalLayout createPreferencesSection() {
        VerticalLayout section = createSection(getTranslation("settings.localization_title"));

        ComboBox<Currency> currencyComboBox = new ComboBox<>(getTranslation("settings.default_currency"));
        currencyComboBox.setItems(currencyService.getAllCurrencies());
        currencyComboBox.setItemLabelGenerator(c -> c.getCode() + " - " + c.getName());
        currencyComboBox.setWidthFull();
        currencyService.getAllCurrencies().stream()
                .filter(c -> c.getCode().equals(currentUser.getDefaultCurrency()))
                .findFirst()
                .ifPresent(currencyComboBox::setValue);

        ComboBox<String> localeComboBox = new ComboBox<>(getTranslation("settings.localization"));
        localeComboBox.setItems("de-DE", "en-US", "en-GB");
        localeComboBox.setItemLabelGenerator(l -> {
            switch (l) {
                case "de-DE": return "Deutsch (Deutschland)";
                case "en-US": return "English (US)";
                case "en-GB": return "English (UK)";
                default: return l;
            }
        });
        localeComboBox.setValue(currentUser.getLocale() != null ? currentUser.getLocale() : "en-US");
        localeComboBox.setWidthFull();

        Checkbox darkMode = new Checkbox("Dark Mode");
        darkMode.setValue(currentUser.isDarkMode());

        Button saveButton = new Button(getTranslation("settings.save"), e -> {
            if (currencyComboBox.getValue() != null) {
                userService.updateDefaultCurrency(currentUser, currencyComboBox.getValue().getCode());
            }
            userService.updateLocale(currentUser, localeComboBox.getValue());
            userService.updateDarkMode(currentUser, darkMode.getValue());
            Notification.show(getTranslation("settings.saved"))
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        section.add(currencyComboBox, localeComboBox, darkMode, saveButton);
        return section;
    }

    private VerticalLayout createPasswordSection() {
        VerticalLayout section = createSection(getTranslation("settings.security_password"));

        PasswordField currentPassword = new PasswordField(getTranslation("settings.current_password"));
        currentPassword.setWidthFull();

        PasswordField newPassword = new PasswordField(getTranslation("settings.new_password"));
        newPassword.setWidthFull();

        PasswordField confirmPassword = new PasswordField(getTranslation("settings.confirm_new_password"));
        confirmPassword.setWidthFull();

        Button changeButton = new Button(getTranslation("settings.change_password"), e -> {
            if (!newPassword.getValue().equals(confirmPassword.getValue())) {
                Notification.show(getTranslation("settings.passwords_not_match"))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            if (newPassword.getValue().length() < 6) {
                Notification.show("Password must be at least 6 characters")
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            try {
                userService.changePassword(currentUser, currentPassword.getValue(), newPassword.getValue());
                Notification.show(getTranslation("settings.password_updated"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                currentPassword.clear();
                newPassword.clear();
                confirmPassword.clear();
            } catch (Exception ex) {
                Notification.show(getTranslation("settings.incorrect_old_password"))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        changeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        section.add(currentPassword, newPassword, confirmPassword, changeButton);
        return section;
    }

    private VerticalLayout createDangerZoneSection() {
        VerticalLayout section = createSection("⚠️ Danger Zone");
        section.getStyle().set("border", "2px solid var(--lumo-error-color)");

        Paragraph warning = new Paragraph(
                "This action will permanently delete ALL your accounts, transactions, scheduled entries, " +
                "assets, categories, payees, tags, and currencies. This cannot be undone!");
        warning.getStyle()
                .set("color", "var(--lumo-error-text-color)")
                .set("background-color", "var(--lumo-error-color-10pct)")
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-m)");

        Button cleanupButton = new Button("Clean All Profile Data", VaadinIcon.TRASH.create(), e -> {
            Dialog confirmDialog = new Dialog();
            confirmDialog.setHeaderTitle("⚠️ Confirm Data Wipe");
            confirmDialog.setWidth("500px");

            confirmDialog.add(new Paragraph(
                    "Are you ABSOLUTELY sure? This will delete everything and you will start with a fresh empty profile. " +
                    "Type 'DELETE' to confirm."));

            TextField confirmField = new TextField("Type DELETE to confirm");
            confirmField.setWidthFull();
            confirmDialog.add(confirmField);

            Button deleteBtn = new Button("Yes, Delete Everything", event -> {
                if ("DELETE".equals(confirmField.getValue())) {
                    profileCleanupService.cleanupUserData(currentUser);
                    Notification.show("Profile data wiped successfully")
                            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    confirmDialog.close();
                    UI.getCurrent().navigate(DashboardView.class);
                } else {
                    Notification.show("Please type DELETE to confirm")
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

            Button cancelBtn = new Button("Cancel", event -> confirmDialog.close());
            confirmDialog.getFooter().add(cancelBtn, deleteBtn);
            confirmDialog.open();
        });
        cleanupButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

        section.add(warning, cleanupButton);
        return section;
    }

    private VerticalLayout createSection(String title) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(true);
        section.setSpacing(true);
        section.getStyle()
                .set("background-color", "var(--lumo-base-color)")
                .set("border-radius", "16px")
                .set("box-shadow", "var(--lumo-box-shadow-s)");

        H3 sectionTitle = new H3(title);
        sectionTitle.getStyle().set("margin-top", "0");
        section.add(sectionTitle);

        return section;
    }
}
