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
    final TextField searchField = new TextField(); // package-visible for tests

    public TagManagementView(TagService tagService, UserService userService, SecurityUtils securityUtils) {
        this.tagService = tagService;
        this.userService = userService;
        this.securityUtils = securityUtils;

        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));
        this.currentUser = userService.findByUsername(username);

        addClassNames("page-scroll", "page-shell");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        setupUI();
        refreshGrid();
    }

    private void setupUI() {
        Span title = new Span(getTranslation("tags.title"));
        title.addComponentAsFirst(VaadinIcon.TAGS.create());
        title.addClassName("page-title");

        searchField.setPlaceholder(getTranslation("transactions.search"));
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.EAGER);
        searchField.addValueChangeListener(e -> refreshGrid());

        Button addButton = new Button(getTranslation("tags.add"), VaadinIcon.PLUS.create(), e -> openTagDialog(new Tag()));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(addButton);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(JustifyContentMode.END);
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setSpacing(false);
        toolbar.addClassName("card-toolbar");
        toolbar.getStyle().set("gap", "var(--vaadin-gap-s)");

        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        com.vaadin.flow.component.button.Button emptyAdd =
                new com.vaadin.flow.component.button.Button(getTranslation("empty.hint"), e -> openTagDialog(new Tag()));
        emptyAdd.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_TERTIARY);
        grid.setEmptyStateComponent(new com.cuenti.app.views.components.EmptyStateNotice(
                VaadinIcon.TAGS, getTranslation("empty.title"), null, emptyAdd));
        grid.addItemDoubleClickListener(e -> openTagDialog(e.getItem()));

        // Demo-style per-column filter: search lives in the grid header
        searchField.setWidthFull();
        grid.addAttachListener(e -> {
            if (grid.getHeaderRows().size() < 2 && !grid.getColumns().isEmpty()) {
                grid.appendHeaderRow().getCell(grid.getColumns().get(0)).setComponent(searchField);
            }
        });
        grid.setSizeFull();
        grid.addColumn(Tag::getName).setHeader(getTranslation("tags.name")).setSortable(true).setAutoWidth(true);

        grid.addComponentColumn(tag -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> openTagDialog(tag));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            editBtn.setTooltipText(getTranslation("transactions.edit"));
            editBtn.getElement().setAttribute("aria-label", getTranslation("transactions.edit"));

            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e -> {
                TagService.TagUsage usage = tagService.usage(tag.getName());
                String message = getTranslation("dialog.confirm_delete_message") + " \"" + tag.getName() + "\"?"
                        + (usage.total() > 0
                            ? " " + getTranslation("tags.delete_usage", usage.transactions(), usage.schedules(), usage.payees())
                            : "");
                DeleteConfirm.show(
                    getTranslation("dialog.confirm_delete"),
                    message,
                    getTranslation("dialog.delete"),
                    getTranslation("dialog.cancel"),
                    getTranslation("error.delete_failed"),
                    () -> {
                        TagService.TagRemoval removal = tagService.deleteEverywhere(tag);
                        refreshGrid();
                        UiNotifier.successWithAction(getTranslation("tags.deleted"),
                                getTranslation("action.undo"), () -> {
                                    tagService.restore(removal);
                                    refreshGrid();
                                });
                    });
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            deleteBtn.setTooltipText(getTranslation("transactions.delete"));
            deleteBtn.getElement().setAttribute("aria-label", getTranslation("transactions.delete"));

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader(getTranslation("transactions.actions")).setFrozenToEnd(true).setAutoWidth(true);

        // Always use card layout
        Div card = new Div();
        card.setSizeFull();
        card.addClassName("card");
        card.addClassName("card--flex");
        card.add(toolbar, grid);
        add(title, card);
        expand(card);
    }

    private void openTagDialog(Tag tag) {
        Dialog dialog = new Dialog();
        dialog.setCloseOnOutsideClick(false);
        dialog.setWidth("min(380px, 96vw)");
        dialog.setResizable(false);
        dialog.getElement().getStyle()
                .set("overflow-x", "hidden");
        dialog.setHeaderTitle(tag.getId() == null ? getTranslation("tags.add") : getTranslation("tags.edit"));
        com.vaadin.flow.component.icon.Icon headerIcon = VaadinIcon.TAGS.create();
        headerIcon.addClassName("dialog-header-icon");
        dialog.getHeader().add(headerIcon);

        TextField nameField = new TextField(getTranslation("tags.name"));
        nameField.setPrefixComponent(VaadinIcon.TAG.create());
        nameField.setWidthFull();

        nameField.setValue(tag.getName() != null ? tag.getName() : "");
        if (tag.getId() != null) {
            TagService.TagUsage usage = tagService.usage(tag.getName());
            nameField.setHelperText(getTranslation("tags.usage_helper", usage.transactions(), usage.schedules(), usage.payees()));
        }

        Div body = new Div(nameField);
        body.setWidthFull();
        body.addClassName("dialog-body");
        dialog.add(body);

        Button saveButton = new Button(getTranslation("dialog.save"), e -> {
            String name = nameField.getValue() == null ? "" : nameField.getValue().trim();
            if (name.isEmpty()) {
                nameField.setErrorMessage(getTranslation("accounts.name_required"));
                nameField.setInvalid(true);
                return;
            }
            Tag other = tagService.getAllTags().stream()
                    .filter(t -> !java.util.Objects.equals(t.getId(), tag.getId())
                            && com.cuenti.app.util.TagNames.same(t.getName(), name))
                    .findFirst().orElse(null);
            if (tag.getId() == null) {
                if (other != null) {
                    nameField.setErrorMessage(getTranslation("tags.name_exists"));
                    nameField.setInvalid(true);
                    return;
                }
                tag.setName(name);
                tagService.saveTag(tag);
                refreshGrid(); dialog.close();
                UiNotifier.success(getTranslation("tags.saved"));
            } else if (other != null) {
                // Renaming onto an existing name merges the two tags.
                com.vaadin.flow.component.confirmdialog.ConfirmDialog merge =
                        new com.vaadin.flow.component.confirmdialog.ConfirmDialog();
                merge.setHeader(getTranslation("tags.merge_title"));
                merge.setText(getTranslation("tags.merge_text", tag.getName(), other.getName()));
                merge.setCancelable(true);
                merge.setCancelText(getTranslation("dialog.cancel"));
                merge.setConfirmText(getTranslation("tags.merge_confirm"));
                merge.addConfirmListener(ev -> {
                    tagService.rename(tag, other.getName());
                    refreshGrid(); dialog.close();
                    UiNotifier.success(getTranslation("tags.merged", other.getName()));
                });
                add(merge);
                merge.open();
            } else {
                tagService.rename(tag, name);
                refreshGrid(); dialog.close();
                UiNotifier.success(getTranslation("tags.saved"));
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
