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
                if (newPass.getValue().equals(confirmPass.getValue()) && newPass.getValue().length() >= 6) {
                    userService.updatePassword(currentUser, newPass.getValue());
                    com.cuenti.app.views.components.UiNotifier.success(getTranslation("settings.saved"));
                    oldPass.clear(); newPass.clear(); confirmPass.clear();
                } else {
                    com.cuenti.app.views.components.UiNotifier.error(getTranslation("settings.passwords_not_match"));
                }
            } else {
                com.cuenti.app.views.components.UiNotifier.error(getTranslation("settings.incorrect_old_password"));
            }
        });
        changePass.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        passCard.add(oldPass, newPassRow, changePass);

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

        container.add(card, localizationCard, passCard, dangerCard);
    }
}
