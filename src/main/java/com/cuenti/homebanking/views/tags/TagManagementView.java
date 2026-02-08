package com.cuenti.homebanking.views.tags;

import com.cuenti.homebanking.data.Tag;
import com.cuenti.homebanking.data.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.services.TagService;
import com.cuenti.homebanking.services.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

@PageTitle("Tags")
@Route("tags")
@Menu(order = 8, icon = LineAwesomeIconUrl.TAGS_SOLID)
@PermitAll
public class TagManagementView extends VerticalLayout {

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

        setSpacing(true);
        setPadding(true);
        setMaxWidth("1200px");
        getStyle().set("margin", "0 auto");

        setupUI();
        refreshGrid();
    }

    private void setupUI() {
        H2 title = new H2(getTranslation("tags.title"));
        title.getStyle().set("margin-top", "0").set("color", "var(--lumo-primary-text-color)");

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

        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
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
        }).setHeader("Actions").setFrozenToEnd(true).setAutoWidth(true);

        Div card = new Div();
        card.setSizeFull();
        card.getStyle()
                .set("background-color", "var(--lumo-base-color)")
                .set("border-radius", "16px")
                .set("padding", "var(--lumo-space-l)")
                .set("box-shadow", "var(--lumo-box-shadow-m)")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("box-sizing", "border-box");
        card.add(toolbar, grid);
        add(title, card);
        expand(card);
    }

    private void openTagDialog(Tag tag) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(tag.getId() == null ? getTranslation("tags.add") : getTranslation("tags.edit"));

        FormLayout formLayout = new FormLayout();
        TextField nameField = new TextField(getTranslation("tags.name"));
        nameField.setPrefixComponent(VaadinIcon.TAG.create());

        Binder<Tag> binder = new Binder<>(Tag.class);
        binder.forField(nameField).asRequired("Name is required").bind(Tag::getName, Tag::setName);
        binder.setBean(tag);

        formLayout.add(nameField);
        formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        Button saveButton = new Button(getTranslation("dialog.save"), e -> {
            if (binder.validate().isOk()) {
                tagService.saveTag(tag);
                refreshGrid();
                dialog.close();
                Notification.show(getTranslation("tags.saved"));
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());

        dialog.add(formLayout);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void refreshGrid() {
        grid.setItems(tagService.searchTags(searchField.getValue()));
    }
}
