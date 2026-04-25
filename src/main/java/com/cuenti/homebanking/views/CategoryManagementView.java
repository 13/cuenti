package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.Category;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.CategoryService;
import com.cuenti.homebanking.service.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Route(value = "categories", layout = MainLayout.class)
@PageTitle("Manage Categories | Cuenti")
@PermitAll
public class CategoryManagementView extends VerticalLayout {

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

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("background-color", "var(--lumo-contrast-5pct)");
        getStyle().set("overflow", "hidden");

        setupUI();
        refreshGrid();
    }

    private void setupUI() {
        H2 title = new H2(getTranslation("categories.title"));
        title.getStyle().set("margin-top", "0").set("color", "var(--lumo-primary-text-color)");

        searchField.setPlaceholder(getTranslation("transactions.search"));
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setValueChangeMode(ValueChangeMode.EAGER);
        searchField.addValueChangeListener(e -> refreshGrid());
        searchField.setClearButtonVisible(true);

        Button addButton = new Button(getTranslation("categories.add"), VaadinIcon.PLUS.create(), e -> openCategoryDialog(new Category()));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(searchField, addButton);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.expand(searchField);

        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(Category::getFullName).setHeader(getTranslation("categories.name")).setSortable(true).setAutoWidth(true);
        
        grid.addComponentColumn(category -> {
            Span span = new Span(category.getType().name());
            span.getElement().getThemeList().add("badge");
            if (category.getType() == Category.CategoryType.EXPENSE) {
                span.getElement().getThemeList().add("error");
            } else {
                span.getElement().getThemeList().add("success");
            }
            return span;
        }).setHeader(getTranslation("categories.type")).setAutoWidth(true).setSortable(true);

        grid.addComponentColumn(category -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> openCategoryDialog(category));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e -> {
                ConfirmDialog dialog = new ConfirmDialog();
                dialog.setHeader(getTranslation("categories.confirm_delete"));
                dialog.setText(getTranslation("categories.confirm_delete_text", category.getFullName()));
                dialog.setConfirmText(getTranslation("dialog.delete"));
                dialog.setCancelText(getTranslation("dialog.cancel"));
                dialog.addConfirmListener(event -> {
                    try {
                        categoryService.deleteCategory(category);
                        refreshGrid();
                        Notification.show(getTranslation("categories.deleted"));
                    } catch (Exception ex) {
                        Notification.show(getTranslation("categories.delete_error"), 5000, Notification.Position.MIDDLE);
                    }
                });
                dialog.open();
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader(getTranslation("transactions.actions")).setFrozenToEnd(true).setAutoWidth(true);

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

    private void openCategoryDialog(Category category) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(category.getId() == null ? getTranslation("categories.add") : getTranslation("categories.edit"));

        FormLayout formLayout = new FormLayout();
        TextField nameField = new TextField(getTranslation("categories.name"));
        
        RadioButtonGroup<Category.CategoryType> typeGroup = new RadioButtonGroup<>(getTranslation("categories.type"));
        typeGroup.setItems(Category.CategoryType.values());
        typeGroup.addThemeVariants(RadioGroupVariant.LUMO_HELPER_ABOVE_FIELD);
        
        ComboBox<Category> parentCombo = new ComboBox<>(getTranslation("categories.parent"));
        parentCombo.setItemLabelGenerator(Category::getFullName);
        parentCombo.setClearButtonVisible(true);

        // Load all available categories for the parent selection
        final List<Category> allAvailableCategories = new ArrayList<>(categoryService.getAllCategories());

        Binder<Category> binder = new Binder<>(Category.class);
        binder.forField(nameField).asRequired(getTranslation("accounts.name_required")).bind(Category::getName, Category::setName);
        binder.forField(typeGroup).asRequired().bind(Category::getType, Category::setType);
        binder.forField(parentCombo).bind(Category::getParent, Category::setParent);

        Runnable updateParentItems = () -> {
            Category.CategoryType selectedType = typeGroup.getValue();
            
            List<Category> filtered = allAvailableCategories.stream()
                    .filter(c -> !Objects.equals(c.getId(), category.getId()))
                    .filter(c -> selectedType == null || c.getType() == selectedType)
                    .filter(c -> !isDescendant(c, category))
                    .collect(Collectors.toList());
            
            parentCombo.setItems(filtered);
        };

        typeGroup.addValueChangeListener(e -> {
            updateParentItems.run();
            if (e.isFromClient() && parentCombo.getValue() != null &&
                parentCombo.getValue().getType() != e.getValue()) {
                parentCombo.clear();
            }
        });

        parentCombo.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                typeGroup.setValue(e.getValue().getType());
                typeGroup.setReadOnly(true);
            } else {
                typeGroup.setReadOnly(false);
            }
        });

        nameField.addValueChangeListener(e -> {
            if (!e.isFromClient()) return;
            String value = e.getValue();
            if (value != null && value.contains(":")) {
                String[] parts = value.split(":", 2);
                if (parts.length == 2) {
                    String parentName = parts[0].trim();
                    String childName = parts[1].trim();

                    Category.CategoryType categoryType = typeGroup.getValue();
                    if (categoryType == null) {
                        categoryType = Category.CategoryType.EXPENSE;
                        typeGroup.setValue(categoryType);
                    }

                    Category.CategoryType finalCategoryType = categoryType;
                    Category parentCategory = allAvailableCategories.stream()
                            .filter(c -> c.getName().equalsIgnoreCase(parentName) && c.getParent() == null && c.getType() == finalCategoryType)
                            .findFirst()
                            .orElse(null);

                    if (parentCategory == null) {
                        parentCategory = new Category();
                        parentCategory.setName(parentName);
                        parentCategory.setType(finalCategoryType);
                        parentCategory.setUser(currentUser);
                        parentCategory.setParent(null);
                        parentCategory = categoryService.saveCategory(parentCategory);
                        allAvailableCategories.add(parentCategory);
                        updateParentItems.run();
                        Notification.show(getTranslation("categories.parent_created") + ": " + parentName, 3000, Notification.Position.MIDDLE);
                    }

                    nameField.setValue(childName);
                    parentCombo.setValue(parentCategory);
                }
            }
        });

        // Initialize items based on current type (if any)
        if (category.getType() != null) {
            typeGroup.setValue(category.getType());
        }
        updateParentItems.run();
        
        // Then bind the bean
        binder.setBean(category);

        // Ensure parent is correctly selected if instance came from grid
        if (category.getParent() != null) {
            final Long parentId = category.getParent().getId();
            allAvailableCategories.stream()
                .filter(c -> c.getId().equals(parentId))
                .findFirst()
                .ifPresent(parentCombo::setValue);
            typeGroup.setReadOnly(true);
        }

        formLayout.add(nameField, typeGroup, parentCombo);
        formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        Button saveButton = new Button(getTranslation("dialog.save"), e -> {
            if (binder.validate().isOk()) {
                try {
                    categoryService.saveCategory(category);
                    refreshGrid();
                    dialog.close();
                    Notification.show(getTranslation("categories.saved"));
                } catch (IllegalArgumentException ex) {
                    Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE);
                }
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());

        dialog.add(formLayout);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
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
