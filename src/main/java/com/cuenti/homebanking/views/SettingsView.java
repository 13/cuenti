package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.Account;
import com.cuenti.homebanking.model.Currency;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.*;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.PermitAll;

import java.io.ByteArrayInputStream;
import java.util.List;

@Route(value = "settings", layout = MainLayout.class)
@PageTitle("Settings | Cuenti")
@PermitAll
public class SettingsView extends VerticalLayout implements BeforeEnterObserver {

    private final UserService userService;
    private final CurrencyService currencyService;
    private final AccountService accountService;
    private final XhbImportService xhbImportService;
    private final XhbExportService xhbExportService;
    private final TradeRepublicImportService tradeRepublicImportService;
    private final JsonExportImportService jsonExportImportService;
    private final GlobalSettingService globalSettingService;
    private final ProfileCleanupService profileCleanupService;
    private final SecurityUtils securityUtils;
    private final User currentUser;

    private final Div container = new Div();
    private final Grid<User> userGrid = new Grid<>(User.class, false);

    public SettingsView(UserService userService, CurrencyService currencyService, 
                        AccountService accountService,
                        XhbImportService xhbImportService, XhbExportService xhbExportService,
                        TradeRepublicImportService tradeRepublicImportService,
                        JsonExportImportService jsonExportImportService,
                        GlobalSettingService globalSettingService, ProfileCleanupService profileCleanupService,
                        SecurityUtils securityUtils) {
        this.userService = userService;
        this.currencyService = currencyService;
        this.accountService = accountService;
        this.xhbImportService = xhbImportService;
        this.xhbExportService = xhbExportService;
        this.tradeRepublicImportService = tradeRepublicImportService;
        this.jsonExportImportService = jsonExportImportService;
        this.globalSettingService = globalSettingService;
        this.profileCleanupService = profileCleanupService;
        this.securityUtils = securityUtils;

        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));
        this.currentUser = userService.findByUsername(username);

        // Remove setSizeFull() to allow natural vertical growth and scrolling
        setWidthFull();
        setAlignItems(Alignment.CENTER);
        setPadding(true);
        setSpacing(true);
        getStyle().set("background-color", "var(--lumo-contrast-5pct)");

        container.setWidthFull();
        container.setMaxWidth("800px");
        container.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-l)");
        
        // Ensure container doesn't force a height, allowing scrolling handled by AppLayout
        add(container);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        container.removeAll();
        String section = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("section", List.of("user")).get(0);

        switch (section) {
            case "admin":
                if (currentUser.getRoles().contains("ROLE_ADMIN")) showAdministration();
                else showUserSettings();
                break;
            case "import-export":
                showImportExport();
                break;
            case "user":
            default:
                showUserSettings();
                break;
        }
    }

    private void showAdministration() {
        Div card = createCard();
        card.add(new H2(getTranslation("settings.administration")));
        
        Checkbox registrationToggle = new Checkbox(getTranslation("settings.enable_registration"));
        registrationToggle.setValue(globalSettingService.isRegistrationEnabled());
        registrationToggle.addValueChangeListener(e -> globalSettingService.setRegistrationEnabled(e.getValue()));

        Checkbox apiToggle = new Checkbox(getTranslation("settings.enable_api"));
        apiToggle.setValue(globalSettingService.isApiEnabled());
        apiToggle.addValueChangeListener(e -> globalSettingService.setApiEnabled(e.getValue()));

        Button addUserButton = new Button(getTranslation("settings.add_new_user"), VaadinIcon.PLUS.create(), e -> openAddUserDialog());
        addUserButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        card.add(new HorizontalLayout(registrationToggle, apiToggle, addUserButton));

        Paragraph userManagementTitle = new Paragraph(getTranslation("settings.user_management"));
        userManagementTitle.getStyle().set("font-weight", "bold").set("margin-top", "var(--lumo-space-m)");
        
        userGrid.removeAllColumns();
        userGrid.addColumn(User::getUsername).setHeader(getTranslation("login.username")).setAutoWidth(true);
        userGrid.addColumn(User::getEmail).setHeader("Email").setAutoWidth(true);
        
        userGrid.addComponentColumn(u -> {
            Checkbox isAdmin = new Checkbox();
            isAdmin.setValue(u.getRoles().contains("ROLE_ADMIN"));
            isAdmin.setEnabled(!u.getUsername().equals("demo"));
            isAdmin.addValueChangeListener(e -> {
                if (e.getValue()) u.getRoles().add("ROLE_ADMIN");
                else u.getRoles().remove("ROLE_ADMIN");
                userService.saveUser(u);
            });
            return isAdmin;
        }).setHeader(getTranslation("settings.admin"));

        userGrid.addComponentColumn(u -> {
            Checkbox isEnabled = new Checkbox();
            isEnabled.setValue(u.getEnabled());
            isEnabled.setEnabled(!u.getUsername().equals("demo"));
            isEnabled.addValueChangeListener(e -> userService.setUserEnabled(u, e.getValue()));
            return isEnabled;
        }).setHeader(getTranslation("settings.enabled"));

        userGrid.addComponentColumn(u -> {
            Button resetBtn = new Button(VaadinIcon.KEY.create(), e -> openAdminPasswordDialog(u));
            resetBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            resetBtn.setTooltipText(getTranslation("settings.force_password_change"));
            return resetBtn;
        }).setHeader(getTranslation("settings.pass"));

        userGrid.addComponentColumn(u -> {
            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e -> openDeleteUserDialog(u));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            deleteBtn.setTooltipText(getTranslation("settings.delete_user"));
            // Don't allow deleting the current user or the demo user
            deleteBtn.setEnabled(!u.getUsername().equals(currentUser.getUsername()) && !u.getUsername().equals("demo"));
            return deleteBtn;
        }).setHeader(getTranslation("settings.delete"));

        userGrid.setItems(userService.findAll());
        userGrid.setAllRowsVisible(true);
        
        card.add(userManagementTitle, userGrid);
        container.add(card);
    }

    private void showUserSettings() {
        Div card = createCard();
        card.add(new H2(getTranslation("settings.user_profile")));

        TextField firstName = new TextField(getTranslation("settings.first_name"));
        firstName.setValue(currentUser.getFirstName());
        TextField lastName = new TextField(getTranslation("settings.last_name"));
        lastName.setValue(currentUser.getLastName());
        EmailField email = new EmailField(getTranslation("settings.email"));
        email.setValue(currentUser.getEmail());

        Button updateInfo = new Button(getTranslation("settings.update_profile"), e -> {
            userService.updateUserInfo(currentUser, firstName.getValue(), lastName.getValue(), email.getValue());
            Notification.show(getTranslation("settings.profile_updated"));
        });
        updateInfo.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        card.add(new FormLayout(firstName, lastName, email), updateInfo);

        // Localization settings
        Div localizationCard = createCard();
        localizationCard.add(new H3(getTranslation("settings.localization_title")));

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
        localeComboBox.setItemLabelGenerator(l -> l.equals("de-DE") ? "Deutsch" : "English");
        localeComboBox.setValue(currentUser.getLocale());
        localeComboBox.setWidthFull();

        Button saveLocalization = new Button(getTranslation("settings.save"), e -> {
            userService.updateDefaultCurrency(currentUser, currencyComboBox.getValue().getCode());
            userService.updateLocale(currentUser, localeComboBox.getValue());
            Notification.show(getTranslation("settings.saved"));
            UI.getCurrent().getPage().reload();
        });
        saveLocalization.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        localizationCard.add(currencyComboBox, localeComboBox, saveLocalization);

        // Password change
        Div passCard = createCard();
        passCard.add(new H3(getTranslation("settings.security_password")));
        PasswordField oldPass = new PasswordField(getTranslation("settings.current_password"));
        PasswordField newPass = new PasswordField(getTranslation("settings.new_password"));
        PasswordField confirmPass = new PasswordField(getTranslation("settings.confirm_new_password"));

        Button changePass = new Button(getTranslation("settings.change_password"), e -> {
            if (userService.checkPassword(currentUser, oldPass.getValue())) {
                if (newPass.getValue().equals(confirmPass.getValue()) && newPass.getValue().length() >= 6) {
                    userService.updatePassword(currentUser, newPass.getValue());
                    Notification.show(getTranslation("settings.saved"));
                    oldPass.clear(); newPass.clear(); confirmPass.clear();
                } else Notification.show(getTranslation("settings.passwords_not_match"), 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
            } else Notification.show(getTranslation("settings.incorrect_old_password"), 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
        });
        changePass.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        passCard.add(new VerticalLayout(oldPass, newPass, confirmPass, changePass));

        Div dangerCard = createCard();
        dangerCard.getStyle().set("border", "1px solid var(--lumo-error-color)");
        dangerCard.add(new H3("Danger Zone"));
        dangerCard.add(new Paragraph("This action will permanently delete all your accounts, transactions, scheduled entries, and holdings. This cannot be undone."));
        
        Button cleanupButton = new Button("Clean All Profile Data", VaadinIcon.TRASH.create(), e -> {
            Dialog confirmDialog = new Dialog();
            confirmDialog.setHeaderTitle("Confirm Data Wipe");
            confirmDialog.add(new Paragraph("Are you sure you want to permanently delete all your data? You will start with a fresh empty profile."));
            
            Button deleteBtn = new Button("Yes, Delete Everything", event -> {
                profileCleanupService.cleanupUserData(currentUser);
                Notification.show("Profile data wiped successfully");
                confirmDialog.close();
                UI.getCurrent().navigate(DashboardView.class);
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
            
            Button cancelBtn = new Button("Cancel", event -> confirmDialog.close());
            confirmDialog.getFooter().add(cancelBtn, deleteBtn);
            confirmDialog.open();
        });
        cleanupButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        dangerCard.add(cleanupButton);

        container.add(card, localizationCard, passCard, dangerCard);
    }

    private void showImportExport() {
        // --- Homebank Import/Export ---
        Div card = createCard();
        card.add(new H2(getTranslation("settings.import_export_title")));
        
        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".xhb");
        upload.setUploadButton(new Button(getTranslation("settings.import"), VaadinIcon.UPLOAD.create()));
        upload.addSucceededListener(e -> {
            try {
                xhbImportService.importXhb(buffer.getInputStream(), currentUser);
                Notification.show(getTranslation("settings.import_success"));
            } catch (Exception ex) {
                Notification.show(getTranslation("settings.import_failed", ex.getMessage()), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Button export = new Button(getTranslation("settings.export"), VaadinIcon.DOWNLOAD.create());
        Anchor anchor = new Anchor(new StreamResource("export.xhb", () -> {
            try {
                return new ByteArrayInputStream(xhbExportService.exportXhb(currentUser));
            } catch (Exception ex) {
                return new ByteArrayInputStream(new byte[0]);
            }
        }), "");
        anchor.add(export);

        card.add(new Paragraph(getTranslation("settings.data_desc")), new HorizontalLayout(upload, anchor));
        container.add(card);

        // --- Trade Republic Import ---
        Div trCard = createCard();
        trCard.add(new H2("Trade Republic Import"));
        trCard.add(new Paragraph("Import your Trade Republic CSV statement. Select target accounts for Cash and Assets."));

        ComboBox<Account> cashAccountCombo = new ComboBox<>("Select Target Cash Account");
        cashAccountCombo.setItems(accountService.getAccountsByUser(currentUser));
        cashAccountCombo.setItemLabelGenerator(Account::getAccountName);
        cashAccountCombo.setWidthFull();

        ComboBox<Account> assetAccountCombo = new ComboBox<>("Select Target Asset Account");
        assetAccountCombo.setItems(accountService.getAccountsByUser(currentUser));
        assetAccountCombo.setItemLabelGenerator(Account::getAccountName);
        assetAccountCombo.setWidthFull();

        MemoryBuffer trBuffer = new MemoryBuffer();
        Upload trUpload = new Upload(trBuffer);
        trUpload.setAcceptedFileTypes(".csv");
        trUpload.setUploadButton(new Button("Import Trade Republic CSV", VaadinIcon.FILE_TEXT.create()));
        trUpload.addSucceededListener(e -> {
            if (cashAccountCombo.isEmpty() || assetAccountCombo.isEmpty()) {
                Notification.show("Please select both target accounts first", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            try {
                tradeRepublicImportService.importCsv(trBuffer.getInputStream(), cashAccountCombo.getValue(), assetAccountCombo.getValue());
                Notification.show("Trade Republic import successful!");
            } catch (Exception ex) {
                Notification.show("Import failed: " + ex.getMessage(), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        trCard.add(cashAccountCombo, assetAccountCombo, trUpload);
        container.add(trCard);

        // --- JSON Export/Import ---
        Div jsonCard = createCard();
        jsonCard.add(new H2(getTranslation("settings.json_backup_restore")));
        jsonCard.add(new Paragraph(getTranslation("settings.json_desc")));

        // Export
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("cuenti_export_%s_%s.json", currentUser.getUsername(), timestamp);

        com.vaadin.flow.server.StreamResource jsonResource = new com.vaadin.flow.server.StreamResource(filename, () -> {
            try {
                java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
                jsonExportImportService.exportUserData(currentUser, outputStream);
                return new ByteArrayInputStream(outputStream.toByteArray());
            } catch (Exception ex) {
                return new ByteArrayInputStream(new byte[0]);
            }
        });
        jsonResource.setContentType("application/json");

        Button jsonExport = new Button(getTranslation("settings.export_json"), VaadinIcon.DOWNLOAD.create());
        jsonExport.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Anchor jsonAnchor = new Anchor(jsonResource, "");
        jsonAnchor.getElement().setAttribute("download", true);
        jsonAnchor.add(jsonExport);

        // Import
        MemoryBuffer jsonBuffer = new MemoryBuffer();
        Upload jsonUpload = new Upload(jsonBuffer);
        jsonUpload.setAcceptedFileTypes("application/json", ".json");
        jsonUpload.setUploadButton(new Button(getTranslation("settings.import_json"), VaadinIcon.UPLOAD.create()));
        jsonUpload.setMaxFiles(1);
        jsonUpload.setMaxFileSize(50 * 1024 * 1024); // 50MB max

        jsonUpload.addSucceededListener(e -> {
            try {
                jsonExportImportService.importUserData(currentUser, jsonBuffer.getInputStream());
                Notification.show(getTranslation("settings.json_import_success"), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                UI.getCurrent().getPage().reload();
            } catch (Exception ex) {
                Notification.show(getTranslation("settings.json_import_failed", ex.getMessage()), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Paragraph jsonWarning = new Paragraph(getTranslation("settings.json_warning"));
        jsonWarning.getStyle()
                .set("color", "var(--lumo-error-text-color)")
                .set("background-color", "var(--lumo-error-color-10pct)")
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("margin-top", "var(--lumo-space-m)");

        jsonCard.add(new HorizontalLayout(jsonAnchor, jsonUpload), jsonWarning);
        container.add(jsonCard);
    }

    private void openAddUserDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("settings.add_new_user"));

        TextField username = new TextField(getTranslation("login.username"));
        EmailField email = new EmailField("Email");
        TextField firstName = new TextField(getTranslation("settings.first_name"));
        TextField lastName = new TextField(getTranslation("settings.last_name"));
        PasswordField password = new PasswordField(getTranslation("login.password"));
        PasswordField confirmPassword = new PasswordField(getTranslation("settings.confirm_password"));

        FormLayout form = new FormLayout(username, email, firstName, lastName, password, confirmPassword);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));
        dialog.add(form);

        Button save = new Button(getTranslation("settings.create_user"), e -> {
            try {
                if (username.isEmpty() || email.isEmpty() || password.isEmpty() || firstName.isEmpty() || lastName.isEmpty()) {
                    Notification.show(getTranslation("settings.all_fields_required"), 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }
                if (!password.getValue().equals(confirmPassword.getValue())) {
                    Notification.show(getTranslation("settings.passwords_not_match"), 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }
                if (password.getValue().length() < 6) {
                    Notification.show(getTranslation("settings.passwords_not_match"), 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }

                userService.registerUser(username.getValue(), email.getValue(), password.getValue(), firstName.getValue(), lastName.getValue());
                userGrid.setItems(userService.findAll());
                Notification.show(getTranslation("settings.user_created"));
                dialog.close();
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancel = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private void openAdminPasswordDialog(User user) {
        Dialog d = new Dialog(getTranslation("settings.reset_password_for", user.getUsername()));
        PasswordField p1 = new PasswordField(getTranslation("settings.new_password"));
        PasswordField p2 = new PasswordField(getTranslation("settings.confirm_password"));
        Button save = new Button(getTranslation("settings.reset"), e -> {
            if (p1.getValue().equals(p2.getValue())) {
                userService.updatePassword(user, p1.getValue());
                d.close();
                Notification.show(getTranslation("settings.saved"));
            }
        });
        d.add(new VerticalLayout(p1, p2, save));
        d.open();
    }

    private void openDeleteUserDialog(User user) {
        Dialog confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle(getTranslation("settings.delete_user_confirm_title"));

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        Paragraph warningText = new Paragraph(String.format(
            getTranslation("settings.delete_user_warning",
                "Are you sure you want to delete user '%s'? This will permanently delete:"),
            user.getUsername()
        ));

        UnorderedList itemList = new UnorderedList(
            new ListItem(getTranslation("settings.delete_user_item1", "All accounts")),
            new ListItem(getTranslation("settings.delete_user_item2", "All transactions")),
            new ListItem(getTranslation("settings.delete_user_item3", "All holdings and assets")),
            new ListItem(getTranslation("settings.delete_user_item4", "All scheduled transactions")),
            new ListItem(getTranslation("settings.delete_user_item5", "All user settings and data"))
        );

        Paragraph dangerText = new Paragraph(getTranslation("settings.delete_user_danger", "This action cannot be undone!"));
        dangerText.getStyle()
                .set("color", "var(--lumo-error-text-color)")
                .set("font-weight", "bold");

        content.add(warningText, itemList, dangerText);
        confirmDialog.add(content);

        Button deleteBtn = new Button(getTranslation("settings.delete_user_confirm", "Yes, Delete User"), event -> {
            try {
                userService.deleteUser(user);
                userGrid.setItems(userService.findAll());
                Notification success = Notification.show(
                    getTranslation("settings.user_deleted", "User deleted successfully"),
                    3000,
                    Notification.Position.TOP_CENTER
                );
                success.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                confirmDialog.close();
            } catch (Exception ex) {
                Notification error = Notification.show(
                    getTranslation("settings.user_delete_failed", "Failed to delete user: ") + ex.getMessage(),
                    5000,
                    Notification.Position.TOP_CENTER
                );
                error.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        Button cancelBtn = new Button(getTranslation("dialog.cancel", "Cancel"), event -> confirmDialog.close());

        confirmDialog.getFooter().add(cancelBtn, deleteBtn);
        confirmDialog.open();
    }

    private Div createCard() {
        Div card = new Div();
        card.getStyle().set("background-color", "var(--lumo-base-color)").set("border-radius", "16px").set("padding", "var(--lumo-space-xl)").set("box-shadow", "var(--lumo-box-shadow-m)");
        return card;
    }
}
