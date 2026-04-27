package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.*;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.*;
import com.cuenti.homebanking.views.components.TagColorUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Route(value = "scheduled", layout = MainLayout.class)
@PageTitle("Scheduled Transactions | Cuenti")
@PermitAll
public class ScheduledTransactionsView extends VerticalLayout {

    private final ScheduledTransactionService scheduledService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final PayeeService payeeService;
    private final TagService tagService;
    private final UserService userService;
    private final SecurityUtils securityUtils;
    private final User currentUser;

    private final Grid<ScheduledTransaction> templateGrid = new Grid<>(ScheduledTransaction.class, false);
    private final Grid<ScheduledTransaction> pendingGrid = new Grid<>(ScheduledTransaction.class, false);
    private final Select<Integer> horizonSelect = new Select<>();

    public ScheduledTransactionsView(ScheduledTransactionService scheduledService, AccountService accountService,
                                     CategoryService categoryService, PayeeService payeeService, TagService tagService,
                                     UserService userService, SecurityUtils securityUtils) {
        this.scheduledService = scheduledService;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.payeeService = payeeService;
        this.tagService = tagService;
        this.userService = userService;
        this.securityUtils = securityUtils;

        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        this.currentUser = userService.findByUsername(username);

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("padding", "var(--lumo-space-m)")
                .set("overflow", "hidden");

        setupUI();
        refreshGrids();
    }

    private void setupUI() {
        setSizeFull();
        setPadding(true);
        setSpacing(false);
        getStyle().set("gap", "var(--lumo-space-m)");

        // Page header
        Span title = new Span(getTranslation("scheduled.title"));
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-xxl)")
                .set("font-weight", "700")
                .set("color", "var(--lumo-header-text-color)");
        add(title);

        // Outer card
        Div card = new Div();
        card.setSizeFull();
        card.getStyle()
                .set("background-color", "var(--lumo-base-color)")
                .set("border-radius", "20px")
                .set("padding", "var(--lumo-space-l)")
                .set("box-shadow", "0 2px 12px rgba(0,0,0,0.06)")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("box-sizing", "border-box")
                .set("gap", "var(--lumo-space-l)");

        // Toolbar
        horizonSelect.setLabel(getTranslation("scheduled.horizon.label"));
        horizonSelect.setItems(7, 30, 90, -1);
        horizonSelect.setItemLabelGenerator(this::getHorizonLabel);
        horizonSelect.setValue(7);
        horizonSelect.addValueChangeListener(e -> refreshGrids());
        horizonSelect.setWidth("200px");

