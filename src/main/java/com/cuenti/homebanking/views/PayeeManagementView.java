package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.Category;
import com.cuenti.homebanking.model.Payee;
import com.cuenti.homebanking.model.Transaction;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.CategoryService;
import com.cuenti.homebanking.service.PayeeService;
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
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "payees", layout = MainLayout.class)
@PageTitle("Manage Payees | Cuenti")
@PermitAll
public class PayeeManagementView extends VerticalLayout {

    private final PayeeService payeeService;
    private final CategoryService categoryService;
    private final UserService userService;
    private final SecurityUtils securityUtils;
    private final User currentUser;

    private final Grid<Payee> grid = new Grid<>(Payee.class, false);
    private final TextField searchField = new TextField();

    public PayeeManagementView(PayeeService payeeService, CategoryService categoryService, 
                               UserService userService, SecurityUtils securityUtils) {
        this.payeeService = payeeService;
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
        H2 title = new H2(getTranslation("payees.title"));
        title.getStyle().set("margin-top", "0").set("color", "var(--lumo-primary-text-color)");
        
        searchField.setPlaceholder(getTranslation("payees.search"));
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.EAGER);
        searchField.addValueChangeListener(e -> refreshGrid());

        Button addButton = new Button(getTranslation("payees.add"), VaadinIcon.PLUS.create(), e -> openPayeeDialog(new Payee()));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(searchField, addButton);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.expand(searchField);

        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(Payee::getName).setHeader(getTranslation("payees.name")).setSortable(true).setAutoWidth(true);
        grid.addColumn(Payee::getNotes).setHeader(getTranslation("payees.notes")).setAutoWidth(true).setSortable(true);
        grid.addColumn(p -> p.getDefaultCategory() != null ? p.getDefaultCategory().getFullName() : "").setHeader(getTranslation("payees.default_category")).setAutoWidth(true).setSortable(true);
        grid.addColumn(p -> p.getDefaultPaymentMethod() != null ? p.getDefaultPaymentMethod().getLabel() : "").setHeader(getTranslation("payees.default_payment")).setAutoWidth(true).setSortable(true);

        grid.addComponentColumn(payee -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> openPayeeDialog(payee));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            editBtn.setTooltipText(getTranslation("transactions.edit"));
            
            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e -> {
                payeeService.deletePayee(payee);
                refreshGrid();
                Notification.show(getTranslation("payees.deleted"));
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            deleteBtn.setTooltipText(getTranslation("transactions.delete"));
            
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

    private void openPayeeDialog(Payee payee) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(payee.getId() == null ? getTranslation("payees.add") : getTranslation("payees.edit"));

        FormLayout formLayout = new FormLayout();
        TextField name = new TextField(getTranslation("payees.name"));
        name.setPrefixComponent(VaadinIcon.USER.create());
        
        TextField notes = new TextField(getTranslation("payees.notes"));
        
        ComboBox<Category> defaultCategory = new ComboBox<>(getTranslation("payees.default_category"));
        defaultCategory.setItems(categoryService.getAllCategories());
        defaultCategory.setItemLabelGenerator(Category::getFullName);
        
        // Add new category button - using prefix since setSuffixComponent isn't available in this Vaadin version
        Button addCategoryBtn = new Button(VaadinIcon.PLUS.create(), e -> {
            Notification.show("Navigate to Categories to add new ones");
        });
        addCategoryBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        defaultCategory.setPrefixComponent(addCategoryBtn);

        ComboBox<Transaction.PaymentMethod> paymentMethodCombo = new ComboBox<>(getTranslation("payees.default_payment"));
        paymentMethodCombo.setItems(Transaction.PaymentMethod.values());
        paymentMethodCombo.setItemLabelGenerator(Transaction.PaymentMethod::getLabel);

        Binder<Payee> binder = new Binder<>(Payee.class);
        binder.forField(name).asRequired(getTranslation("accounts.name_required")).bind(Payee::getName, Payee::setName);
        binder.bind(notes, Payee::getNotes, Payee::setNotes);
        binder.bind(defaultCategory, Payee::getDefaultCategory, Payee::setDefaultCategory);
        binder.bind(paymentMethodCombo, Payee::getDefaultPaymentMethod, Payee::setDefaultPaymentMethod);
        binder.setBean(payee);

        formLayout.add(name, notes, defaultCategory, paymentMethodCombo);
        formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        Button saveButton = new Button(getTranslation("dialog.save"), e -> {
            if (binder.validate().isOk()) {
                payeeService.savePayee(payee);
                refreshGrid();
                dialog.close();
                Notification.show(getTranslation("payees.saved"));
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());

        dialog.add(formLayout);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void refreshGrid() {
        grid.setItems(payeeService.searchPayees(searchField.getValue()));
    }
}
