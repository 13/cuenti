package com.cuenti.app.views;

import com.cuenti.app.model.Category;
import com.cuenti.app.model.Payee;
import com.cuenti.app.model.Tag;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import com.cuenti.app.security.SecurityUtils;
import com.cuenti.app.service.CategoryService;
import com.cuenti.app.service.PayeeService;
import com.cuenti.app.service.TagService;
import com.cuenti.app.service.UserService;
import com.vaadin.flow.component.button.Button;
import com.cuenti.app.views.components.DeleteConfirm;
import com.cuenti.app.views.components.UiNotifier;

import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Route(value = "payees", layout = MainLayout.class)
@PermitAll
public class PayeeManagementView extends VerticalLayout implements HasDynamicTitle {

    private final PayeeService payeeService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final UserService userService;
    private final SecurityUtils securityUtils;
    private final User currentUser;

    private final Grid<Payee> grid = new Grid<>(Payee.class, false);
    private final TextField searchField = new TextField();

    public PayeeManagementView(PayeeService payeeService, CategoryService categoryService,
                               TagService tagService, UserService userService, SecurityUtils securityUtils) {
        this.payeeService = payeeService;
        this.categoryService = categoryService;
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

    @Override
    public String getPageTitle() {
        return getTranslation("payees.title") + " | " + getTranslation("app.name");
    }

    private void setupUI() {
        Span title = new Span(getTranslation("payees.title"));
        title.addComponentAsFirst(VaadinIcon.USERS.create());
        title.addClassName("page-title");

        searchField.setPlaceholder(getTranslation("payees.search"));
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.EAGER);
        searchField.addValueChangeListener(e -> refreshGrid());

        Button addButton = new Button(getTranslation("payees.add"), VaadinIcon.PLUS.create(), e -> openPayeeDialog(new Payee()));
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
                new com.vaadin.flow.component.button.Button(getTranslation("empty.hint"), e -> openPayeeDialog(new Payee()));
        emptyAdd.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_TERTIARY);
        grid.setEmptyStateComponent(new com.cuenti.app.views.components.EmptyStateNotice(
                VaadinIcon.USERS, getTranslation("empty.title"), null, emptyAdd));
        grid.addItemDoubleClickListener(e -> openPayeeDialog(e.getItem()));

        // Demo-style per-column filter: search lives in the grid header
        searchField.setWidthFull();
        grid.addAttachListener(e -> {
            if (grid.getHeaderRows().size() < 2 && !grid.getColumns().isEmpty()) {
                grid.appendHeaderRow().getCell(grid.getColumns().get(0)).setComponent(searchField);
            }
        });
        grid.addColumn(Payee::getName).setHeader(getTranslation("payees.name")).setSortable(true).setAutoWidth(true);
        grid.addColumn(Payee::getNotes).setHeader(getTranslation("payees.notes")).setAutoWidth(true).setSortable(true);
        grid.addColumn(p -> p.getDefaultCategory() != null ? p.getDefaultCategory().getFullName() : "").setHeader(getTranslation("payees.default_category")).setAutoWidth(true).setSortable(true);
        grid.addColumn(p -> p.getDefaultPaymentMethod() != null ? p.getDefaultPaymentMethod().getLabel() : "").setHeader(getTranslation("payees.default_payment")).setAutoWidth(true).setSortable(true);
        grid.addColumn(p -> p.getDefaultMemo() != null ? p.getDefaultMemo() : "").setHeader(getTranslation("payees.default_memo")).setAutoWidth(true).setSortable(true);
        grid.addColumn(p -> p.getDefaultTags() != null ? p.getDefaultTags() : "").setHeader(getTranslation("payees.default_tags")).setAutoWidth(true).setSortable(true);

        grid.addComponentColumn(payee -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> openPayeeDialog(payee));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            editBtn.setTooltipText(getTranslation("transactions.edit"));

