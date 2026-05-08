package com.cuenti.app.views;

import com.cuenti.app.model.User;
import com.cuenti.app.security.SecurityUtils;
import com.cuenti.app.service.GlobalSettingService;
import com.cuenti.app.service.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "settings/admin", layout = MainLayout.class)
@RolesAllowed("ROLE_ADMIN")
public class SettingsAdminView extends BaseSettingsView implements HasDynamicTitle {

    private final UserService userService;
    private final GlobalSettingService globalSettingService;
    private final Grid<User> userGrid = new Grid<>(User.class, false);

    public SettingsAdminView(UserService userService, GlobalSettingService globalSettingService,
                             SecurityUtils securityUtils) {
        super(securityUtils, userService);
        this.userService = userService;
        this.globalSettingService = globalSettingService;
        buildContent();
    }

    @Override
    public String getPageTitle() {
        return getTranslation("settings.title") + " | " + getTranslation("app.name");
    }

    private void buildContent() {
        // ── Global toggles card ───────────────────────────────────────
        Div toggleCard = createCard();
        toggleCard.add(cardHeader(VaadinIcon.COG, getTranslation("settings.administration"),
                getTranslation("settings.administration_desc"), "var(--lumo-primary-color)"));

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
            Span initials = new Span(u.getUsername().length() >= 2 ? u.getUsername().substring(0, 2).toUpperCase() : u.getUsername().toUpperCase());
            initials.getStyle()
                    .set("width", "28px").set("height", "28px").set("border-radius", "50%")
                    .set("background", "var(--lumo-contrast-10pct)").set("color", "var(--lumo-secondary-text-color)")
                    .set("font-size", "11px").set("font-weight", "700")
                    .set("display", "flex").set("align-items", "center").set("justify-content", "center").set("flex-shrink", "0");
            Div nameStack = new Div();
            nameStack.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "1px");
            Span name = new Span(u.getUsername()); name.getStyle().set("font-weight", "600").set("font-size", "var(--lumo-font-size-s)");
            Span mail = new Span(u.getEmail()); mail.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("color", "var(--lumo-secondary-text-color)");
            nameStack.add(name, mail);
            Div row = new Div(initials, nameStack);
            row.getStyle().set("display", "flex").set("align-items", "center").set("gap", "var(--lumo-space-s)").set("padding", "var(--lumo-space-xs) 0");
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
            hl.setSpacing(false); hl.getStyle().set("gap", "var(--lumo-space-xs)");
            return hl;
        }).setHeader(getTranslation("transactions.actions")).setFrozenToEnd(true).setAutoWidth(true);

        userGrid.setItems(userService.findAll());
        userGrid.setAllRowsVisible(true);
        card.add(userGrid);
        container.add(card);
    }

    private void openAddUserDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("settings.add_new_user"));
        dialog.setWidth("min(600px, 96vw)");

        TextField username = new TextField(getTranslation("login.username")); username.setWidthFull();
        EmailField email = new EmailField(getTranslation("settings.email")); email.setWidthFull();
        TextField firstName = new TextField(getTranslation("settings.first_name")); firstName.setWidthFull();
        TextField lastName = new TextField(getTranslation("settings.last_name")); lastName.setWidthFull();
        PasswordField password = new PasswordField(getTranslation("login.password")); password.setWidthFull();
        PasswordField confirmPassword = new PasswordField(getTranslation("settings.confirm_password")); confirmPassword.setWidthFull();

        HorizontalLayout nameRow = new HorizontalLayout(firstName, lastName);
        nameRow.setWidthFull(); nameRow.setSpacing(false);
        nameRow.getStyle().set("gap", "var(--lumo-space-m)").set("flex-wrap", "wrap");
        firstName.getStyle().set("flex", "1 1 160px"); lastName.getStyle().set("flex", "1 1 160px");
        HorizontalLayout passRow = new HorizontalLayout(password, confirmPassword);
        passRow.setWidthFull(); passRow.setSpacing(false);
        passRow.getStyle().set("gap", "var(--lumo-space-m)").set("flex-wrap", "wrap");
        password.getStyle().set("flex", "1 1 160px"); confirmPassword.getStyle().set("flex", "1 1 160px");

        VerticalLayout form = new VerticalLayout(username, email, nameRow, passRow);
        form.setPadding(false); form.setSpacing(false);
        form.getStyle().set("gap", "var(--lumo-space-s)");
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
                Notification.show(getTranslation("settings.error_prefix") + ex.getMessage(), 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
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
        body.setPadding(false); body.setSpacing(false); body.getStyle().set("gap", "var(--lumo-space-s)");
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
                getTranslation("settings.delete_user_warning", "Are you sure you want to delete user '%s'? This will permanently delete:"),
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
                Notification.show(getTranslation("settings.user_deleted", "User deleted successfully"), 3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                confirmDialog.close();
            } catch (Exception ex) {
                Notification.show(getTranslation("settings.user_delete_failed", "Failed to delete user: ") + ex.getMessage(), 5000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        Button cancelBtn = new Button(getTranslation("dialog.cancel", "Cancel"), event -> confirmDialog.close());
        confirmDialog.getFooter().add(cancelBtn, deleteBtn);
        confirmDialog.open();
    }
}
