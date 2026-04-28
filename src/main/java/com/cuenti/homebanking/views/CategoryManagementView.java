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
        Span title = new Span(getTranslation("categories.title"));
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-xxl)")
                .set("font-weight", "700")
                .set("color", "var(--lumo-header-text-color)");

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
        toolbar.setSpacing(false);
        toolbar.getStyle()
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "12px")
                .set("gap", "var(--lumo-space-s)");

        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        grid.addColumn(Category::getFullName).setHeader(getTranslation("categories.name")).setSortable(true).setAutoWidth(true);
        
        grid.addComponentColumn(category -> {
            boolean isExpense = category.getType() == Category.CategoryType.EXPENSE;
            String color = isExpense ? "var(--lumo-error-color)" : "var(--lumo-success-color)";
            Span span = new Span(getTranslation("category.type." + category.getType().name().toLowerCase()));
            span.getStyle().set("font-size","10px").set("font-weight","700").set("letter-spacing","0.05em")
                    .set("padding","2px 8px").set("border-radius","99px")
                    .set("background", color + "1a").set("color", color);
            return span;
        }).setHeader(getTranslation("categories.type")).setAutoWidth(true).setSortable(true);

        grid.addComponentColumn(category -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> openCategoryDialog(category));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

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
                         Notification.show(getTranslation("categories.deleted"));
                     } catch (Exception ex) {
                         Notification.show(getTranslation("error.delete_failed"), 5000, Notification.Position.MIDDLE);
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

    private void openCategoryDialog(Category category) {
        Dialog dialog = new Dialog();
        dialog.setWidth("min(460px, 96vw)");
        dialog.setResizable(false);
        dialog.getElement().getStyle()
                .set("--lumo-border-radius-l", "20px")
                .set("overflow-x", "hidden");
        dialog.setHeaderTitle(category.getId() == null ? getTranslation("categories.add") : getTranslation("categories.edit"));

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
        body.getStyle().set("display","flex").set("flex-direction","column").set("gap","var(--lumo-space-s)")
                .set("padding","var(--lumo-space-m) var(--lumo-space-l)").set("box-sizing","border-box");
        body.add(nameField, typeGroup, parentCombo);
        dialog.add(body);

        Button saveButton = new Button(getTranslation("dialog.save"), VaadinIcon.CHECK.create(), e -> {
            if (binder.validate().isOk()) {
                try {
                    categoryService.saveCategory(category); refreshGrid(); dialog.close();
                    Notification.show(getTranslation("categories.saved"), 2000, Notification.Position.BOTTOM_END)
                    .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_SUCCESS);
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