            editBtn.getElement().setAttribute("aria-label", getTranslation("transactions.edit"));Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e ->
                DeleteConfirm.show(
                    getTranslation("dialog.confirm_delete"),
                    getTranslation("dialog.confirm_delete_message") + " \"" + payee.getName() + "\"?",
                    getTranslation("dialog.delete"),
                    getTranslation("dialog.cancel"),
                    getTranslation("error.delete_failed"),
                    () -> {
                        payeeService.deletePayee(payee);
                        refreshGrid();
                        UiNotifier.successWithAction(getTranslation("payees.deleted"),
                                getTranslation("action.undo"), () -> {
                                    payee.setId(null);
                                    payeeService.savePayee(payee);
                                    refreshGrid();
                                });
                    }));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            deleteBtn.setTooltipText(getTranslation("transactions.delete"));

            deleteBtn.getElement().setAttribute("aria-label", getTranslation("transactions.delete"));return new HorizontalLayout(editBtn, deleteBtn);
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

    private void openPayeeDialog(Payee payee) {
        Dialog dialog = new Dialog();
        dialog.setCloseOnOutsideClick(false);
        dialog.setWidth("min(560px, 96vw)");
        dialog.setResizable(false);
        dialog.getElement().getStyle()
                .set("overflow-x", "hidden");
        dialog.setHeaderTitle(payee.getId() == null ? getTranslation("payees.add") : getTranslation("payees.edit"));
        com.vaadin.flow.component.icon.Icon headerIcon = VaadinIcon.USERS.create();
        headerIcon.addClassName("dialog-header-icon");
        dialog.getHeader().add(headerIcon);

        TextField name = new TextField(getTranslation("payees.name"));
        name.setPrefixComponent(VaadinIcon.USER.create()); name.setWidthFull();

        TextField notes = new TextField(getTranslation("payees.notes"));
        notes.setWidthFull();

        ComboBox<Category> defaultCategory = new ComboBox<>(getTranslation("payees.default_category"));
        defaultCategory.setItems(categoryService.getAllCategories());
        defaultCategory.setItemLabelGenerator(Category::getFullName);
        defaultCategory.setClearButtonVisible(true);
        defaultCategory.setAllowCustomValue(true);
        defaultCategory.setWidthFull();
        defaultCategory.addCustomValueSetListener(e -> {
            String newCatName = e.getDetail();
            if (newCatName == null || newCatName.isEmpty()) return;
            Category saved;
            if (newCatName.contains(":")) {
                String[] parts = newCatName.split(":", 2);
                String parentName = parts[0].trim();
                String childName = parts[1].trim();
                Category parentCat = categoryService.getAllCategories().stream()
                        .filter(c -> c.getName().equals(parentName) && c.getParent() == null)
                        .findFirst().orElse(null);
                if (parentCat == null) {
                    parentCat = categoryService.saveCategory(Category.builder()
                            .name(parentName).type(Category.CategoryType.EXPENSE).user(currentUser).parent(null).build());
                    Notification.show(getTranslation("categories.parent_created") + ": " + parentName, 3000, Notification.Position.MIDDLE);
                }
                saved = categoryService.saveCategory(Category.builder()
                        .name(childName).type(parentCat.getType()).parent(parentCat).user(currentUser).build());
            } else {
                saved = categoryService.saveCategory(Category.builder()
                        .name(newCatName).type(Category.CategoryType.EXPENSE).user(currentUser).build());
            }
            defaultCategory.setItems(categoryService.getAllCategories());
            defaultCategory.setValue(saved);
        });

        ComboBox<Transaction.PaymentMethod> paymentMethodCombo = new ComboBox<>(getTranslation("payees.default_payment"));
        paymentMethodCombo.setItems(Transaction.PaymentMethod.values());
        paymentMethodCombo.setItemLabelGenerator(Transaction.PaymentMethod::getLabel);
        paymentMethodCombo.setWidthFull();

        TextField defaultMemoField = new TextField(getTranslation("payees.default_memo"));
        defaultMemoField.setWidthFull();

        MultiSelectComboBox<Tag> defaultTagsCombo = new MultiSelectComboBox<>(getTranslation("payees.default_tags"));
        defaultTagsCombo.setItems(tagService.getAllTags());
        defaultTagsCombo.setItemLabelGenerator(Tag::getName);
        defaultTagsCombo.setWidthFull();
        if (payee.getDefaultTags() != null && !payee.getDefaultTags().isEmpty()) {
            Set<String> tagNames = new HashSet<>(Arrays.asList(payee.getDefaultTags().split(",")));
            defaultTagsCombo.setValue(tagService.getAllTags().stream()
                    .filter(t -> tagNames.contains(t.getName().trim()))
                    .collect(Collectors.toSet()));
        }

        TextField newTagField = new TextField();
        newTagField.setPlaceholder(getTranslation("payees.default_tags"));
        newTagField.setWidth("100%");

        Button addNewTagBtn = new Button(VaadinIcon.PLUS.create(), ev -> {
            String newTagName = newTagField.getValue().trim();
            if (!newTagName.isEmpty()) {
                boolean tagExists = tagService.getAllTags().stream()
                        .anyMatch(t -> t.getName().equalsIgnoreCase(newTagName));
                if (!tagExists) {
                    Tag newTag = Tag.builder().name(newTagName).build();
                    tagService.saveTag(newTag);
                }
                Set<Tag> sel = new HashSet<>(defaultTagsCombo.getValue());
                Tag newTag = tagService.getAllTags().stream()
                        .filter(t -> t.getName().equalsIgnoreCase(newTagName))
                        .findFirst().orElse(null);
                if (newTag != null) {
                    sel.add(newTag);
                    defaultTagsCombo.setItems(tagService.getAllTags());
                    defaultTagsCombo.setValue(sel);
                }
                newTagField.clear();
            }
        });
        addNewTagBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        HorizontalLayout tagsRow = new HorizontalLayout(defaultTagsCombo, newTagField, addNewTagBtn);
        tagsRow.setWidthFull();
        tagsRow.setSpacing(false);
        tagsRow.setAlignItems(FlexComponent.Alignment.END);
        tagsRow.getStyle().set("gap", "var(--vaadin-gap-s)");
        defaultTagsCombo.getStyle().set("flex", "1 1 0");
        newTagField.getStyle().set("flex", "1 1 0");
        addNewTagBtn.getStyle().set("flex-shrink", "0");

        Binder<Payee> binder = new Binder<>(Payee.class);
        binder.forField(name).asRequired(getTranslation("accounts.name_required")).bind(Payee::getName, Payee::setName);
        binder.bind(notes, Payee::getNotes, Payee::setNotes);
        binder.bind(defaultCategory, Payee::getDefaultCategory, Payee::setDefaultCategory);
        binder.bind(paymentMethodCombo, Payee::getDefaultPaymentMethod, Payee::setDefaultPaymentMethod);
        binder.bind(defaultMemoField, Payee::getDefaultMemo, Payee::setDefaultMemo);
        binder.setBean(payee);

        HorizontalLayout catRow = new HorizontalLayout(defaultCategory, paymentMethodCombo);
        catRow.setWidthFull(); catRow.setSpacing(false);
        catRow.getStyle().set("gap","var(--vaadin-gap-m)").set("flex-wrap","wrap");
        defaultCategory.getElement().getStyle().set("flex","2 1 180px").set("min-width","0");
        paymentMethodCombo.getElement().getStyle().set("flex","1 1 140px").set("min-width","0");

        Div body = new Div();
        body.setWidthFull();
        body.addClassName("dialog-body");
        body.add(name, notes, catRow, defaultMemoField, tagsRow);
        dialog.add(body);

        Button saveButton = new Button(getTranslation("dialog.save"), e -> {
            if (binder.validate().isOk()) {
                String tags = defaultTagsCombo.getValue().stream().map(Tag::getName).collect(Collectors.joining(","));
                payee.setDefaultTags(tags.isEmpty() ? null : tags);
                payeeService.savePayee(payee); refreshGrid(); dialog.close();
                com.cuenti.app.views.components.UiNotifier.success(getTranslation("payees.saved"));
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
        name.focus();
    }

    private void refreshGrid() {
        grid.setItems(payeeService.searchPayees(searchField.getValue()));
    }
}
