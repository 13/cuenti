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

@Route(value = "payees", layout = MainLayout.class)
@PermitAll
public class PayeeManagementView extends VerticalLayout implements HasDynamicTitle {

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
        setPadding(false);
        setSpacing(false);
        getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("padding", "var(--lumo-space-m)")
                .set("overflow", "hidden");

        setupUI();
        refreshGrid();
    }

    @Override
    public String getPageTitle() {
        return getTranslation("payees.title") + " | " + getTranslation("app.name");
    }

    private void setupUI() {
        Span title = new Span(getTranslation("payees.title"));
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-xxl)")
                .set("font-weight", "700")
                .set("color", "var(--lumo-header-text-color)");

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
        toolbar.setSpacing(false);
        toolbar.getStyle()
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "12px")
                .set("gap", "var(--lumo-space-s)");

        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
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

    private void openPayeeDialog(Payee payee) {
        Dialog dialog = new Dialog();
        dialog.setWidth("min(500px, 96vw)");
        dialog.setResizable(false);
        dialog.getElement().getStyle()
                .set("--lumo-border-radius-l", "20px")
                .set("overflow-x", "hidden");
        dialog.setHeaderTitle(payee.getId() == null ? getTranslation("payees.add") : getTranslation("payees.edit"));

        TextField name = new TextField(getTranslation("payees.name"));
        name.setPrefixComponent(VaadinIcon.USER.create()); name.setWidthFull();

        TextField notes = new TextField(getTranslation("payees.notes"));
        notes.setWidthFull();

        ComboBox<Category> defaultCategory = new ComboBox<>(getTranslation("payees.default_category"));
        defaultCategory.setItems(categoryService.getAllCategories());
        defaultCategory.setItemLabelGenerator(Category::getFullName);
        defaultCategory.setWidthFull();

        ComboBox<Transaction.PaymentMethod> paymentMethodCombo = new ComboBox<>(getTranslation("payees.default_payment"));
        paymentMethodCombo.setItems(Transaction.PaymentMethod.values());
        paymentMethodCombo.setItemLabelGenerator(Transaction.PaymentMethod::getLabel);
        paymentMethodCombo.setWidthFull();

        Binder<Payee> binder = new Binder<>(Payee.class);
        binder.forField(name).asRequired(getTranslation("accounts.name_required")).bind(Payee::getName, Payee::setName);
        binder.bind(notes, Payee::getNotes, Payee::setNotes);
        binder.bind(defaultCategory, Payee::getDefaultCategory, Payee::setDefaultCategory);
        binder.bind(paymentMethodCombo, Payee::getDefaultPaymentMethod, Payee::setDefaultPaymentMethod);
        binder.setBean(payee);

        HorizontalLayout catRow = new HorizontalLayout(defaultCategory, paymentMethodCombo);
        catRow.setWidthFull(); catRow.setSpacing(false);
        catRow.getStyle().set("gap","var(--lumo-space-m)").set("flex-wrap","wrap");
        defaultCategory.getElement().getStyle().set("flex","2 1 180px").set("min-width","0");
        paymentMethodCombo.getElement().getStyle().set("flex","1 1 140px").set("min-width","0");

        Div body = new Div();
        body.setWidthFull();
        body.getStyle().set("display","flex").set("flex-direction","column").set("gap","var(--lumo-space-s)")
                .set("padding","var(--lumo-space-m) var(--lumo-space-l)").set("box-sizing","border-box");
        body.add(name, notes, catRow);
        dialog.add(body);

        Button saveButton = new Button(getTranslation("dialog.save"), VaadinIcon.CHECK.create(), e -> {
            if (binder.validate().isOk()) {
                payeeService.savePayee(payee); refreshGrid(); dialog.close();
                Notification.show(getTranslation("payees.saved"), 2000, Notification.Position.BOTTOM_END)
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
        grid.setItems(payeeService.searchPayees(searchField.getValue()));
    }
}
