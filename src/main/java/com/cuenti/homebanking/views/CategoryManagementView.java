package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.Category;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.CategoryService;
import com.cuenti.homebanking.service.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
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

import java.util.List;
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

        // Always use card layout
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

        Binder<Category> binder = new Binder<>(Category.class);
        binder.forField(nameField).asRequired(getTranslation("accounts.name_required")).bind(Category::getName, Category::setName);
        binder.forField(typeGroup).asRequired().bind(Category::getType, Category::setType);
        binder.bind(parentCombo, Category::getParent, Category::setParent);
        binder.setBean(category);

        // Update parent combo items based on selected type
        Runnable updateParentItems = () -> {
            Category.CategoryType selectedType = typeGroup.getValue();
            if (selectedType != null) {
                parentCombo.setItems(categoryService.getAllCategories().stream()
                        .filter(c -> !c.equals(category) && c.getType() == selectedType && c.getParent() == null)
                        .collect(Collectors.toList()));
            } else {
                parentCombo.setItems(List.of());
            }
        };

        // Initialize parent items
        updateParentItems.run();

        // Update parent items when type changes
        typeGroup.addValueChangeListener(e -> {
            updateParentItems.run();
            // Clear parent if type changed
            if (!e.isFromClient() && parentCombo.getValue() != null &&
                parentCombo.getValue().getType() != e.getValue()) {
                parentCombo.clear();
            }
        });

        // When parent is selected, set the type to match parent's type
        parentCombo.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                typeGroup.setValue(e.getValue().getType());
                typeGroup.setReadOnly(true);
            } else {
                typeGroup.setReadOnly(false);
            }
        });

        // Add listener to parse "Parent:Child" format
        nameField.addValueChangeListener(e -> {
            String value = e.getValue();
            if (value != null && value.contains(":")) {
                String[] parts = value.split(":", 2);
                if (parts.length == 2) {
                    String parentName = parts[0].trim();
                    String childName = parts[1].trim();

                    // Get current type or default to EXPENSE
                    Category.CategoryType categoryType = typeGroup.getValue();
                    if (categoryType == null) {
                        categoryType = Category.CategoryType.EXPENSE;
                        typeGroup.setValue(categoryType);
                    }

                    // Find or create parent category
                    Category.CategoryType finalCategoryType = categoryType;
                    Category parentCategory = categoryService.getAllCategories().stream()
                            .filter(c -> c.getName().equals(parentName) && c.getParent() == null && c.getType() == finalCategoryType)
                            .findFirst()
                            .orElse(null);

                    if (parentCategory == null) {
                        // Create new parent category with the same type
                        parentCategory = new Category();
                        parentCategory.setName(parentName);
                        parentCategory.setType(finalCategoryType);
                        parentCategory.setUser(currentUser);
                        parentCategory.setParent(null);
                        parentCategory = categoryService.saveCategory(parentCategory);

                        // Refresh the parent combo box items to include the new parent
                        updateParentItems.run();

                        Notification.show(getTranslation("categories.parent_created") + ": " + parentName, 3000, Notification.Position.MIDDLE);
                    }

                    // Set the child name and parent
                    nameField.setValue(childName);
                    parentCombo.setValue(parentCategory);
                }
            }
        });

        formLayout.add(nameField, typeGroup, parentCombo);
        formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        Button saveButton = new Button(getTranslation("dialog.save"), e -> {
            if (binder.validate().isOk()) {
                categoryService.saveCategory(category);
                refreshGrid();
                dialog.close();
                Notification.show(getTranslation("categories.saved"));
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());

        dialog.add(formLayout);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void refreshGrid() {
        String filter = searchField.getValue().toLowerCase();
        List<Category> categories = categoryService.getAllCategories().stream()
                .filter(c -> c.getFullName().toLowerCase().contains(filter))
                .collect(Collectors.toList());
        grid.setItems(categories);
    }
}
