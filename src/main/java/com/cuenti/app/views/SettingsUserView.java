package com.cuenti.app.views;

import com.cuenti.app.model.Currency;
import com.cuenti.app.security.SecurityUtils;
import com.cuenti.app.service.CurrencyService;
import com.cuenti.app.service.ProfileCleanupService;
import com.cuenti.app.service.UserService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "settings/user", layout = MainLayout.class)
@PermitAll
public class SettingsUserView extends BaseSettingsView implements HasDynamicTitle {

    private final UserService userService;
    private final CurrencyService currencyService;
    private final ProfileCleanupService profileCleanupService;

    public SettingsUserView(UserService userService, CurrencyService currencyService,
                            ProfileCleanupService profileCleanupService,
                            SecurityUtils securityUtils) {
        super(securityUtils, userService);
        this.userService = userService;
        this.currencyService = currencyService;
        this.profileCleanupService = profileCleanupService;
        buildContent();
    }

    @Override
    public String getPageTitle() {
        return getTranslation("settings.title") + " | " + getTranslation("app.name");
    }

    private void buildContent() {
        // ── Profile card ──────────────────────────────────────────────
        Div card = createCard();
        card.add(cardHeader(VaadinIcon.USER, getTranslation("settings.user_profile"), null, "var(--aura-accent-color)"));

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
        nameRow.getStyle().set("gap", "var(--vaadin-gap-m)").set("flex-wrap", "wrap");
        firstName.getStyle().set("flex", "1 1 160px");
        lastName.getStyle().set("flex", "1 1 160px");

        Button updateInfo = new Button(getTranslation("settings.update_profile"), VaadinIcon.CHECK.create(), e -> {
            userService.updateUserInfo(currentUser, firstName.getValue(), lastName.getValue(), email.getValue());
            com.cuenti.app.views.components.UiNotifier.success(getTranslation("settings.profile_updated"));
        });
        updateInfo.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        card.add(nameRow, email, updateInfo);

        // ── Localization card ──────────────────────────────────────────
        Div localizationCard = createCard();
        localizationCard.add(cardHeader(VaadinIcon.GLOBE, getTranslation("settings.localization_title"),
                getTranslation("settings.localization_desc"), "var(--aura-green)"));

        ComboBox<Currency> currencyComboBox = new ComboBox<>(getTranslation("settings.default_currency"));
        currencyComboBox.setItems(currencyService.getAllCurrencies());
        currencyComboBox.setItemLabelGenerator(c -> c.getCode() + " — " + c.getName());
        currencyComboBox.setWidthFull();
        currencyService.getAllCurrencies().stream()
                .filter(c -> c.getCode().equals(currentUser.getDefaultCurrency()))
                .findFirst().ifPresent(currencyComboBox::setValue);

        ComboBox<String> localeComboBox = new ComboBox<>(getTranslation("settings.localization"));
        localeComboBox.setItems("de-DE", "en-US", "en-GB");
        localeComboBox.setItemLabelGenerator(l -> switch (l) {
            case "de-DE" -> getTranslation("locale.de");
            case "en-GB" -> getTranslation("locale.en_gb");
            case "en-US" -> getTranslation("locale.en_us");
            default -> l;
        });
        localeComboBox.setValue(currentUser.getLocale());
        localeComboBox.setWidthFull();

        HorizontalLayout locRow = new HorizontalLayout(currencyComboBox, localeComboBox);
        locRow.setWidthFull(); locRow.setSpacing(false);
        locRow.getStyle().set("gap", "var(--vaadin-gap-m)").set("flex-wrap", "wrap");
        currencyComboBox.getStyle().set("flex", "1 1 200px");
        localeComboBox.getStyle().set("flex", "1 1 160px");

        Button saveLocalization = new Button(getTranslation("settings.save"), VaadinIcon.CHECK.create(), e -> {
            userService.updateDefaultCurrency(currentUser, currencyComboBox.getValue().getCode());
            userService.updateLocale(currentUser, localeComboBox.getValue());
            com.cuenti.app.views.components.UiNotifier.success(getTranslation("settings.saved"));
            UI.getCurrent().getPage().reload();
        });
        saveLocalization.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        localizationCard.add(locRow, saveLocalization);

        // ── Password card ──────────────────────────────────────────────
        Div passCard = createCard();
        passCard.add(cardHeader(VaadinIcon.LOCK, getTranslation("settings.security_password"),
                null, "var(--aura-orange)"));

        PasswordField oldPass = new PasswordField(getTranslation("settings.current_password"));
        oldPass.setWidthFull();
        PasswordField newPass = new PasswordField(getTranslation("settings.new_password"));
        newPass.setWidthFull();
        PasswordField confirmPass = new PasswordField(getTranslation("settings.confirm_new_password"));
        confirmPass.setWidthFull();

        HorizontalLayout newPassRow = new HorizontalLayout(newPass, confirmPass);
        newPassRow.setWidthFull(); newPassRow.setSpacing(false);
        newPassRow.getStyle().set("gap", "var(--vaadin-gap-m)").set("flex-wrap", "wrap");
        newPass.getStyle().set("flex", "1 1 160px");
        confirmPass.getStyle().set("flex", "1 1 160px");

        Button changePass = new Button(getTranslation("settings.change_password"), VaadinIcon.LOCK.create(), e -> {
            if (userService.checkPassword(currentUser, oldPass.getValue())) {
                if (!newPass.getValue().equals(confirmPass.getValue())) {
                    com.cuenti.app.views.components.UiNotifier.error(getTranslation("settings.passwords_not_match"));
                } else {
                    try {
                        userService.updatePassword(currentUser, newPass.getValue());
                        com.cuenti.app.views.components.UiNotifier.success(getTranslation("settings.saved"));
                        oldPass.clear(); newPass.clear(); confirmPass.clear();
                    } catch (IllegalArgumentException ex) {
                        com.cuenti.app.views.components.UiNotifier.error(getTranslation("register.validation.password_length"));
                    }
                }
            } else {
                com.cuenti.app.views.components.UiNotifier.error(getTranslation("settings.incorrect_old_password"));
            }
        });
        changePass.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        // Revokes every API token (mobile app, scripts); the web session stays signed in
        Button logoutApps = new Button(getTranslation("settings.logout_all_apps"), VaadinIcon.SIGN_OUT.create(), e -> {
            userService.revokeTokens(currentUser, "LOGOUT_ALL");
            com.cuenti.app.views.components.UiNotifier.success(getTranslation("settings.logout_all_apps_done"));
        });
        logoutApps.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        logoutApps.setTooltipText(getTranslation("settings.logout_all_apps_hint"));

        HorizontalLayout passActions = new HorizontalLayout(changePass, logoutApps);
        passActions.setSpacing(false);
        passActions.getStyle().set("gap", "var(--vaadin-gap-s)").set("flex-wrap", "wrap");
        passCard.add(oldPass, newPassRow, passActions);

        // ── Danger zone card ───────────────────────────────────────────
        Div dangerCard = createCard();
        dangerCard.getStyle().set("border-left", "4px solid var(--aura-red)");
        dangerCard.add(cardHeader(VaadinIcon.WARNING, getTranslation("settings.danger_zone"),
                getTranslation("settings.danger_desc"), "var(--aura-red)"));

        Button cleanupButton = new Button(getTranslation("settings.clean_profile"), VaadinIcon.TRASH.create(), e -> {
            Dialog confirmDialog = new Dialog();
            confirmDialog.setHeaderTitle(getTranslation("settings.confirm_wipe"));
            Span msg = new Span(getTranslation("settings.wipe_confirm_msg"));
            Span typeHint = new Span(getTranslation("settings.delete_confirm_type"));
            typeHint.getStyle().set("font-size", "var(--aura-font-size-s)").set("color", "var(--vaadin-text-color-secondary)");
            TextField confirmField = new TextField();
            confirmField.setPlaceholder("DELETE");
            confirmField.setWidthFull();
            confirmField.setValueChangeMode(ValueChangeMode.EAGER);
            Div dialogBody = new Div(msg, typeHint, confirmField);
            dialogBody.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "var(--vaadin-gap-s)");
            confirmDialog.add(dialogBody);
            Button delBtn = new Button(getTranslation("settings.delete_everything"), VaadinIcon.TRASH.create(), ev -> {
                profileCleanupService.cleanupUserData(currentUser);
                confirmDialog.close();
                com.cuenti.app.views.components.UiNotifier.success(getTranslation("settings.wiped"));
                UI.getCurrent().navigate(DashboardView.class);
            });
            delBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
            delBtn.setEnabled(false);
            confirmField.addValueChangeListener(ev -> delBtn.setEnabled("DELETE".equals(ev.getValue())));
            Button cancelBtn = new Button(getTranslation("dialog.cancel"), ev -> confirmDialog.close());
            cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            confirmDialog.getFooter().add(cancelBtn, delBtn);
            confirmDialog.open();
        });
        cleanupButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        dangerCard.add(cleanupButton);

