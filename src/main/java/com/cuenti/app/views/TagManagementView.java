package com.cuenti.app.views;

import com.cuenti.app.model.Tag;
import com.cuenti.app.model.User;
import com.cuenti.app.security.SecurityUtils;
import com.cuenti.app.service.TagService;
import com.cuenti.app.service.UserService;
import com.vaadin.flow.component.button.Button;
import com.cuenti.app.views.components.DeleteConfirm;
import com.cuenti.app.views.components.UiNotifier;

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

        addClassName("page-scroll");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle()
                .set("padding", "var(--vaadin-gap-m)")
                .set("overflow", "hidden");

        setupUI();
        refreshGrid();
    }

    private void setupUI() {
        Span title = new Span(getTranslation("tags.title"));
        title.addClassName("page-title");

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
                .set("padding", "var(--vaadin-gap-s) var(--vaadin-gap-m)")
                .set("background", "var(--cuenti-surface-muted)")
                .set("border-radius", "var(--vaadin-radius-l)")
                .set("gap", "var(--vaadin-gap-s)");

        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        grid.addItemDoubleClickListener(e -> openTagDialog(e.getItem()));
        grid.setSizeFull();
        grid.addColumn(Tag::getName).setHeader(getTranslation("tags.name")).setSortable(true).setAutoWidth(true);

        grid.addComponentColumn(tag -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> openTagDialog(tag));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e ->
                DeleteConfirm.show(
                    getTranslation("dialog.confirm_delete"),
                    getTranslation("dialog.confirm_delete_message") + " \"" + tag.getName() + "\"?",
                    getTranslation("dialog.delete"),
                    getTranslation("dialog.cancel"),
                    getTranslation("error.delete_failed"),
                    () -> {
                        tagService.deleteTag(tag);
                        refreshGrid();
                        UiNotifier.success(getTranslation("tags.deleted"));
                    }));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader(getTranslation("transactions.actions")).setFrozenToEnd(true).setAutoWidth(true);

        // Always use card layout
        Div card = new Div();
        card.setSizeFull();
        card.addClassName("card");
        card.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--vaadin-gap-s)")
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
        body.getStyle().set("padding","var(--vaadin-gap-m) var(--vaadin-gap-l)").set("box-sizing","border-box");
        dialog.add(body);

        Button saveButton = new Button(getTranslation("dialog.save"), VaadinIcon.CHECK.create(), e -> {
            if (binder.validate().isOk()) {
                tagService.saveTag(tag); refreshGrid(); dialog.close();
                Notification.show(getTranslation("tags.saved"), 2000, Notification.Position.BOTTOM_END)
                    .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_SUCCESS);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickShortcut(com.vaadin.flow.component.Key.ENTER);
        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
        nameField.focus();
    }

    private void refreshGrid() {
        grid.setItems(tagService.searchTags(searchField.getValue()));
    }
}
