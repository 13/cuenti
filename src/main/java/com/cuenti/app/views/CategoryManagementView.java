package com.cuenti.app.views;

import com.cuenti.app.model.Category;
import com.cuenti.app.model.User;
import com.cuenti.app.security.SecurityUtils;
import com.cuenti.app.service.CategoryService;
import com.cuenti.app.service.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Route(value = "categories", layout = MainLayout.class)
@PermitAll
public class CategoryManagementView extends VerticalLayout implements HasDynamicTitle {

    @Override
    public String getPageTitle() {
        return getTranslation("categories.title") + " | " + getTranslation("app.name");
    }


    private final CategoryService categoryService;
    private final UserService userService;
    private final SecurityUtils securityUtils;
    private final User currentUser;

    private final Grid<Category> grid = new Grid<>(Category.class, false);
    private final TextField searchField = new TextField();

    public CategoryManagementView(CategoryService categoryService, UserService userService, SecurityUtils securityUtils) {
        this.categoryService = categoryService;
        this.userService = userService;
        this.securityUtils = securityUtils;

        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));
        this.currentUser = userService.findByUsername(username);

        addClassNames("page-scroll", "page-shell");
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
        Span title = new Span(getTranslation("categories.title"));
        title.addComponentAsFirst(VaadinIcon.SITEMAP.create());
        title.addClassName("page-title");

        searchField.setPlaceholder(getTranslation("transactions.search"));
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setValueChangeMode(ValueChangeMode.EAGER);
        searchField.addValueChangeListener(e -> refreshGrid());
        searchField.setClearButtonVisible(true);

        Button addButton = new Button(getTranslation("categories.add"), VaadinIcon.PLUS.create(), e -> openCategoryDialog(new Category()));
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
                new com.vaadin.flow.component.button.Button(getTranslation("empty.hint"), e -> openCategoryDialog(new Category()));
        emptyAdd.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_TERTIARY);
        grid.setEmptyStateComponent(new com.cuenti.app.views.components.EmptyStateNotice(
                VaadinIcon.SITEMAP, getTranslation("empty.title"), null, emptyAdd));
        grid.addItemDoubleClickListener(e -> openCategoryDialog(e.getItem()));

        // Demo-style per-column filter: search lives in the grid header
        searchField.setWidthFull();
        grid.addAttachListener(e -> {
            if (grid.getHeaderRows().size() < 2 && !grid.getColumns().isEmpty()) {
                grid.appendHeaderRow().getCell(grid.getColumns().get(0)).setComponent(searchField);
            }
        });
        grid.addColumn(Category::getFullName).setHeader(getTranslation("categories.name")).setSortable(true).setAutoWidth(true);
        
        grid.addComponentColumn(category -> {
            boolean isExpense = category.getType() == Category.CategoryType.EXPENSE;
            String color = isExpense ? "var(--aura-red)" : "var(--aura-green)";
            Span span = new Span(getTranslation("category.type." + category.getType().name().toLowerCase()));
            span.getStyle().set("font-size","10px").set("font-weight","700").set("letter-spacing","0.05em")
                    .set("padding","2px 8px").set("border-radius","99px")
                    .set("background", color + "1a").set("color", color);
            return span;
        }).setHeader(getTranslation("categories.type")).setAutoWidth(true).setSortable(true);

        grid.addComponentColumn(category -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> openCategoryDialog(category));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            editBtn.setTooltipText(getTranslation("transactions.edit"));
            editBtn.getElement().setAttribute("aria-label", getTranslation("transactions.edit"));

             Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e -> {
                 ConfirmDialog dialog = new ConfirmDialog();
                 dialog.setHeader(getTranslation("dialog.confirm_delete"));
                 dialog.setText(getTranslation("categories.confirm_delete_message", category.getFullName()));
                 dialog.setConfirmText(getTranslation("dialog.confirm"));
                 dialog.setCancelText(getTranslation("dialog.cancel"));
                 dialog.addConfirmListener(event -> {
                     try {
                         categoryService.deleteCategory(category);
                         refreshGrid();
                         com.cuenti.app.views.components.UiNotifier.successWithAction(
                                 getTranslation("categories.deleted"),
                                 getTranslation("action.undo"), () -> {
                                     category.setId(null);
                                     categoryService.saveCategory(category);
                                     refreshGrid();
                                 });
                     } catch (Exception ex) {
                         com.cuenti.app.views.components.UiNotifier.error(getTranslation("error.delete_failed"));
                     }
                 });
                 dialog.open();
             });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            deleteBtn.setTooltipText(getTranslation("transactions.delete"));
            deleteBtn.getElement().setAttribute("aria-label", getTranslation("transactions.delete"));

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader(getTranslation("transactions.actions")).setFrozenToEnd(true).setAutoWidth(true);

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

    private void openCategoryDialog(Category category) {
        Dialog dialog = new Dialog();
        dialog.setCloseOnOutsideClick(false);
        dialog.setWidth("min(460px, 96vw)");
        dialog.setResizable(false);
        dialog.getElement().getStyle()
                .set("overflow-x", "hidden");
        dialog.setHeaderTitle(category.getId() == null ? getTranslation("categories.add") : getTranslation("categories.edit"));
        com.vaadin.flow.component.icon.Icon headerIcon = VaadinIcon.SITEMAP.create();
        headerIcon.addClassName("dialog-header-icon");
        dialog.getHeader().add(headerIcon);

        TextField nameField = new TextField(getTranslation("categories.name"));
        nameField.setWidthFull();

        RadioButtonGroup<Category.CategoryType> typeGroup = new RadioButtonGroup<>(getTranslation("categories.type"));
        typeGroup.setItems(Category.CategoryType.values());
        // Show translated labels for the enum values (e.g. "category.type.expense")
        typeGroup.setItemLabelGenerator(ct -> getTranslation("category.type." + ct.name().toLowerCase()));
        typeGroup.addThemeVariants(RadioGroupVariant.LUMO_HELPER_ABOVE_FIELD);

        ComboBox<Category> parentCombo = new ComboBox<>(getTranslation("categories.parent"));
        parentCombo.setItemLabelGenerator(Category::getFullName);
        parentCombo.setClearButtonVisible(true);
        parentCombo.setWidthFull();

        final List<Category> allAvailableCategories = new ArrayList<>(categoryService.getAllCategories());

        Binder<Category> binder = new Binder<>(Category.class);
        binder.forField(nameField).asRequired(getTranslation("accounts.name_required")).bind(Category::getName, Category::setName);
        binder.forField(typeGroup).asRequired().bind(Category::getType, Category::setType);
        binder.forField(parentCombo).bind(Category::getParent, Category::setParent);

        Runnable updateParentItems = () -> {
            Category.CategoryType selectedType = typeGroup.getValue();
            List<Category> filtered = allAvailableCategories.stream()
                    .filter(c -> !java.util.Objects.equals(c.getId(), category.getId()))
                    .filter(c -> selectedType == null || c.getType() == selectedType)
                    .filter(c -> !isDescendant(c, category))
                    .collect(Collectors.toList());
            parentCombo.setItems(filtered);
        };

        typeGroup.addValueChangeListener(e -> {
            updateParentItems.run();
            if (e.isFromClient() && parentCombo.getValue() != null && parentCombo.getValue().getType() != e.getValue())
                parentCombo.clear();
        });

        parentCombo.addValueChangeListener(e -> {
            if (e.getValue() != null) { typeGroup.setValue(e.getValue().getType()); typeGroup.setReadOnly(true); }
            else typeGroup.setReadOnly(false);
        });

        nameField.addValueChangeListener(e -> {
            if (!e.isFromClient()) return;
            String value = e.getValue();
            if (value != null && value.contains(":")) {
                String[] parts = value.split(":", 2);
                if (parts.length == 2) {
                    String parentName = parts[0].trim(); String childName = parts[1].trim();
                    Category.CategoryType ct = typeGroup.getValue() != null ? typeGroup.getValue() : Category.CategoryType.EXPENSE;
                    typeGroup.setValue(ct);
                    Category parentCat = allAvailableCategories.stream()
                            .filter(c -> c.getName().equalsIgnoreCase(parentName) && c.getParent() == null && c.getType() == ct)
                            .findFirst().orElse(null);
                    if (parentCat == null) {
                        parentCat = categoryService.saveCategory(Category.builder().name(parentName).type(ct).user(currentUser).parent(null).build());
                        allAvailableCategories.add(parentCat);
                        updateParentItems.run();
                        Notification.show(getTranslation("categories.parent_created") + ": " + parentName, 3000, Notification.Position.MIDDLE);
                    }
                    nameField.setValue(childName); parentCombo.setValue(parentCat);
                }
            }
        });

        if (category.getType() != null) typeGroup.setValue(category.getType());
        updateParentItems.run();
        binder.setBean(category);

        if (category.getParent() != null) {
            final Long parentId = category.getParent().getId();
            allAvailableCategories.stream().filter(c -> c.getId().equals(parentId)).findFirst().ifPresent(parentCombo::setValue);
            typeGroup.setReadOnly(true);
        }

        Div body = new Div();
        body.setWidthFull();
        body.getStyle().set("display","flex").set("flex-direction","column").set("gap","var(--vaadin-gap-s)")
                .set("padding","var(--vaadin-gap-m) var(--vaadin-gap-l)").set("box-sizing","border-box");
        body.add(nameField, typeGroup, parentCombo);
        dialog.add(body);

        Button saveButton = new Button(getTranslation("dialog.save"), e -> {
            if (binder.validate().isOk()) {
                try {
                    categoryService.saveCategory(category); refreshGrid(); dialog.close();
                    com.cuenti.app.views.components.UiNotifier.success(getTranslation("categories.saved"));
                } catch (IllegalArgumentException ex) {
                    Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE);
                }
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
        nameField.focus();
    }

    private boolean isDescendant(Category potentialParent, Category category) {
        if (category == null || category.getId() == null || potentialParent == null) return false;
        Category current = potentialParent.getParent();
        while (current != null) {
            if (Objects.equals(current.getId(), category.getId())) return true;
            current = current.getParent();
        }
        return false;
    }

    private void refreshGrid() {
        String filter = searchField.getValue().toLowerCase();
        List<Category> categories = categoryService.getAllCategories().stream()
                .filter(c -> c.getFullName().toLowerCase().contains(filter))
                .collect(Collectors.toList());
        grid.setItems(categories);
    }
}
