package com.cuenti.homebanking.views.categories;

import com.cuenti.homebanking.data.Category;
import com.cuenti.homebanking.data.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.services.CategoryService;
import com.cuenti.homebanking.services.UserService;
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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

import java.util.List;
import java.util.stream.Collectors;

@PageTitle("Categories")
@Route("categories")
@Menu(order = 7, icon = LineAwesomeIconUrl.FOLDER_SOLID)
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

        setSpacing(true);
        setPadding(true);
        setMaxWidth("1200px");
        getStyle().set("margin", "0 auto");

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
                dialog.setHeader("Confirm Delete");
                dialog.setText("Are you sure you want to delete " + category.getFullName() + "?");
                dialog.setConfirmText("Delete");
                dialog.setCancelText(getTranslation("dialog.cancel"));
                dialog.addConfirmListener(event -> {
                    try {
                        categoryService.deleteCategory(category);
                        refreshGrid();
                        Notification.show(getTranslation("categories.deleted"));
                    } catch (Exception ex) {
                        Notification.show("Error deleting category", 5000, Notification.Position.MIDDLE);
                    }
                });
                dialog.open();
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

    private void openCategoryDialog(Category category) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(category.getId() == null ? getTranslation("categories.add") : getTranslation("categories.edit"));

        FormLayout formLayout = new FormLayout();
        TextField nameField = new TextField(getTranslation("categories.name"));

        RadioButtonGroup<Category.CategoryType> typeGroup = new RadioButtonGroup<>(getTranslation("categories.type"));
        typeGroup.setItems(Category.CategoryType.values());

        ComboBox<Category> parentCombo = new ComboBox<>(getTranslation("categories.parent"));
        parentCombo.setItemLabelGenerator(Category::getFullName);
        parentCombo.setClearButtonVisible(true);

        Binder<Category> binder = new Binder<>(Category.class);
        binder.forField(nameField).asRequired("Name is required").bind(Category::getName, Category::setName);
        binder.forField(typeGroup).asRequired().bind(Category::getType, Category::setType);
        binder.bind(parentCombo, Category::getParent, Category::setParent);
        binder.setBean(category);

        // Update parent combo items based on selected type
        typeGroup.addValueChangeListener(e -> {
            Category.CategoryType selectedType = e.getValue();
            if (selectedType != null) {
                parentCombo.setItems(categoryService.getAllCategories().stream()
                        .filter(c -> !c.equals(category) && c.getType() == selectedType && c.getParent() == null)
                        .collect(Collectors.toList()));
            } else {
                parentCombo.setItems(List.of());
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
        String filter = searchField.getValue();
        if (filter == null || filter.isEmpty()) {
            grid.setItems(categoryService.getAllCategories());
        } else {
            grid.setItems(categoryService.getAllCategories().stream()
                    .filter(c -> c.getFullName().toLowerCase().contains(filter.toLowerCase()))
                    .collect(Collectors.toList()));
        }
    }
}
