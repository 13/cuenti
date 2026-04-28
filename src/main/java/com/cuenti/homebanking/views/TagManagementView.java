package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.Tag;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.TagService;
import com.cuenti.homebanking.service.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "tags", layout = MainLayout.class)
@PermitAll
public class TagManagementView extends VerticalLayout implements HasDynamicTitle {

    @Override
    public String getPageTitle() {
        return getTranslation("tags.title") + " | " + getTranslation("app.name");
    }


    private final TagService tagService;
    private final UserService userService;
    private final SecurityUtils securityUtils;
    private final User currentUser;

    private final Grid<Tag> grid = new Grid<>(Tag.class, false);
    private final TextField searchField = new TextField();

    public TagManagementView(TagService tagService, UserService userService, SecurityUtils securityUtils) {
        this.tagService = tagService;
        this.userService = userService;
        this.securityUtils = securityUtils;

        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));
        this.currentUser = userService.findByUsername(username);

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("padding", "var(--lumo-space-m)")
                .set("overflow", "hidden");

        setupUI();
        refreshGrid();
    }

    private void setupUI() {
        Span title = new Span(getTranslation("tags.title"));
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-xxl)")
                .set("font-weight", "700")
                .set("color", "var(--lumo-header-text-color)");

        searchField.setPlaceholder(getTranslation("transactions.search"));
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.EAGER);
        searchField.addValueChangeListener(e -> refreshGrid());

        Button addButton = new Button(getTranslation("tags.add"), VaadinIcon.PLUS.create(), e -> openTagDialog(new Tag()));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(searchField, addButton);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.expand(searchField);
        toolbar.setSpacing(false);
        toolbar.getStyle()
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "12px")
                .set("gap", "var(--lumo-space-s)");

        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        grid.setSizeFull();
        grid.addColumn(Tag::getName).setHeader(getTranslation("tags.name")).setSortable(true).setAutoWidth(true);

        grid.addComponentColumn(tag -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> openTagDialog(tag));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e -> {
                tagService.deleteTag(tag);
                refreshGrid();
                Notification.show(getTranslation("tags.deleted"));
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader(getTranslation("transactions.actions")).setFrozenToEnd(true).setAutoWidth(true);

        // Always use card layout
        Div card = new Div();
        card.setSizeFull();
        card.getStyle()
                .set("background-color", "var(--lumo-base-color)")
                .set("border-radius", "20px")
                .set("padding", "var(--lumo-space-l)")
                .set("box-shadow", "0 2px 12px rgba(0,0,0,0.06)")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-s)")
                .set("box-sizing", "border-box");
        card.add(toolbar, grid);
        add(title, card);
        expand(card);
    }

    private void openTagDialog(Tag tag) {
        Dialog dialog = new Dialog();
        dialog.setWidth("min(380px, 96vw)");
        dialog.setResizable(false);
        dialog.getElement().getStyle()
                .set("--lumo-border-radius-l", "20px")
                .set("overflow-x", "hidden");
        dialog.setHeaderTitle(tag.getId() == null ? getTranslation("tags.add") : getTranslation("tags.edit"));

        TextField nameField = new TextField(getTranslation("tags.name"));
        nameField.setPrefixComponent(VaadinIcon.TAG.create());
        nameField.setWidthFull();

        Binder<Tag> binder = new Binder<>(Tag.class);
        binder.forField(nameField).asRequired(getTranslation("accounts.name_required")).bind(Tag::getName, Tag::setName);
        binder.setBean(tag);

        Div body = new Div(nameField);
        body.setWidthFull();
        body.getStyle().set("padding","var(--lumo-space-m) var(--lumo-space-l)").set("box-sizing","border-box");
        dialog.add(body);

        Button saveButton = new Button(getTranslation("dialog.save"), VaadinIcon.CHECK.create(), e -> {
            if (binder.validate().isOk()) {
                tagService.saveTag(tag); refreshGrid(); dialog.close();
                Notification.show(getTranslation("tags.saved"), 2000, Notification.Position.BOTTOM_END)
                    .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_SUCCESS);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void refreshGrid() {
        grid.setItems(tagService.searchTags(searchField.getValue()));
    }
}
