package com.cuenti.homebanking.views.admin;

import com.cuenti.homebanking.data.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.services.GlobalSettingService;
import com.cuenti.homebanking.services.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

/**
 * Administration view for managing users and global settings.
 * Only accessible to users with ROLE_ADMIN.
 */
@PageTitle("Administration")
@Route("admin")
@Menu(order = 11, icon = LineAwesomeIconUrl.USER_SHIELD_SOLID)
@RolesAllowed("ADMIN")
public class AdministrationView extends VerticalLayout implements BeforeEnterObserver {

    private final UserService userService;
    private final GlobalSettingService globalSettingService;
    private final SecurityUtils securityUtils;
    private final User currentUser;

    private final Grid<User> userGrid = new Grid<>(User.class, false);

    public AdministrationView(UserService userService, GlobalSettingService globalSettingService,
                               SecurityUtils securityUtils) {
        this.userService = userService;
        this.globalSettingService = globalSettingService;
        this.securityUtils = securityUtils;

        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));
        this.currentUser = userService.findByUsername(username);

        setSpacing(true);
        setPadding(true);
        setMaxWidth("1200px");
        getStyle().set("margin", "0 auto");

        setupUI();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Additional security check
        if (!currentUser.getRoles().contains("ROLE_ADMIN")) {
            event.forwardTo("");
        }
    }

    private void setupUI() {
        H2 title = new H2(getTranslation("settings.administration"));

        // Global settings section
        H4 globalSettingsTitle = new H4("Global Settings");

        Checkbox registrationToggle = new Checkbox(getTranslation("settings.enable_registration"));
        registrationToggle.setValue(globalSettingService.isRegistrationEnabled());
        registrationToggle.addValueChangeListener(e -> {
            globalSettingService.setRegistrationEnabled(e.getValue());
            Notification.show(e.getValue() ? "Registration enabled" : "Registration disabled")
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });

        Button addUserButton = new Button(getTranslation("settings.add_new_user"), VaadinIcon.PLUS.create(),
                e -> openAddUserDialog());
        addUserButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        HorizontalLayout globalControls = new HorizontalLayout(registrationToggle, addUserButton);
        globalControls.setAlignItems(Alignment.CENTER);
        globalControls.setSpacing(true);

        // User management section
        H4 userManagementTitle = new H4(getTranslation("settings.user_management"));

        setupUserGrid();
        userGrid.setItems(userService.findAll());

        add(title, globalSettingsTitle, globalControls, userManagementTitle, userGrid);
    }

    private void setupUserGrid() {
        userGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        userGrid.setAllRowsVisible(true);

        userGrid.addColumn(User::getUsername).setHeader(getTranslation("login.username")).setAutoWidth(true).setSortable(true);
        userGrid.addColumn(User::getEmail).setHeader("Email").setAutoWidth(true).setSortable(true);
        userGrid.addColumn(User::getFirstName).setHeader(getTranslation("settings.first_name")).setAutoWidth(true);
        userGrid.addColumn(User::getLastName).setHeader(getTranslation("settings.last_name")).setAutoWidth(true);

        // Admin checkbox
        userGrid.addComponentColumn(u -> {
            Checkbox isAdmin = new Checkbox();
            isAdmin.setValue(u.getRoles().contains("ROLE_ADMIN"));
            isAdmin.setEnabled(!u.getUsername().equals("demo"));
            isAdmin.addValueChangeListener(e -> {
                if (e.getValue()) {
                    u.getRoles().add("ROLE_ADMIN");
                } else {
                    u.getRoles().remove("ROLE_ADMIN");
                }
                userService.saveUser(u);
                Notification.show("User roles updated").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            });
            return isAdmin;
        }).setHeader("Admin").setAutoWidth(true);

        // Enabled checkbox
        userGrid.addComponentColumn(u -> {
            Checkbox isEnabled = new Checkbox();
            isEnabled.setValue(u.getEnabled());
            isEnabled.setEnabled(!u.getUsername().equals("demo"));
            isEnabled.addValueChangeListener(e -> {
                userService.setUserEnabled(u, e.getValue());
                Notification.show(e.getValue() ? "User enabled" : "User disabled")
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            });
            return isEnabled;
        }).setHeader("Enabled").setAutoWidth(true);

        // Reset password button
        userGrid.addComponentColumn(u -> {
            Button resetBtn = new Button(VaadinIcon.KEY.create(), e -> openAdminPasswordDialog(u));
            resetBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            resetBtn.setTooltipText("Reset Password");
            return resetBtn;
        }).setHeader("Password").setAutoWidth(true);

        // Delete button
        userGrid.addComponentColumn(u -> {
            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e -> openDeleteUserDialog(u));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            deleteBtn.setTooltipText("Delete User");
            deleteBtn.setEnabled(!u.getUsername().equals(currentUser.getUsername()) && !u.getUsername().equals("demo"));
            return deleteBtn;
        }).setHeader("Delete").setAutoWidth(true);
    }

    private void openAddUserDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("settings.add_new_user"));
        dialog.setWidth("500px");

        TextField username = new TextField(getTranslation("login.username"));
        username.setWidthFull();
        EmailField email = new EmailField("Email");
        email.setWidthFull();
        TextField firstName = new TextField(getTranslation("settings.first_name"));
        firstName.setWidthFull();
        TextField lastName = new TextField(getTranslation("settings.last_name"));
        lastName.setWidthFull();
        PasswordField password = new PasswordField(getTranslation("login.password"));
        password.setWidthFull();
        PasswordField confirmPassword = new PasswordField("Confirm Password");
        confirmPassword.setWidthFull();

        FormLayout form = new FormLayout(username, email, firstName, lastName, password, confirmPassword);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));
        dialog.add(form);

        Button save = new Button("Create User", e -> {
            try {
                if (username.isEmpty() || email.isEmpty() || password.isEmpty() ||
                    firstName.isEmpty() || lastName.isEmpty()) {
                    Notification.show("All fields are required", 3000, Notification.Position.TOP_CENTER)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }
                if (!password.getValue().equals(confirmPassword.getValue())) {
                    Notification.show("Passwords do not match", 3000, Notification.Position.TOP_CENTER)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }
                if (password.getValue().length() < 6) {
                    Notification.show("Password must be at least 6 characters", 3000, Notification.Position.TOP_CENTER)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }

                userService.registerUser(username.getValue(), email.getValue(), password.getValue(),
                        firstName.getValue(), lastName.getValue());
                userGrid.setItems(userService.findAll());
                Notification.show("User created successfully").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancel = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private void openAdminPasswordDialog(User user) {
        Dialog d = new Dialog("Reset Password for " + user.getUsername());
        d.setWidth("400px");

        PasswordField p1 = new PasswordField("New Password");
        p1.setWidthFull();
        PasswordField p2 = new PasswordField("Confirm Password");
        p2.setWidthFull();

        Button save = new Button("Reset Password", e -> {
            if (p1.getValue().equals(p2.getValue()) && p1.getValue().length() >= 6) {
                userService.updatePassword(user, p1.getValue());
                d.close();
                Notification.show("Password reset successfully").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else {
                Notification.show("Passwords must match and be at least 6 characters")
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancel = new Button(getTranslation("dialog.cancel"), e -> d.close());

        d.add(new VerticalLayout(p1, p2));
        d.getFooter().add(cancel, save);
        d.open();
    }

    private void openDeleteUserDialog(User user) {
        Dialog confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle("Delete User: " + user.getUsername());
        confirmDialog.setWidth("500px");

        Paragraph warningText = new Paragraph(
                "Are you sure you want to delete this user? This will permanently delete all their " +
                "accounts, transactions, categories, payees, tags, and all other data. This action cannot be undone!");
        warningText.getStyle()
                .set("color", "var(--lumo-error-text-color)")
                .set("background-color", "var(--lumo-error-color-10pct)")
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-m)");

        confirmDialog.add(warningText);

        Button deleteBtn = new Button("Yes, Delete User", event -> {
            try {
                userService.deleteUser(user);
                userGrid.setItems(userService.findAll());
                Notification.show("User deleted successfully").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                confirmDialog.close();
            } catch (Exception ex) {
                Notification.show("Failed to delete user: " + ex.getMessage())
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        Button cancelBtn = new Button(getTranslation("dialog.cancel"), event -> confirmDialog.close());
        confirmDialog.getFooter().add(cancelBtn, deleteBtn);
        confirmDialog.open();
    }
}