        container.add(card, localizationCard, scheduledCard(), passCard, twoFactorCard(), dangerCard);
    }

    // ── Two-factor sign-in ─────────────────────────────────────────────

    private Div twoFactorCard() {
        Div twoFactorCard = createCard();
        twoFactorCard.add(cardHeader(VaadinIcon.KEY, getTranslation("settings.twofactor_title"),
                getTranslation("settings.twofactor_desc"), "var(--aura-green)"));
        fillTwoFactorCard(twoFactorCard);
        return twoFactorCard;
    }

    private void fillTwoFactorCard(Div twoFactorCard) {
        twoFactorCard.getChildren().skip(1).toList().forEach(twoFactorCard::remove);
        if (currentUser.isTotpEnabled()) {
            Span status = new Span(getTranslation("settings.twofactor_on",
                    userService.remainingRecoveryCodes(currentUser)));
            Button off = new Button(getTranslation("settings.twofactor_disable"), VaadinIcon.CLOSE_SMALL.create(),
                    e -> openDisableTwoFactor(twoFactorCard));
            off.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            twoFactorCard.add(status, off);
        } else {
            Span status = new Span(getTranslation("settings.twofactor_off"));
            Button on = new Button(getTranslation("settings.twofactor_enable"), VaadinIcon.KEY.create(),
                    e -> openEnableTwoFactor(twoFactorCard));
            on.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            twoFactorCard.add(status, on);
        }
    }

    private void openEnableTwoFactor(Div twoFactorCard) {
        UserService.TotpSetup setup = userService.startTotpSetup(currentUser);
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("settings.twofactor_enable"));
        dialog.setWidth("min(460px, 96vw)");

        Span step1 = new Span(getTranslation("settings.twofactor_scan"));
        com.vaadin.flow.component.html.Image qr = com.cuenti.app.views.components.QrCode.image(
                setup.otpauthUri(), getTranslation("settings.twofactor_qr_alt"));
        Span secret = new Span(setup.secret().replaceAll("(.{4})", "$1 ").trim());
        secret.getStyle().set("font-family", "monospace").set("user-select", "all").set("word-break", "break-all");
        Span manual = new Span(getTranslation("settings.twofactor_manual"));
        manual.getStyle().set("font-size", "var(--aura-font-size-xs)").set("color", "var(--vaadin-text-color-secondary)");
        TextField code = new TextField(getTranslation("twofactor.code"));
        code.setWidthFull();
        code.getElement().setAttribute("autocomplete", "one-time-code");

        com.vaadin.flow.component.orderedlayout.VerticalLayout body =
                new com.vaadin.flow.component.orderedlayout.VerticalLayout(step1, qr, manual, secret, code);
        body.setPadding(false);
        body.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
        dialog.add(body);

        Button cancel = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button activate = new Button(getTranslation("settings.twofactor_activate"), e -> {
            try {
                java.util.List<String> codes = userService.enableTotp(currentUser, setup.secret(), code.getValue());
                // this session just proved the second factor
                com.cuenti.app.security.TwoFactorSession.markVerified(currentUser.getUsername());
                fillTwoFactorCard(twoFactorCard);
                showRecoveryCodes(dialog, codes);
            } catch (IllegalArgumentException ex) {
                code.setInvalid(true);
                code.setErrorMessage(getTranslation("twofactor.invalid"));
            }
        });
        activate.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, activate);
        dialog.open();
    }

    private void showRecoveryCodes(Dialog dialog, java.util.List<String> codes) {
        dialog.removeAll();
        dialog.getFooter().removeAll();
        dialog.setHeaderTitle(getTranslation("settings.twofactor_recovery_title"));
        Span intro = new Span(getTranslation("settings.twofactor_recovery_intro"));
        Div list = new Div();
        list.getStyle().set("font-family", "monospace").set("display", "grid")
                .set("grid-template-columns", "1fr 1fr").set("gap", "var(--vaadin-gap-xs) var(--vaadin-gap-m)")
                .set("user-select", "all").set("margin-top", "var(--vaadin-gap-m)");
        codes.forEach(c -> list.add(new Span(c)));
        dialog.add(intro, list);
        dialog.setCloseOnOutsideClick(false);
        Button done = new Button(getTranslation("settings.twofactor_recovery_saved"), e -> dialog.close());
        done.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(done);
    }

    private void openDisableTwoFactor(Div twoFactorCard) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("settings.twofactor_disable"));
        dialog.setWidth("min(420px, 96vw)");
        TextField code = new TextField(getTranslation("twofactor.code"));
        code.setWidthFull();
        code.getElement().setAttribute("autocomplete", "one-time-code");
        dialog.add(new Span(getTranslation("settings.twofactor_disable_hint")), code);
        Button cancel = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button confirm = new Button(getTranslation("settings.twofactor_disable"), e -> {
            try {
                userService.disableTotp(currentUser, code.getValue());
                fillTwoFactorCard(twoFactorCard);
                dialog.close();
                com.cuenti.app.views.components.UiNotifier.success(getTranslation("settings.saved"));
            } catch (IllegalArgumentException ex) {
                code.setInvalid(true);
                code.setErrorMessage(getTranslation("twofactor.invalid"));
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    /** What the Geplant nav badge counts: due/overdue only, or a look-ahead window. */
    private Div scheduledCard() {
        Div scheduledCard = createCard();
        scheduledCard.add(cardHeader(VaadinIcon.CALENDAR_CLOCK, getTranslation("settings.scheduled_title"),
                getTranslation("settings.scheduled_desc"), "var(--aura-orange)"));

        com.vaadin.flow.component.select.Select<Integer> badgeDays = new com.vaadin.flow.component.select.Select<>();
        badgeDays.setLabel(getTranslation("settings.scheduled_badge"));
        badgeDays.setItems(0, 3, 7, 14);
        badgeDays.setItemLabelGenerator(d -> d == 0
                ? getTranslation("settings.scheduled_badge.0")
                : getTranslation("settings.scheduled_badge.n", d));
        Integer current = currentUser.getScheduledBadgeDays();
        badgeDays.setValue(current != null && java.util.List.of(0, 3, 7, 14).contains(current) ? current : 0);
        badgeDays.setWidth("min(320px, 100%)");

        Button save = new Button(getTranslation("settings.save"), VaadinIcon.CHECK.create(), e -> {
            userService.updateScheduledPreferences(currentUser, badgeDays.getValue(),
                    currentUser.getScheduledHorizonDays());
            com.cuenti.app.views.components.UiNotifier.success(getTranslation("settings.saved"));
            // badge lives in MainLayout with its own user snapshot
            UI.getCurrent().getPage().reload();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        scheduledCard.add(badgeDays, save);
        return scheduledCard;
    }
}