        Button addButton = new Button(getTranslation("scheduled.new"), VaadinIcon.PLUS.create(),
                e -> openEditDialog(new ScheduledTransaction()));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(horizonSelect, addButton);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.BASELINE);
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        toolbar.getStyle()
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "12px");

        setupPendingGrid();
        setupTemplateGrid();

        Div content = new Div();
        content.getStyle()
                .set("overflow-y", "auto")
                .set("flex-grow", "1")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-l)");

        content.add(buildSectionCard("scheduled.pending_title", VaadinIcon.CLOCK, pendingGrid, true));
        content.add(buildSectionCard("scheduled.list_title", VaadinIcon.CALENDAR, templateGrid, false));

        card.add(toolbar, content);
        add(card);
        expand(card);
    }

    private Div buildSectionCard(String titleKey, VaadinIcon icon, Grid<?> grid, boolean accent) {
        Div section = new Div();
        section.setWidthFull();
        section.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "16px")
                .set("padding", "var(--lumo-space-m) var(--lumo-space-l)")
                .set("box-sizing", "border-box");
        if (accent) {
            section.getStyle().set("border-left", "4px solid var(--lumo-primary-color)");
        }

        Icon ico = icon.create();
        ico.getStyle()
                .set("color", accent ? "var(--lumo-primary-color)" : "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-m)")
                .set("flex-shrink", "0");

        Span sectionTitle = new Span(getTranslation(titleKey));
        sectionTitle.getStyle()
                .set("font-size", "var(--lumo-font-size-m)")
                .set("font-weight", "700")
                .set("color", "var(--lumo-header-text-color)");

        HorizontalLayout header = new HorizontalLayout(ico, sectionTitle);
        header.setAlignItems(Alignment.CENTER);
        header.setSpacing(false);
        header.getStyle()
                .set("gap", "var(--lumo-space-s)")
                .set("margin-bottom", "var(--lumo-space-s)");

        section.add(header, grid);
        return section;
    }

    private void setupTemplateGrid() {
        templateGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        templateGrid.setAllRowsVisible(true);

        // Account
        templateGrid.addComponentColumn(st -> {
            Span s = new Span(st.getFromAccount() != null ? st.getFromAccount().getAccountName() : "—");
            s.getStyle().set("font-size", "var(--lumo-font-size-s)");
            return s;
        }).setHeader(getTranslation("dialog.account")).setAutoWidth(true).setSortable(true)
                .setComparator(Comparator.comparing(st -> st.getFromAccount() != null ? st.getFromAccount().getAccountName() : ""));

        // Payee
        templateGrid.addComponentColumn(st -> {
            Span s = new Span(st.getPayee() != null ? st.getPayee() : "—");
            s.getStyle().set("font-weight", "600").set("font-size", "var(--lumo-font-size-s)");
            return s;
        }).setHeader(getTranslation("transactions.payee")).setAutoWidth(true).setSortable(true)
                .setComparator(Comparator.comparing(st -> st.getPayee() != null ? st.getPayee() : ""));

        // Amount
        templateGrid.addComponentColumn(st -> createAmountSpan(st.getAmount(), st.getType()))
                .setHeader(getTranslation("dialog.amount")).setAutoWidth(true).setSortable(true)
                .setComparator(Comparator.comparing(ScheduledTransaction::getAmount));

        // Recurrence pill
        templateGrid.addComponentColumn(st -> createRecurrenceBadge(st.getRecurrencePattern(), st.getRecurrenceValue()))
                .setHeader(getTranslation("scheduled.recurrence")).setAutoWidth(true);

        // Next date
        templateGrid.addComponentColumn(st -> {
            Span d = new Span(st.getNextOccurrence().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            d.getStyle().set("font-size", "var(--lumo-font-size-s)");
            return d;
        }).setHeader(getTranslation("scheduled.next_date")).setAutoWidth(true).setSortable(true)
                .setComparator(Comparator.comparing(ScheduledTransaction::getNextOccurrence));

        // Tags
        templateGrid.addComponentColumn(this::buildTagBadges)
                .setHeader(getTranslation("dialog.tags")).setAutoWidth(true);

        // Enabled toggle styled as a pill
        templateGrid.addComponentColumn(st -> {
            Checkbox enabled = new Checkbox(st.isEnabled());
            enabled.addValueChangeListener(e -> {
                st.setEnabled(e.getValue());
                scheduledService.save(st);
                refreshGrids();
            });
            return enabled;
        }).setHeader(getTranslation("scheduled.enabled")).setAutoWidth(true);

        // Actions
        templateGrid.addComponentColumn(st -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> openEditDialog(st));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            editBtn.getElement().setAttribute("title", getTranslation("dialog.edit_transaction"));

            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e -> {
                scheduledService.delete(st);
                refreshGrids();
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            deleteBtn.getElement().setAttribute("title", getTranslation("transactions.actions"));

            HorizontalLayout hl = new HorizontalLayout(editBtn, deleteBtn);
            hl.setSpacing(false);
            hl.getStyle().set("gap", "var(--lumo-space-xs)");
            return hl;
        }).setHeader(getTranslation("transactions.actions")).setFrozenToEnd(true).setAutoWidth(true);
    }

    private void setupPendingGrid() {
        pendingGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        pendingGrid.setAllRowsVisible(true);

        // Due date with urgency badge
        pendingGrid.addComponentColumn(st -> {
            LocalDateTime now = LocalDateTime.now();
            boolean overdue = st.getNextOccurrence().isBefore(now);
            boolean dueToday = !overdue && st.getNextOccurrence().toLocalDate().isEqual(now.toLocalDate());

            Span date = new Span(st.getNextOccurrence().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            date.getStyle().set("font-size", "var(--lumo-font-size-s)").set("font-weight", "600");

            String badgeText = null;
            String badgeColor = null;
            if (overdue) {
                badgeText = getTranslation("scheduled.late");
                badgeColor = "var(--lumo-error-color)";
                date.getStyle().set("color", "var(--lumo-error-color)");
            } else if (dueToday) {
                badgeText = getTranslation("scheduled.today");
                badgeColor = "var(--lumo-warning-color, #f5a623)";
                date.getStyle().set("color", "var(--lumo-warning-color, #f5a623)");
            }

            Div cell = new Div();
            cell.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "3px")
                    .set("padding", "var(--lumo-space-xs) 0");
            cell.add(date);

            if (badgeText != null) {
                Span badge = new Span(badgeText);
                badge.getStyle()
                        .set("font-size", "9px").set("font-weight", "700").set("letter-spacing", "0.06em")
                        .set("text-transform", "uppercase").set("padding", "1px 6px")
                        .set("border-radius", "99px").set("background", badgeColor)
                        .set("color", "white").set("width", "fit-content");
                cell.add(badge);
            }
            return cell;
        }).setHeader(getTranslation("scheduled.due_date")).setAutoWidth(true).setSortable(true)
                .setComparator(Comparator.comparing(ScheduledTransaction::getNextOccurrence));

        // Account
        pendingGrid.addComponentColumn(st -> {
            Span s = new Span(st.getFromAccount() != null ? st.getFromAccount().getAccountName() : "—");
            s.getStyle().set("font-size", "var(--lumo-font-size-s)");
            return s;
        }).setHeader(getTranslation("dialog.account")).setAutoWidth(true).setSortable(true)
                .setComparator(Comparator.comparing(st -> st.getFromAccount() != null ? st.getFromAccount().getAccountName() : ""));

        // Payee
        pendingGrid.addComponentColumn(st -> {
            Span s = new Span(st.getPayee() != null ? st.getPayee() : "—");
            s.getStyle().set("font-weight", "600").set("font-size", "var(--lumo-font-size-s)");
            return s;
        }).setHeader(getTranslation("transactions.payee")).setAutoWidth(true).setSortable(true)
                .setComparator(Comparator.comparing(st -> st.getPayee() != null ? st.getPayee() : ""));

        // Amount
        pendingGrid.addComponentColumn(st -> createAmountSpan(st.getAmount(), st.getType()))
                .setHeader(getTranslation("dialog.amount")).setAutoWidth(true).setSortable(true)
                .setComparator(Comparator.comparing(ScheduledTransaction::getAmount));

        // Tags
        pendingGrid.addComponentColumn(this::buildTagBadges)
                .setHeader(getTranslation("dialog.tags")).setAutoWidth(true);

        // Actions: Post (primary), Skip (subtle), Edit (icon)
        pendingGrid.addComponentColumn(st -> {
            Button postBtn = new Button(getTranslation("scheduled.post"), VaadinIcon.CHECK.create(), e -> {
                scheduledService.post(st.getId());
                refreshGrids();
                Notification.show(getTranslation("scheduled.posted"), 2000, Notification.Position.BOTTOM_END);
            });
            postBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);

            Button skipBtn = new Button(getTranslation("scheduled.skip"), VaadinIcon.STEP_FORWARD.create(), e -> {
                scheduledService.skip(st.getId());
                refreshGrids();
                Notification.show(getTranslation("scheduled.skipped"), 2000, Notification.Position.BOTTOM_END);
            });
            skipBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> openEditDialog(st));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            editBtn.getElement().setAttribute("title", getTranslation("dialog.edit_transaction"));

            HorizontalLayout actions = new HorizontalLayout(postBtn, skipBtn, editBtn);
            actions.setSpacing(false);
            actions.setAlignItems(Alignment.CENTER);
            actions.getStyle().set("gap", "var(--lumo-space-xs)");
            return actions;
        }).setHeader(getTranslation("transactions.actions")).setFrozenToEnd(true).setAutoWidth(true);
    }

    private void openEditDialog(ScheduledTransaction st) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(st.getId() == null ? getTranslation("dialog.add_transaction") : getTranslation("dialog.edit_transaction"));
        dialog.setWidth("640px");
        dialog.getElement().getStyle().set("--lumo-border-radius-l", "16px");

        FormLayout form = new FormLayout();

        Tabs typeTabs = new Tabs();
        Tab expenseTab = new Tab(getTranslation("transaction.type.expense"));
        Tab incomeTab = new Tab(getTranslation("transaction.type.income"));
        Tab transferTab = new Tab(getTranslation("transaction.type.transfer"));
        typeTabs.add(expenseTab, incomeTab, transferTab);
        typeTabs.setWidthFull();
        
        DatePicker nextDate = new DatePicker(getTranslation("scheduled.next_date"));
        BigDecimalField amount = new BigDecimalField(getTranslation("dialog.amount"));

        ComboBox<String> payee = new ComboBox<>(getTranslation("transactions.payee"));
        List<String> existingPayees = payeeService.getAllPayees().stream().map(Payee::getName).distinct().toList();
        payee.setItems(existingPayees);
        payee.setAllowCustomValue(true);
        payee.addCustomValueSetListener(e -> payee.setValue(e.getDetail()));

        ComboBox<Account> fromAccount = new ComboBox<>(getTranslation("dialog.from"));
        fromAccount.setItems(accountService.getAccountsByUser(currentUser));
        fromAccount.setItemLabelGenerator(Account::getAccountName);
        
        ComboBox<Account> toAccount = new ComboBox<>(getTranslation("dialog.to"));
        toAccount.setItems(accountService.getAccountsByUser(currentUser));
        toAccount.setItemLabelGenerator(Account::getAccountName);
        toAccount.setVisible(false);

        ComboBox<ScheduledTransaction.RecurrencePattern> pattern = new ComboBox<>(getTranslation("scheduled.recurrence"));
        pattern.setItems(ScheduledTransaction.RecurrencePattern.values());

        ComboBox<Transaction.PaymentMethod> paymentMethod = new ComboBox<>(getTranslation("dialog.payment_method"));
        paymentMethod.setItems(Transaction.PaymentMethod.values());
        paymentMethod.setItemLabelGenerator(pm -> pm == Transaction.PaymentMethod.NONE ? getTranslation("dialog.none") : pm.getLabel());

        IntegerField recValue = new IntegerField(getTranslation("scheduled.every_x"));
        recValue.setMin(1);
        recValue.setStepButtonsVisible(true);

        ComboBox<Category> category = new ComboBox<>(getTranslation("transactions.category"));
        category.setItemLabelGenerator(Category::getFullName);
        category.setAllowCustomValue(true);

        if (st.getType() == Transaction.TransactionType.INCOME) {
            typeTabs.setSelectedTab(incomeTab);
        } else if (st.getType() == Transaction.TransactionType.TRANSFER) {
            typeTabs.setSelectedTab(transferTab);
        } else {
            typeTabs.setSelectedTab(expenseTab);
        }

        java.util.function.Supplier<Transaction.TransactionType> selectedType = () -> {
            Tab selected = typeTabs.getSelectedTab();
            if (selected == incomeTab) {
                return Transaction.TransactionType.INCOME;
            }
            if (selected == transferTab) {
                return Transaction.TransactionType.TRANSFER;
            }
            return Transaction.TransactionType.EXPENSE;
        };

        // Helper to update category items based on transaction type
        Runnable updateCategoryItems = () -> {
            Transaction.TransactionType transactionType = selectedType.get();
            if (transactionType == Transaction.TransactionType.INCOME) {
                category.setItems(categoryService.getCategoriesByType(Category.CategoryType.INCOME));
            } else if (transactionType == Transaction.TransactionType.EXPENSE) {
                category.setItems(categoryService.getCategoriesByType(Category.CategoryType.EXPENSE));
            } else {
                // Transfers can still be categorized for reporting consistency.
                category.setItems(categoryService.getAllCategories());
            }
        };

        category.addCustomValueSetListener(e -> {
            String newCatName = e.getDetail();
            // Determine category type based on transaction type
            Category.CategoryType categoryType = selectedType.get() == Transaction.TransactionType.INCOME
                    ? Category.CategoryType.INCOME
                    : Category.CategoryType.EXPENSE;

            Category saved;
            // Check if the name contains ":" for Parent:Child format
            if (newCatName != null && newCatName.contains(":")) {
                String[] parts = newCatName.split(":", 2);
                if (parts.length == 2) {
                    String parentName = parts[0].trim();
                    String childName = parts[1].trim();

                    // Find or create parent category
                    Category parentCategory = categoryService.getAllCategories().stream()
                            .filter(c -> c.getName().equals(parentName) && c.getParent() == null && c.getType() == categoryType)
                            .findFirst()
                            .orElse(null);

                    if (parentCategory == null) {
                        // Create new parent category
                        parentCategory = Category.builder()
                                .name(parentName)
                                .type(categoryType)
                                .user(currentUser)
                                .parent(null)
                                .build();
                        parentCategory = categoryService.saveCategory(parentCategory);
                        Notification.show(getTranslation("categories.parent_created") + ": " + parentName, 3000, Notification.Position.MIDDLE);
                    }

                    // Create child category with parent
                    Category newCat = Category.builder()
                            .name(childName)
                            .type(categoryType)
                            .parent(parentCategory)
                            .user(currentUser)
                            .build();
                    saved = categoryService.saveCategory(newCat);
                } else {
                    // Fallback to simple category creation
                    Category newCat = Category.builder()
                            .name(newCatName)
                            .type(categoryType)
                            .user(currentUser)
                            .build();
                    saved = categoryService.saveCategory(newCat);
                }
            } else {
                // Simple category creation
                Category newCat = Category.builder()
                        .name(newCatName)
                        .type(categoryType)
                        .user(currentUser)
                        .build();
                saved = categoryService.saveCategory(newCat);
            }

            updateCategoryItems.run();
            category.setValue(saved);
        });

        TextArea memo = new TextArea(getTranslation("dialog.memo"));

        MultiSelectComboBox<Tag> tags = new MultiSelectComboBox<>(getTranslation("dialog.tags"));
        tags.setItems(tagService.getAllTags());
        tags.setItemLabelGenerator(Tag::getName);
        tags.setAllowCustomValue(true);
        tags.addCustomValueSetListener(e -> {
            Tag newTag = Tag.builder().name(e.getDetail()).build();
            tagService.saveTag(newTag);
            tags.setItems(tagService.getAllTags());
            Set<Tag> current = new HashSet<>(tags.getValue());
            current.add(newTag);
            tags.setValue(current);
        });

        typeTabs.addSelectedChangeListener(e -> {
            toAccount.setVisible(selectedType.get() == Transaction.TransactionType.TRANSFER);
            updateCategoryItems.run();
        });

        Binder<ScheduledTransaction> binder = new Binder<>(ScheduledTransaction.class);
        binder.forField(nextDate).asRequired().bind(t -> t.getNextOccurrence().toLocalDate(), (t, v) -> t.setNextOccurrence(v.atStartOfDay()));
        binder.forField(amount).asRequired().bind(ScheduledTransaction::getAmount, ScheduledTransaction::setAmount);
        binder.bind(payee, ScheduledTransaction::getPayee, ScheduledTransaction::setPayee);
        binder.forField(fromAccount).asRequired().bind(ScheduledTransaction::getFromAccount, ScheduledTransaction::setFromAccount);
        binder.bind(toAccount, ScheduledTransaction::getToAccount, ScheduledTransaction::setToAccount);
        binder.forField(pattern).asRequired().bind(ScheduledTransaction::getRecurrencePattern, ScheduledTransaction::setRecurrencePattern);
        binder.bind(recValue, ScheduledTransaction::getRecurrenceValue, ScheduledTransaction::setRecurrenceValue);
        binder.bind(category, ScheduledTransaction::getCategory, ScheduledTransaction::setCategory);
        binder.bind(paymentMethod,
                stx -> stx.getPaymentMethod() != null ? stx.getPaymentMethod() : Transaction.PaymentMethod.NONE,
                ScheduledTransaction::setPaymentMethod);
        binder.bind(memo, ScheduledTransaction::getMemo, ScheduledTransaction::setMemo);
        binder.bind(tags,
                stx -> {
                    if (stx.getTags() == null || stx.getTags().isBlank()) {
                        return Set.of();
                    }
                    Set<String> names = Arrays.stream(stx.getTags().split(","))
                            .map(String::trim)
                            .filter(value -> !value.isBlank())
                            .collect(Collectors.toSet());
                    return tagService.getAllTags().stream()
                            .filter(tag -> names.contains(tag.getName()))
                            .collect(Collectors.toSet());
                },
                (stx, selectedTags) -> stx.setTags(selectedTags.stream().map(Tag::getName).collect(Collectors.joining(","))));

        // Populate category items before binder.setBean(...) so ComboBox can accept existing value.
        updateCategoryItems.run();

        if (st.getId() != null) {
            binder.setBean(st);
            toAccount.setVisible(st.getType() == Transaction.TransactionType.TRANSFER);
        } else {
            st.setType(Transaction.TransactionType.EXPENSE);
            st.setNextOccurrence(LocalDateTime.now());
            st.setPaymentMethod(Transaction.PaymentMethod.NONE);
            binder.setBean(st);
        }


        form.add(typeTabs, nextDate, amount, fromAccount, toAccount, payee, category, paymentMethod, pattern, recValue, memo, tags);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));
        form.setColspan(typeTabs, 2);
        form.setColspan(memo, 2);
        form.setColspan(tags, 2);

        Button save = new Button(getTranslation("dialog.save"), e -> {
            if (binder.validate().isOk()) {
                st.setType(selectedType.get());
                st.setUser(currentUser);
                scheduledService.save(st);
                refreshGrids();
                dialog.close();
                Notification.show(getTranslation("dialog.saved"), 2000, Notification.Position.BOTTOM_END);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancel = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        dialog.add(form);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    // ─────────────────────────────────────────────────────────────────
    // Visual helper factories
    // ─────────────────────────────────────────────────────────────────

    /** Coloured pill badge for transaction type. */
    private Span createTypeBadge(Transaction.TransactionType type) {
        String label;
        String bg;
        String fg;
        switch (type) {
            case INCOME   -> { label = getTranslation("transaction.type.income");   bg = "var(--lumo-success-color)"; fg = "white"; }
            case TRANSFER -> { label = getTranslation("transaction.type.transfer"); bg = "var(--lumo-primary-color)"; fg = "white"; }
            default       -> { label = getTranslation("transaction.type.expense");  bg = "var(--lumo-error-color)";   fg = "white"; }
        }
        Span badge = new Span(label);
        badge.getStyle()
                .set("font-size", "9px").set("font-weight", "700").set("letter-spacing", "0.06em")
                .set("text-transform", "uppercase").set("padding", "2px 8px")
                .set("border-radius", "99px").set("background", bg).set("color", fg)
                .set("white-space", "nowrap");
        return badge;
    }

    /** Amount span coloured by transaction type with bold weight. */
    private Span createAmountSpan(BigDecimal amount, Transaction.TransactionType type) {
        Span span = new Span(formatCurrency(amount));
        span.getStyle().set("font-weight", "700").set("font-size", "var(--lumo-font-size-s)");
        if (type == Transaction.TransactionType.EXPENSE)
            span.getStyle().set("color", "var(--lumo-error-color)");
        else if (type == Transaction.TransactionType.INCOME)
            span.getStyle().set("color", "var(--lumo-success-color)");
        else
            span.getStyle().set("color", "var(--lumo-primary-color)");
        return span;
    }

    /** Friendly recurrence pill: e.g. "Monthly", "Every 2 weeks". */
    private Span createRecurrenceBadge(ScheduledTransaction.RecurrencePattern pattern, Integer value) {
        String text = pattern.name().charAt(0) + pattern.name().substring(1).toLowerCase();
        if (value != null && value > 1) text = getTranslation("scheduled.every_n").replace("{0}", String.valueOf(value)) + " " + text.toLowerCase();
        Span badge = new Span(text);
        badge.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)").set("font-weight", "500")
                .set("padding", "2px 8px").set("border-radius", "99px")
                .set("background", "var(--lumo-contrast-10pct)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("white-space", "nowrap");
        return badge;
    }

    private void refreshGrids() {
        List<ScheduledTransaction> all = scheduledService.getByUser(currentUser);
        templateGrid.setItems(all);
        
        Integer selectedHorizon = horizonSelect.getValue();
        int days = selectedHorizon == null || selectedHorizon < 0 ? 36500 : selectedHorizon;

        List<ScheduledTransaction> pending = all.stream()
                .filter(ScheduledTransaction::isEnabled)
                .filter(st -> st.getNextOccurrence().isBefore(LocalDateTime.now().plusDays(days)))
                .sorted(Comparator.comparing(ScheduledTransaction::getNextOccurrence))
                .toList();
        pendingGrid.setItems(pending);
    }

    private Div createCard() {
        Div card = new Div();
        card.getStyle()
                .set("background-color", "var(--lumo-base-color)")
                .set("border-radius", "16px")
                .set("padding", "var(--lumo-space-l)")
                .set("box-shadow", "var(--lumo-box-shadow-m)")
                .set("margin-bottom", "var(--lumo-space-m)");
        return card;
    }

    private String getHorizonLabel(Integer value) {
        if (value == null) {
            return "";
        }
        return switch (value) {
            case 7 -> getTranslation("scheduled.horizon.7");
            case 30 -> getTranslation("scheduled.horizon.30");
            case 90 -> getTranslation("scheduled.horizon.90");
            default -> getTranslation("scheduled.horizon.unlimited");
        };
    }

    private HorizontalLayout buildTagBadges(ScheduledTransaction st) {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setSpacing(false);
        layout.getStyle().set("flex-wrap", "wrap").set("gap", "4px");
        if (st.getTags() == null || st.getTags().isBlank()) {
            return layout;
        }

        Arrays.stream(st.getTags().split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .forEach(tag -> layout.add(TagColorUtil.createTagBadge(tag)));

        return layout;
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "";
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.GERMANY);
        try {
            java.util.Currency currency = java.util.Currency.getInstance(currentUser.getDefaultCurrency());
            formatter.setCurrency(currency);
        } catch (Exception e) {}
        return formatter.format(amount);
    }
}
