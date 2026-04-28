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
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.html.*;
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

        setWidthFull();
        setAlignItems(Alignment.CENTER);
        setPadding(false);
        setSpacing(false);
        getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("padding", "var(--lumo-space-m)")
                .set("overflow-y", "auto");

        container.setWidthFull();
        container.setMaxWidth("860px");
        container.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-m)");

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
        // ── Global toggles card ───────────────────────────────────────
        Div toggleCard = createCard();
        toggleCard.add(cardHeader(VaadinIcon.COG, getTranslation("settings.administration"),
                getTranslation("settings.administration_desc", "Global application settings"), "var(--lumo-primary-color)"));

        Checkbox registrationToggle = new Checkbox(getTranslation("settings.enable_registration"));
        registrationToggle.setValue(globalSettingService.isRegistrationEnabled());
        registrationToggle.addValueChangeListener(e -> globalSettingService.setRegistrationEnabled(e.getValue()));

        Checkbox apiToggle = new Checkbox(getTranslation("settings.enable_api"));
        apiToggle.setValue(globalSettingService.isApiEnabled());
        apiToggle.addValueChangeListener(e -> globalSettingService.setApiEnabled(e.getValue()));

        Div toggleRow = new Div(registrationToggle, apiToggle);
        toggleRow.getStyle()
                .set("display", "flex").set("flex-wrap", "wrap").set("gap", "var(--lumo-space-l)")
                .set("padding", "var(--lumo-space-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "12px");

        toggleCard.add(toggleRow);
        container.add(toggleCard);

        // ── User management card ──────────────────────────────────────
        Div card = createCard();

        Button addUserButton = new Button(getTranslation("settings.add_new_user"), VaadinIcon.PLUS.create(), e -> openAddUserDialog());
        addUserButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout mgmtHeader = new HorizontalLayout();
        mgmtHeader.setWidthFull();
        mgmtHeader.setAlignItems(FlexComponent.Alignment.CENTER);
        mgmtHeader.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        mgmtHeader.add(cardHeader(VaadinIcon.USERS, getTranslation("settings.user_management"), null, "var(--lumo-success-color)"), addUserButton);
        card.add(mgmtHeader);

        userGrid.removeAllColumns();
        userGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER);

        userGrid.addComponentColumn(u -> {
            Span initials = new Span(u.getUsername().length() >= 2 ? u.getUsername().substring(0,2).toUpperCase() : u.getUsername().toUpperCase());
            initials.getStyle()
                    .set("width","28px").set("height","28px").set("border-radius","50%")
                    .set("background","var(--lumo-contrast-10pct)").set("color","var(--lumo-secondary-text-color)")
                    .set("font-size","11px").set("font-weight","700")
                    .set("display","flex").set("align-items","center").set("justify-content","center").set("flex-shrink","0");
            Div nameStack = new Div();
            nameStack.getStyle().set("display","flex").set("flex-direction","column").set("gap","1px");
            Span name = new Span(u.getUsername()); name.getStyle().set("font-weight","600").set("font-size","var(--lumo-font-size-s)");
            Span mail = new Span(u.getEmail()); mail.getStyle().set("font-size","var(--lumo-font-size-xs)").set("color","var(--lumo-secondary-text-color)");
            nameStack.add(name, mail);
            Div row = new Div(initials, nameStack);
            row.getStyle().set("display","flex").set("align-items","center").set("gap","var(--lumo-space-s)").set("padding","var(--lumo-space-xs) 0");
            return row;
        }).setHeader(getTranslation("login.username")).setAutoWidth(true);

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
        }).setHeader(getTranslation("settings.admin")).setAutoWidth(true);

        userGrid.addComponentColumn(u -> {
            Checkbox isEnabled = new Checkbox();
            isEnabled.setValue(u.getEnabled());
            isEnabled.setEnabled(!u.getUsername().equals("demo"));
            isEnabled.addValueChangeListener(e -> userService.setUserEnabled(u, e.getValue()));
            return isEnabled;
        }).setHeader(getTranslation("settings.enabled")).setAutoWidth(true);

        userGrid.addComponentColumn(u -> {
            Checkbox apiEnabled = new Checkbox();
            apiEnabled.setValue(u.isApiEnabled());
            apiEnabled.addValueChangeListener(e -> userService.updateApiEnabled(u, e.getValue()));
            return apiEnabled;
        }).setHeader(getTranslation("settings.api_enabled")).setAutoWidth(true);

        userGrid.addComponentColumn(u -> {
            Button resetBtn = new Button(VaadinIcon.KEY.create(), e -> openAdminPasswordDialog(u));
            resetBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            resetBtn.setTooltipText(getTranslation("settings.force_password_change"));
            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e -> openDeleteUserDialog(u));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            deleteBtn.setTooltipText(getTranslation("settings.delete_user"));
            deleteBtn.setEnabled(!u.getUsername().equals(currentUser.getUsername()) && !u.getUsername().equals("demo"));
            HorizontalLayout hl = new HorizontalLayout(resetBtn, deleteBtn);
            hl.setSpacing(false); hl.getStyle().set("gap","var(--lumo-space-xs)");
            return hl;
        }).setHeader(getTranslation("transactions.actions")).setFrozenToEnd(true).setAutoWidth(true);

        userGrid.setItems(userService.findAll());
        userGrid.setAllRowsVisible(true);
        card.add(userGrid);
        container.add(card);
    }

    private void showUserSettings() {
        // ── Profile card ──────────────────────────────────────────────
        Div card = createCard();
        card.add(cardHeader(VaadinIcon.USER, getTranslation("settings.user_profile"), null, "var(--lumo-primary-color)"));

        TextField firstName = new TextField(getTranslation("settings.first_name"));
        firstName.setValue(currentUser.getFirstName());
        firstName.setWidthFull();
        TextField lastName = new TextField(getTranslation("settings.last_name"));
        lastName.setValue(currentUser.getLastName());
        lastName.setWidthFull();
        EmailField email = new EmailField(getTranslation("settings.email"));
        email.setValue(currentUser.getEmail());
        email.setWidthFull();

        HorizontalLayout nameRow = new HorizontalLayout(firstName, lastName);
        nameRow.setWidthFull(); nameRow.setSpacing(false);
        nameRow.getStyle().set("gap", "var(--lumo-space-m)").set("flex-wrap", "wrap");
        firstName.getStyle().set("flex", "1 1 160px");
        lastName.getStyle().set("flex", "1 1 160px");

        Button updateInfo = new Button(getTranslation("settings.update_profile"), VaadinIcon.CHECK.create(), e -> {
            userService.updateUserInfo(currentUser, firstName.getValue(), lastName.getValue(), email.getValue());
            Notification n = Notification.show(getTranslation("settings.profile_updated"), 2000, Notification.Position.BOTTOM_END);
            n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        updateInfo.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        card.add(nameRow, email, updateInfo);

        // ── Localization card ──────────────────────────────────────────
        Div localizationCard = createCard();
        localizationCard.add(cardHeader(VaadinIcon.GLOBE, getTranslation("settings.localization_title"),
                getTranslation("settings.localization_desc", "Language and currency preferences"), "var(--lumo-success-color)"));

        ComboBox<Currency> currencyComboBox = new ComboBox<>(getTranslation("settings.default_currency"));
        currencyComboBox.setItems(currencyService.getAllCurrencies());
        currencyComboBox.setItemLabelGenerator(c -> c.getCode() + " — " + c.getName());
        currencyComboBox.setWidthFull();
        currencyService.getAllCurrencies().stream()
                .filter(c -> c.getCode().equals(currentUser.getDefaultCurrency()))
                .findFirst().ifPresent(currencyComboBox::setValue);

        ComboBox<String> localeComboBox = new ComboBox<>(getTranslation("settings.localization"));
        localeComboBox.setItems("de-DE", "en-US", "en-GB");
        localeComboBox.setItemLabelGenerator(l -> l.equals("de-DE") ? "Deutsch (DE)" : l.equals("en-GB") ? "English (UK)" : "English (US)");
        localeComboBox.setValue(currentUser.getLocale());
        localeComboBox.setWidthFull();

        HorizontalLayout locRow = new HorizontalLayout(currencyComboBox, localeComboBox);
        locRow.setWidthFull(); locRow.setSpacing(false);
        locRow.getStyle().set("gap", "var(--lumo-space-m)").set("flex-wrap", "wrap");
        currencyComboBox.getStyle().set("flex", "1 1 200px");
        localeComboBox.getStyle().set("flex", "1 1 160px");

        Button saveLocalization = new Button(getTranslation("settings.save"), VaadinIcon.CHECK.create(), e -> {
            userService.updateDefaultCurrency(currentUser, currencyComboBox.getValue().getCode());
            userService.updateLocale(currentUser, localeComboBox.getValue());
            Notification n = Notification.show(getTranslation("settings.saved"), 2000, Notification.Position.BOTTOM_END);
            n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            UI.getCurrent().getPage().reload();
        });
        saveLocalization.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        localizationCard.add(locRow, saveLocalization);

        // ── Password card ──────────────────────────────────────────────
        Div passCard = createCard();
        passCard.add(cardHeader(VaadinIcon.LOCK, getTranslation("settings.security_password"),
                null, "var(--lumo-warning-color, #e8a000)"));

        PasswordField oldPass = new PasswordField(getTranslation("settings.current_password"));
        oldPass.setWidthFull();
        PasswordField newPass = new PasswordField(getTranslation("settings.new_password"));
        newPass.setWidthFull();
        PasswordField confirmPass = new PasswordField(getTranslation("settings.confirm_new_password"));
        confirmPass.setWidthFull();

        HorizontalLayout newPassRow = new HorizontalLayout(newPass, confirmPass);
        newPassRow.setWidthFull(); newPassRow.setSpacing(false);
        newPassRow.getStyle().set("gap", "var(--lumo-space-m)").set("flex-wrap", "wrap");
        newPass.getStyle().set("flex", "1 1 160px");
        confirmPass.getStyle().set("flex", "1 1 160px");

        Button changePass = new Button(getTranslation("settings.change_password"), VaadinIcon.LOCK.create(), e -> {
            if (userService.checkPassword(currentUser, oldPass.getValue())) {
                if (newPass.getValue().equals(confirmPass.getValue()) && newPass.getValue().length() >= 6) {
                    userService.updatePassword(currentUser, newPass.getValue());
                    Notification n = Notification.show(getTranslation("settings.saved"), 2000, Notification.Position.BOTTOM_END);
                    n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    oldPass.clear(); newPass.clear(); confirmPass.clear();
                } else {
                    Notification.show(getTranslation("settings.passwords_not_match"), 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            } else {
                Notification.show(getTranslation("settings.incorrect_old_password"), 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        changePass.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        passCard.add(oldPass, newPassRow, changePass);

        // ── Danger zone card ───────────────────────────────────────────
        Div dangerCard = createCard();
        dangerCard.getStyle().set("border-left", "4px solid var(--lumo-error-color)");
        dangerCard.add(cardHeader(VaadinIcon.WARNING, getTranslation("settings.danger_zone", "Danger Zone"),
                getTranslation("settings.danger_desc", "Permanently deletes all your accounts, transactions, scheduled entries and holdings. Cannot be undone."),
                "var(--lumo-error-color)"));

        Button cleanupButton = new Button(getTranslation("settings.clean_profile", "Clean All Profile Data"), VaadinIcon.TRASH.create(), e -> {
            Dialog confirmDialog = new Dialog();
            confirmDialog.setHeaderTitle(getTranslation("settings.confirm_wipe", "Confirm Data Wipe"));
            confirmDialog.add(new Span(getTranslation("settings.wipe_confirm_msg", "Are you sure? This will leave you with a fresh empty profile.")));
            Button delBtn = new Button(getTranslation("settings.delete_everything", "Yes, Delete Everything"), VaadinIcon.TRASH.create(), ev -> {
                profileCleanupService.cleanupUserData(currentUser);
                confirmDialog.close();
                Notification.show(getTranslation("settings.wiped", "Profile data wiped"), 3000, Notification.Position.BOTTOM_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                UI.getCurrent().navigate(DashboardView.class);
            });
            delBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
            Button cancelBtn = new Button(getTranslation("dialog.cancel"), ev -> confirmDialog.close());
            cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            confirmDialog.getFooter().add(cancelBtn, delBtn);
            confirmDialog.open();
        });
        cleanupButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        dangerCard.add(cleanupButton);

        container.add(card, localizationCard, passCard, dangerCard);
    }

    private void showImportExport() {
        // ── Homebank XHB ──────────────────────────────────────────────
        Div card = createCard();
        card.add(cardHeader(VaadinIcon.DATABASE, getTranslation("settings.import_export_title"),
                getTranslation("settings.data_desc"), "var(--lumo-primary-color)"));

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".xhb");
        upload.setUploadButton(new Button(getTranslation("settings.import"), VaadinIcon.UPLOAD.create()));
        upload.addSucceededListener(e -> {
            try {
                xhbImportService.importXhb(buffer.getInputStream(), currentUser);
                Notification.show(getTranslation("settings.import_success"), 2000, Notification.Position.BOTTOM_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show(getTranslation("settings.import_failed", ex.getMessage()), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Button exportBtn = new Button(getTranslation("settings.export"), VaadinIcon.DOWNLOAD.create());
        exportBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Anchor anchor = new Anchor(new StreamResource("export.xhb", () -> {
            try { return new ByteArrayInputStream(xhbExportService.exportXhb(currentUser)); }
            catch (Exception ex) { return new ByteArrayInputStream(new byte[0]); }
        }), "");
        anchor.add(exportBtn);

        HorizontalLayout xhbActions = new HorizontalLayout(upload, anchor);
        xhbActions.setAlignItems(FlexComponent.Alignment.CENTER);
        xhbActions.setSpacing(false);
        xhbActions.getStyle().set("gap", "var(--lumo-space-m)").set("flex-wrap", "wrap");
        card.add(xhbActions);
        container.add(card);

        // ── Trade Republic ────────────────────────────────────────────
        Div trCard = createCard();
        trCard.add(cardHeader(VaadinIcon.STOCK, "Trade Republic Import",
                getTranslation("settings.tr_desc", "Import your Trade Republic CSV statement. Select target accounts for cash and assets."),
                "var(--lumo-success-color)"));

        ComboBox<Account> cashAccountCombo = new ComboBox<>(getTranslation("settings.tr_cash_account", "Target Cash Account"));
        cashAccountCombo.setItems(accountService.getAccountsByUser(currentUser));
        cashAccountCombo.setItemLabelGenerator(Account::getAccountName);
        cashAccountCombo.setWidthFull();

        ComboBox<Account> assetAccountCombo = new ComboBox<>(getTranslation("settings.tr_asset_account", "Target Asset Account"));
        assetAccountCombo.setItems(accountService.getAccountsByUser(currentUser));
        assetAccountCombo.setItemLabelGenerator(Account::getAccountName);
        assetAccountCombo.setWidthFull();

        HorizontalLayout trAccounts = new HorizontalLayout(cashAccountCombo, assetAccountCombo);
        trAccounts.setWidthFull(); trAccounts.setSpacing(false);
        trAccounts.getStyle().set("gap", "var(--lumo-space-m)").set("flex-wrap", "wrap");
        cashAccountCombo.getStyle().set("flex", "1 1 200px");
        assetAccountCombo.getStyle().set("flex", "1 1 200px");

        MemoryBuffer trBuffer = new MemoryBuffer();
        Upload trUpload = new Upload(trBuffer);
        trUpload.setAcceptedFileTypes(".csv");
        trUpload.setUploadButton(new Button(getTranslation("settings.tr_import_btn", "Import Trade Republic CSV"), VaadinIcon.FILE_TEXT.create()));
        trUpload.addSucceededListener(e -> {
            if (cashAccountCombo.isEmpty() || assetAccountCombo.isEmpty()) {
                Notification.show(getTranslation("settings.tr_select_accounts", "Please select both target accounts first"), 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            try {
                tradeRepublicImportService.importCsv(trBuffer.getInputStream(), cashAccountCombo.getValue(), assetAccountCombo.getValue());
                Notification.show(getTranslation("settings.tr_success", "Trade Republic import successful!"), 2000, Notification.Position.BOTTOM_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show(getTranslation("settings.import_failed", ex.getMessage()), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        trCard.add(trAccounts, trUpload);
        container.add(trCard);

        // ── JSON Backup / Restore ─────────────────────────────────────
        Div jsonCard = createCard();
        jsonCard.add(cardHeader(VaadinIcon.ARCHIVE, getTranslation("settings.json_backup_restore"),
                getTranslation("settings.json_desc"), "var(--lumo-warning-color, #e8a000)"));

        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("cuenti_export_%s_%s.json", currentUser.getUsername(), timestamp);

        com.vaadin.flow.server.StreamResource jsonResource = new com.vaadin.flow.server.StreamResource(filename, () -> {
            try {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                jsonExportImportService.exportUserData(currentUser, out);
                return new ByteArrayInputStream(out.toByteArray());
            } catch (Exception ex) { return new ByteArrayInputStream(new byte[0]); }
        });
        jsonResource.setContentType("application/json");

        Button jsonExportBtn = new Button(getTranslation("settings.export_json"), VaadinIcon.DOWNLOAD.create());
        jsonExportBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Anchor jsonAnchor = new Anchor(jsonResource, "");
        jsonAnchor.getElement().setAttribute("download", true);
        jsonAnchor.add(jsonExportBtn);

        MemoryBuffer jsonBuffer = new MemoryBuffer();
        Upload jsonUpload = new Upload(jsonBuffer);
        jsonUpload.setAcceptedFileTypes("application/json", ".json");
        jsonUpload.setUploadButton(new Button(getTranslation("settings.import_json"), VaadinIcon.UPLOAD.create()));
        jsonUpload.setMaxFiles(1);
        jsonUpload.setMaxFileSize(50 * 1024 * 1024);
        jsonUpload.addSucceededListener(e -> {
            try {
                jsonExportImportService.importUserData(currentUser, jsonBuffer.getInputStream());
                Notification.show(getTranslation("settings.json_import_success"), 3000, Notification.Position.BOTTOM_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                UI.getCurrent().getPage().reload();
            } catch (Exception ex) {
                Notification.show(getTranslation("settings.json_import_failed", ex.getMessage()), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        HorizontalLayout jsonActions = new HorizontalLayout(jsonAnchor, jsonUpload);
        jsonActions.setAlignItems(FlexComponent.Alignment.CENTER);
        jsonActions.setSpacing(false);
        jsonActions.getStyle().set("gap", "var(--lumo-space-m)").set("flex-wrap", "wrap");

        jsonCard.add(jsonActions, infoBanner(getTranslation("settings.json_warning"), true));
        container.add(jsonCard);
    }

    private void openAddUserDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("settings.add_new_user"));
        dialog.setWidth("min(600px, 96vw)");

        TextField username = new TextField(getTranslation("login.username")); username.setWidthFull();
        EmailField email = new EmailField("Email"); email.setWidthFull();
        TextField firstName = new TextField(getTranslation("settings.first_name")); firstName.setWidthFull();
        TextField lastName = new TextField(getTranslation("settings.last_name")); lastName.setWidthFull();
        PasswordField password = new PasswordField(getTranslation("login.password")); password.setWidthFull();
        PasswordField confirmPassword = new PasswordField(getTranslation("settings.confirm_password")); confirmPassword.setWidthFull();

        HorizontalLayout nameRow = new HorizontalLayout(firstName, lastName);
        nameRow.setWidthFull(); nameRow.setSpacing(false);
        nameRow.getStyle().set("gap","var(--lumo-space-m)").set("flex-wrap","wrap");
        firstName.getStyle().set("flex","1 1 160px"); lastName.getStyle().set("flex","1 1 160px");
        HorizontalLayout passRow = new HorizontalLayout(password, confirmPassword);
        passRow.setWidthFull(); passRow.setSpacing(false);
        passRow.getStyle().set("gap","var(--lumo-space-m)").set("flex-wrap","wrap");
        password.getStyle().set("flex","1 1 160px"); confirmPassword.getStyle().set("flex","1 1 160px");

        VerticalLayout form = new VerticalLayout(username, email, nameRow, passRow);
        form.setPadding(false); form.setSpacing(false);
        form.getStyle().set("gap","var(--lumo-space-s)");
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

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private void openAdminPasswordDialog(User user) {
        Dialog d = new Dialog();
        d.setHeaderTitle(getTranslation("settings.reset_password_for", user.getUsername()));
        d.setWidth("min(440px, 96vw)");
        PasswordField p1 = new PasswordField(getTranslation("settings.new_password")); p1.setWidthFull();
        PasswordField p2 = new PasswordField(getTranslation("settings.confirm_password")); p2.setWidthFull();
        VerticalLayout body = new VerticalLayout(p1, p2);
        body.setPadding(false); body.setSpacing(false); body.getStyle().set("gap","var(--lumo-space-s)");
        d.add(body);
        Button save = new Button(getTranslation("settings.reset"), VaadinIcon.CHECK.create(), e -> {
            if (p1.getValue().equals(p2.getValue()) && !p1.getValue().isBlank()) {
                userService.updatePassword(user, p1.getValue());
                d.close();
                Notification.show(getTranslation("settings.saved"), 2000, Notification.Position.BOTTOM_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else {
                Notification.show(getTranslation("settings.passwords_not_match"), 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button(getTranslation("dialog.cancel"), e -> d.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        d.getFooter().add(cancel, save);
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
        card.setWidthFull();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "20px")
                .set("padding", "var(--lumo-space-l)")
                .set("box-shadow", "0 2px 12px rgba(0,0,0,0.06)")
                .set("box-sizing", "border-box")
                .set("display", "flex").set("flex-direction", "column")
                .set("gap", "var(--lumo-space-m)");
        return card;
    }

    /** Section header: icon + bold title + optional subtitle, with a bottom divider. */
    private Div cardHeader(VaadinIcon icon, String title, String subtitle, String accentColor) {
        Icon ico = icon.create();
        ico.getStyle()
                .set("color", accentColor != null ? accentColor : "var(--lumo-primary-color)")
                .set("font-size", "20px").set("flex-shrink", "0");

        Span titleSpan = new Span(title);
        titleSpan.getStyle()
                .set("font-size", "var(--lumo-font-size-l)").set("font-weight", "700")
                .set("color", "var(--lumo-header-text-color)");

        Div headingRow = new Div();
        headingRow.getStyle().set("display", "flex").set("align-items", "center").set("gap", "var(--lumo-space-s)");
        headingRow.add(ico, titleSpan);

        Div wrapper = new Div();
        wrapper.setWidthFull();
        wrapper.getStyle()
                .set("padding-bottom", "var(--lumo-space-m)")
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)")
                .set("display", "flex").set("flex-direction", "column").set("gap", "4px");
        wrapper.add(headingRow);

        if (subtitle != null && !subtitle.isBlank()) {
            Span sub = new Span(subtitle);
            sub.getStyle().set("font-size", "var(--lumo-font-size-s)")
                    .set("color", "var(--lumo-secondary-text-color)");
            wrapper.add(sub);
        }
        return wrapper;
    }

    /** Subtle info/warning banner. */
    private Div infoBanner(String text, boolean error) {
        Div banner = new Div();
        banner.setWidthFull();
        String color  = error ? "var(--lumo-error-color)"   : "var(--lumo-primary-color)";
        String bg     = error ? "rgba(var(--lumo-error-color-50pct-rgb, 255,63,63),0.08)"
                              : "var(--lumo-primary-color-10pct, rgba(26,119,242,0.08))";
        Span text_ = new Span(text);
        text_.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", color);
        banner.add(text_);
        banner.getStyle()
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "10px")
                .set("background", bg)
                .set("border-left", "3px solid " + color)
                .set("box-sizing", "border-box");
        return banner;
    }
}
