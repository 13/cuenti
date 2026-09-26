package com.cuenti.app.views;

import com.cuenti.app.model.*;
import com.cuenti.app.security.SecurityUtils;
import com.cuenti.app.service.*;
import com.cuenti.app.views.components.TagColorUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Route(value = "scheduled", layout = MainLayout.class)
@PermitAll
public class ScheduledTransactionsView extends VerticalLayout implements HasDynamicTitle {

    @Override
    public String getPageTitle() {
        return getTranslation("scheduled.title") + " | " + getTranslation("app.name");
    }


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
    private final Button postAllButton = new Button();

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

        addClassNames("page-scroll", "page-shell");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        setupUI();
        refreshGrids();
    }

    private void setupUI() {
        setSizeFull();
        setPadding(true);
        setSpacing(false);
        getStyle().set("gap", "var(--vaadin-gap-m)");

        // Page header
        Span title = new Span(getTranslation("scheduled.title"));
        title.addComponentAsFirst(VaadinIcon.CALENDAR_CLOCK.create());
        title.addClassName("page-title");
        add(title);

        // No outer card: the two sections below are cards themselves
        Div card = new Div();
        card.setSizeFull();
        card.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--vaadin-gap-m)");

        // Toolbar
        horizonSelect.setLabel(getTranslation("scheduled.horizon.label"));
        horizonSelect.setItems(7, 30, 90, -1);
        horizonSelect.setItemLabelGenerator(this::getHorizonLabel);
        Integer savedHorizon = currentUser.getScheduledHorizonDays();
        horizonSelect.setValue(savedHorizon != null && List.of(7, 30, 90, -1).contains(savedHorizon) ? savedHorizon : 7);
        horizonSelect.addValueChangeListener(e -> {
            if (e.isFromClient() && e.getValue() != null) {
                userService.updateScheduledPreferences(currentUser, currentUser.getScheduledBadgeDays(), e.getValue());
            }
            refreshGrids();
        });
        horizonSelect.setWidth("200px");

        Button addButton = new Button(getTranslation("scheduled.new"), VaadinIcon.PLUS.create(),
                e -> openEditDialog(new ScheduledTransaction()));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        postAllButton.setText(getTranslation("scheduled.post_all"));
        postAllButton.setIcon(VaadinIcon.CHECK_CIRCLE.create());
        postAllButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        postAllButton.addClickListener(e -> {
            int posted = scheduledService.postAllDue();
            refreshGrids();
            com.cuenti.app.views.components.UiNotifier.success(
                    getTranslation("scheduled.posted_all", posted));
        });

        HorizontalLayout toolbarActions = new HorizontalLayout(postAllButton, addButton);
        toolbarActions.setAlignItems(Alignment.BASELINE);

        HorizontalLayout toolbar = new HorizontalLayout(horizonSelect, toolbarActions);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.BASELINE);
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        toolbar.getStyle().set("flex-wrap", "wrap");

        setupPendingGrid();
        setupTemplateGrid();

        Div content = new Div();
        content.getStyle()
                .set("overflow-y", "auto")
                .set("flex-grow", "1")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--vaadin-gap-l)");

        content.add(buildSectionCard("scheduled.pending_title", VaadinIcon.CLOCK, pendingGrid, true));
        content.add(buildSectionCard("scheduled.list_title", VaadinIcon.CALENDAR, templateGrid, false));

        card.add(toolbar, content);
        add(card);
        expand(card);
    }

    private Div buildSectionCard(String titleKey, VaadinIcon icon, Grid<?> grid, boolean accent) {
        Div section = new Div();
        section.setWidthFull();
        section.addClassName("card");
        if (accent) {
            section.getStyle().set("border-left", "4px solid var(--aura-accent-color)");
        }

        Icon ico = icon.create();
        ico.getStyle()
                .set("color", accent ? "var(--aura-accent-color)" : "var(--vaadin-text-color-secondary)")
                .set("font-size", "var(--aura-font-size-m)")
                .set("flex-shrink", "0");

        Span sectionTitle = new Span(getTranslation(titleKey));
        sectionTitle.getStyle()
                .set("font-size", "var(--aura-font-size-m)")
                .set("font-weight", "700")
                .set("color", "var(--vaadin-text-color)");

        HorizontalLayout header = new HorizontalLayout(ico, sectionTitle);
        header.setAlignItems(Alignment.CENTER);
        header.setSpacing(false);
        header.getStyle()
                .set("gap", "var(--vaadin-gap-s)")
                .set("margin-bottom", "var(--vaadin-gap-s)");

        section.add(header, grid);
        return section;
    }

    private void setupTemplateGrid() {
        templateGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        templateGrid.addItemDoubleClickListener(e -> openEditDialog(e.getItem()));
        templateGrid.setAllRowsVisible(true);

        // Payee (flexes; the key columns stay visible on narrow screens)
        templateGrid.addComponentColumn(st -> {
            Span s = new Span(st.getPayee() != null ? st.getPayee() : "—");
            s.getStyle().set("font-weight", "600").set("font-size", "var(--aura-font-size-s)");
            return s;
        }).setHeader(getTranslation("transactions.payee")).setWidth("9rem").setFlexGrow(1).setSortable(true)
                .setComparator(Comparator.comparing(st -> st.getPayee() != null ? st.getPayee() : ""));

        // Amount
        templateGrid.addComponentColumn(st -> createAmountSpan(st.getAmount(), st.getType()))
                .setHeader(getTranslation("dialog.amount"))
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END).setAutoWidth(true).setFlexGrow(0).setSortable(true)
                .setComparator(Comparator.comparing(ScheduledTransaction::getAmount));

        // Next date
        templateGrid.addComponentColumn(st -> {
            Span d = new Span(st.getNextOccurrence().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            d.getStyle().set("font-size", "var(--aura-font-size-s)");
            return d;
        }).setHeader(getTranslation("scheduled.next_date")).setAutoWidth(true).setFlexGrow(0).setSortable(true)
                .setComparator(Comparator.comparing(ScheduledTransaction::getNextOccurrence));

        // Recurrence pill
        templateGrid.addComponentColumn(st -> createRecurrenceBadge(st.getRecurrencePattern(), st.getRecurrenceValue()))
                .setHeader(getTranslation("scheduled.recurrence")).setAutoWidth(true).setFlexGrow(0);

        // Account
        com.vaadin.flow.component.grid.Grid.Column<ScheduledTransaction> templateAccountCol =
        templateGrid.addComponentColumn(st -> {
            Span s = new Span(accountLabel(st));
            s.getStyle().set("font-size", "var(--aura-font-size-s)");
            return s;
        }).setHeader(getTranslation("dialog.account")).setWidth("7rem").setFlexGrow(1).setSortable(true)
                .setComparator(Comparator.comparing(this::accountLabel));
        com.cuenti.app.views.components.ResponsiveGridColumns.hideBelow(520, templateGrid,
                java.util.List.of(templateAccountCol));

        // Tags
        com.vaadin.flow.component.grid.Grid.Column<ScheduledTransaction> templateTagsCol =
        templateGrid.addComponentColumn(this::buildTagBadges)
                .setHeader(getTranslation("dialog.tags")).setAutoWidth(true);
        com.cuenti.app.views.components.ResponsiveGridColumns.hideBelow(768, templateGrid,
                java.util.List.of(templateTagsCol));

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
                com.cuenti.app.views.components.UiNotifier.error(getTranslation("scheduled.deleted"));
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            deleteBtn.getElement().setAttribute("title", getTranslation("transactions.actions"));

            Button historyBtn = new Button(VaadinIcon.TIME_BACKWARD.create(), e -> openHistoryDialog(st));
            historyBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            historyBtn.getElement().setAttribute("title", getTranslation("scheduled.history"));
            historyBtn.getElement().setAttribute("aria-label", getTranslation("scheduled.history"));

            HorizontalLayout hl = new HorizontalLayout(editBtn, historyBtn, deleteBtn);
            hl.setSpacing(false);
            hl.getStyle().set("gap", "var(--vaadin-gap-xs)");
            return hl;
        }).setHeader(getTranslation("transactions.actions")).setFrozenToEnd(true).setAutoWidth(true);
    }

    private void setupPendingGrid() {
        pendingGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        pendingGrid.setAllRowsVisible(true);

        // Due date with urgency badge
        pendingGrid.addComponentColumn(st -> {
            boolean overdue = ScheduledTransactionService.isOverdue(st);
            boolean dueToday = !overdue && st.getNextOccurrence().toLocalDate().isEqual(java.time.LocalDate.now());

            Span date = new Span(st.getNextOccurrence().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            date.getStyle().set("font-size", "var(--aura-font-size-s)").set("font-weight", "600");

            String badgeText = null;
            String badgeColor = null;
            if (overdue) {
                badgeText = getTranslation("scheduled.late");
                badgeColor = "var(--aura-red)";
                date.getStyle().set("color", "var(--aura-red)");
            } else if (dueToday) {
                badgeText = getTranslation("scheduled.today");
                badgeColor = "var(--aura-orange)";
                date.getStyle().set("color", "var(--aura-orange)");
            }

            Div cell = new Div();
            cell.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "3px")
                    .set("padding", "var(--vaadin-gap-xs) 0");
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
        }).setHeader(getTranslation("scheduled.due_date")).setAutoWidth(true).setFlexGrow(0).setSortable(true)
                .setComparator(Comparator.comparing(ScheduledTransaction::getNextOccurrence));

        // Amount right after the date: the one value that must never scroll out of view
        pendingGrid.addComponentColumn(st -> createAmountSpan(st.getAmount(), st.getType()))
                .setKey("pending-amount")
                .setHeader(getTranslation("dialog.amount"))
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END).setAutoWidth(true).setFlexGrow(0).setSortable(true)
                .setComparator(Comparator.comparing(ScheduledTransaction::getAmount));

        // Payee
        pendingGrid.addComponentColumn(st -> {
            Span s = new Span(st.getPayee() != null ? st.getPayee() : "—");
            s.getStyle().set("font-weight", "600").set("font-size", "var(--aura-font-size-s)");
            return s;
        }).setKey("pending-payee").setHeader(getTranslation("transactions.payee")).setWidth("9rem").setFlexGrow(1).setSortable(true)
                .setComparator(Comparator.comparing(st -> st.getPayee() != null ? st.getPayee() : ""));

        // Account
        pendingGrid.addComponentColumn(st -> {
            Span s = new Span(accountLabel(st));
            s.getStyle().set("font-size", "var(--aura-font-size-s)");
            return s;
        }).setKey("pending-account").setHeader(getTranslation("dialog.account")).setWidth("7rem").setFlexGrow(1).setSortable(true)
                .setComparator(Comparator.comparing(this::accountLabel));

        // Tags
        com.vaadin.flow.component.grid.Grid.Column<ScheduledTransaction> pendingTagsCol =
        pendingGrid.addComponentColumn(this::buildTagBadges)
                .setHeader(getTranslation("dialog.tags")).setAutoWidth(true);
        com.cuenti.app.views.components.ResponsiveGridColumns.hideBelow(768, pendingGrid,
                java.util.List.of(pendingTagsCol));
        // Phones: due date + amount + actions only
        com.cuenti.app.views.components.ResponsiveGridColumns.hideBelow(520, pendingGrid,
                java.util.List.of(pendingGrid.getColumnByKey("pending-account"),
                        pendingGrid.getColumnByKey("pending-payee")));

        pendingGrid.setPartNameGenerator(st ->
                ScheduledTransactionService.isOverdue(st) ? "overdue-row" : null);

        // Actions: Post (primary), Skip (subtle), Edit (icon)
        pendingGrid.addComponentColumn(st -> {
            Button postBtn = new Button(getTranslation("scheduled.post"), VaadinIcon.CHECK.create(), e -> {
                scheduledService.post(st.getId());
                refreshGrids();
                com.cuenti.app.views.components.UiNotifier.success(getTranslation("scheduled.posted"));
            });
            postBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);

            Button skipBtn = new Button(getTranslation("scheduled.skip"), VaadinIcon.STEP_FORWARD.create(), e -> {
                scheduledService.skip(st.getId());
                refreshGrids();
                // st still carries the pre-skip nextOccurrence (service loaded its own copy)
                com.cuenti.app.views.components.UiNotifier.infoWithAction(
                        getTranslation("scheduled.skipped"),
                        getTranslation("scheduled.undo"),
                        () -> {
                            scheduledService.save(st);
                            refreshGrids();
                        });
            });
            skipBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            Button adjustBtn = new Button(VaadinIcon.SLIDERS.create(), e -> openAdjustPostDialog(st));
            adjustBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            adjustBtn.getElement().setAttribute("title", getTranslation("scheduled.post_adjust"));
            adjustBtn.getElement().setAttribute("aria-label", getTranslation("scheduled.post_adjust"));

            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> openEditDialog(st));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            editBtn.getElement().setAttribute("title", getTranslation("dialog.edit_transaction"));

            HorizontalLayout actions = new HorizontalLayout(postBtn, adjustBtn, skipBtn, editBtn);
            actions.setSpacing(false);
            actions.setAlignItems(Alignment.CENTER);
            actions.getStyle().set("gap", "var(--vaadin-gap-xs)");
            return actions;
        }).setHeader(getTranslation("transactions.actions")).setFrozenToEnd(true).setAutoWidth(true);
    }

    void openEditDialog(ScheduledTransaction st) { // package-visible for tests
        Dialog dialog = new Dialog();
        dialog.setCloseOnOutsideClick(false);
        dialog.setWidth("min(700px, 96vw)");
        dialog.setResizable(false);
        dialog.getElement().getStyle()
                .set("padding", "0")
                .set("overflow-x", "hidden");

        // ── Type selector: coloured pill buttons (as in the transaction dialog) ──
        Button expenseBtn  = new Button(getTranslation("transaction.type.expense"));
        Button incomeBtn   = new Button(getTranslation("transaction.type.income"));
        Button transferBtn = new Button(getTranslation("transaction.type.transfer"));
        Button[] typeBtns = {expenseBtn, incomeBtn, transferBtn};
        Transaction.TransactionType[] types = {
                Transaction.TransactionType.EXPENSE, Transaction.TransactionType.INCOME, Transaction.TransactionType.TRANSFER};
        String[] TYPE_COLORS = {
            "var(--aura-red)",
            "var(--aura-green)",
            "var(--aura-accent-color)"
        };
        Transaction.TransactionType[] selectedType = {
                st.getType() != null ? st.getType() : Transaction.TransactionType.EXPENSE};

        Div accentBar = new Div();
        accentBar.setWidthFull();
        accentBar.setHeight("4px");
        accentBar.getStyle()
                .set("border-radius", "var(--vaadin-radius-l) var(--vaadin-radius-l) 0 0")
                .set("transition", "background 0.2s");

        HorizontalLayout typeRow = new HorizontalLayout(expenseBtn, incomeBtn, transferBtn);
        typeRow.setSpacing(false);
        typeRow.getStyle()
                .set("gap", "var(--vaadin-gap-xs)")
                .set("padding", "var(--vaadin-gap-m) var(--vaadin-gap-l)")
                .set("flex-wrap", "wrap");

        // ── Hero: Amount field ────────────────────────────────────────
        BigDecimalField amount = new BigDecimalField();
        amount.setId("st-amount");
        amount.setWidthFull();
        amount.setRequiredIndicatorVisible(true);
        amount.getStyle()
                .set("font-size", "var(--cuenti-font-size-xxl)")
                .set("font-weight", "800")
                .set("--vaadin-text-field-default-width", "100%");
        amount.getElement().getStyle().set("font-size", "var(--cuenti-font-size-xxl)").set("font-weight", "800");

        Span amountLabel = new Span(getTranslation("dialog.amount").toUpperCase());
        amountLabel.addClassName("text-overline");

        Div heroSection = new Div(amountLabel, amount);
        heroSection.setWidthFull();
        heroSection.getStyle()
                .set("padding", "var(--vaadin-gap-m) var(--vaadin-gap-l) var(--vaadin-gap-l)")
                .set("background", "var(--vaadin-background-container)")
                .set("border-bottom", "1px solid var(--vaadin-border-color-secondary)")
                .set("box-sizing", "border-box");

        // ── Date + account ────────────────────────────────────────────
        DatePicker nextDate = new DatePicker(getTranslation("scheduled.next_date"));
        nextDate.setId("st-next-date");
        com.cuenti.app.views.components.LocalizedDatePicker.applyLocale(nextDate, getLocale());
        nextDate.setWidthFull();

        List<Account> accounts = accountService.getAccountsByUser(currentUser);
        // One "Konto" field; saved to the side the type books on (income: to, expense: from).
        ComboBox<Account> account = new ComboBox<>(getTranslation("dialog.account"));
        account.setId("st-account");
        account.setItems(accounts);
        account.setItemLabelGenerator(Account::getAccountName);
        account.setRequired(true);
        account.setWidthFull();

        ComboBox<Account> toAccount = new ComboBox<>(getTranslation("dialog.to"));
        toAccount.setId("st-to-account");
        toAccount.setItems(accounts);
        toAccount.setItemLabelGenerator(Account::getAccountName);
        toAccount.setWidthFull();

        // ── Payee + Category ──────────────────────────────────────────
        ComboBox<String> payee = new ComboBox<>(getTranslation("transactions.payee"));
        List<Payee> allPayees = payeeService.getAllPayees();
        payee.setItems(allPayees.stream().map(Payee::getName).distinct().toList());
        payee.setAllowCustomValue(true);
        payee.addCustomValueSetListener(e -> payee.setValue(e.getDetail()));
        payee.setWidthFull();
        payee.setPrefixComponent(VaadinIcon.USER.create());

        ComboBox<Category> category = new ComboBox<>(getTranslation("transactions.category"));
        category.setItemLabelGenerator(Category::getFullName);
        category.setAllowCustomValue(true);
        category.setWidthFull();

        // ── Payment + Number ──────────────────────────────────────────
        ComboBox<Transaction.PaymentMethod> paymentMethod = new ComboBox<>(getTranslation("dialog.payment_method"));
        paymentMethod.setItems(Transaction.PaymentMethod.values());
        paymentMethod.setItemLabelGenerator(pm -> pm == Transaction.PaymentMethod.NONE ? getTranslation("dialog.none") : pm.getLabel());
        paymentMethod.setWidthFull();

        TextField number = new TextField(getTranslation("dialog.number"));
        number.setWidthFull();

        // ── Tags + Memo ───────────────────────────────────────────────
        com.cuenti.app.views.components.TagField tags =
                new com.cuenti.app.views.components.TagField(tagService, getTranslation("dialog.tags"));
        tags.setId("st-tags");
        tags.setSuggestionsFor(st.getPayee());
        payee.addValueChangeListener(e -> tags.setSuggestionsFor(e.getValue()));

        TextArea memo = new TextArea(getTranslation("dialog.memo"));
        memo.setWidthFull();
        memo.setMinHeight("60px");
        memo.setMaxHeight("100px");

        // ── Recurrence ────────────────────────────────────────────────
        ComboBox<ScheduledTransaction.RecurrencePattern> pattern = new ComboBox<>(getTranslation("scheduled.recurrence"));
        pattern.setItems(ScheduledTransaction.RecurrencePattern.values());
        pattern.setItemLabelGenerator(this::getRecurrenceLabel);
        pattern.setWidthFull();

        IntegerField recValue = new IntegerField(getTranslation("scheduled.every_x"));
        recValue.setMin(1);
        recValue.setStepButtonsVisible(true);
        recValue.setWidthFull();

        Span preview = new Span();
        preview.setId("st-preview");
        preview.getStyle()
                .set("font-size", "var(--aura-font-size-s)")
                .set("color", "var(--vaadin-text-color-secondary)");

        Checkbox enabled = new Checkbox(getTranslation("scheduled.enabled"));
        enabled.setHelperText(getTranslation("scheduled.enabled_helper"));

        Runnable updatePreview = () -> {
            if (nextDate.getValue() == null || pattern.getValue() == null) {
                preview.setVisible(false);
                return;
            }
            ScheduledTransaction probe = new ScheduledTransaction();
            probe.setRecurrencePattern(pattern.getValue());
            probe.setRecurrenceValue(recValue.getValue());
            DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                    .withLocale(getLocale());
            List<String> dates = new java.util.ArrayList<>();
            LocalDateTime current = nextDate.getValue().atStartOfDay();
            for (int i = 0; i < 3; i++) {
                dates.add(current.format(fmt));
                current = ScheduledTransactionService.advanceOccurrence(current, probe);
            }
            preview.setText(getTranslation("scheduled.preview", String.join(" · ", dates)));
            preview.setVisible(true);
        };
        nextDate.addValueChangeListener(e -> updatePreview.run());
        pattern.addValueChangeListener(e -> updatePreview.run());
        recValue.addValueChangeListener(e -> updatePreview.run());

        // ── Category list follows the type ────────────────────────────
        Runnable updateCategoryItems = () -> {
            Category currentCat = category.getValue();
            if (selectedType[0] == Transaction.TransactionType.INCOME) {
                category.setItems(categoryService.getCategoriesByType(Category.CategoryType.INCOME));
            } else if (selectedType[0] == Transaction.TransactionType.EXPENSE) {
                category.setItems(categoryService.getCategoriesByType(Category.CategoryType.EXPENSE));
            } else {
                category.setItems(categoryService.getAllCategories());
            }
            if (currentCat != null) category.setValue(currentCat);
        };

        category.addCustomValueSetListener(e -> {
            String newCatName = e.getDetail();
            Category.CategoryType categoryType = selectedType[0] == Transaction.TransactionType.INCOME
                    ? Category.CategoryType.INCOME : Category.CategoryType.EXPENSE;
            Category saved;
            if (newCatName != null && newCatName.contains(":")) {
                String[] parts = newCatName.split(":", 2);
                String parentName = parts[0].trim(); String childName = parts[1].trim();
                Category parentCategory = categoryService.getAllCategories().stream()
                        .filter(c -> c.getName().equals(parentName) && c.getParent() == null && c.getType() == categoryType)
                        .findFirst().orElse(null);
                if (parentCategory == null) {
                    parentCategory = categoryService.saveCategory(Category.builder()
                            .name(parentName).type(categoryType).user(currentUser).parent(null).build());
                    Notification.show(getTranslation("categories.parent_created") + ": " + parentName, 3000, Notification.Position.MIDDLE);
                }
                saved = categoryService.saveCategory(Category.builder()
                        .name(childName).type(categoryType).parent(parentCategory).user(currentUser).build());
            } else {
                saved = categoryService.saveCategory(Category.builder()
                        .name(newCatName).type(categoryType).user(currentUser).build());
            }
            updateCategoryItems.run();
            category.setValue(saved);
        });

        // Autofill from payee defaults, as in the transaction dialog
        payee.addValueChangeListener(e -> {
            if (!e.isFromClient() || e.getValue() == null || e.getValue().isEmpty()) return;
            allPayees.stream()
                    .filter(p -> p.getName().equalsIgnoreCase(e.getValue()))
                    .findFirst()
                    .ifPresent(p -> {
                        if (p.getDefaultCategory() != null) {
                            category.setItems(categoryService.getCategoriesByType(p.getDefaultCategory().getType()));
                            category.setValue(p.getDefaultCategory());
                        }
                        if (p.getDefaultPaymentMethod() != null
                                && p.getDefaultPaymentMethod() != Transaction.PaymentMethod.NONE) {
                            paymentMethod.setValue(p.getDefaultPaymentMethod());
                        }
                        if (p.getDefaultMemo() != null && !p.getDefaultMemo().isEmpty()) {
                            memo.setValue(p.getDefaultMemo());
                        }
                        tags.addTags(com.cuenti.app.util.TagNames.parse(p.getDefaultTags()));
                    });
        });

        // ── Type switching ────────────────────────────────────────────
        Runnable applyType = () -> {
            int sel = java.util.Arrays.asList(types).indexOf(selectedType[0]);
            for (int i = 0; i < typeBtns.length; i++) {
                boolean active = (i == sel);
                typeBtns[i].getElement().getStyle()
                        .set("background", active ? TYPE_COLORS[i] : "var(--vaadin-background-container)")
                        .set("color", active ? "white" : "var(--vaadin-text-color-secondary)")
                        .set("border", "none").set("border-radius", "99px")
                        .set("font-weight", active ? "700" : "500")
                        .set("font-size", "var(--aura-font-size-s)")
                        .set("padding", "var(--vaadin-gap-xs) var(--vaadin-gap-m)")
                        .set("cursor", "pointer").set("transition", "all 0.15s");
            }
            accentBar.getStyle().set("background", TYPE_COLORS[sel]);
            boolean isTransfer = selectedType[0] == Transaction.TransactionType.TRANSFER;
            toAccount.setVisible(isTransfer);
            toAccount.setRequired(isTransfer);
            paymentMethod.setVisible(!isTransfer);
            account.setLabel(isTransfer ? getTranslation("dialog.from") : getTranslation("dialog.account"));
            updateCategoryItems.run();
        };
        for (int i = 0; i < typeBtns.length; i++) {
            Transaction.TransactionType type = types[i];
            typeBtns[i].addClickListener(e -> { selectedType[0] = type; applyType.run(); });
        }

        // ── Binder (buffered: cancelling leaves the grid row untouched) ──
        Binder<ScheduledTransaction> binder = new Binder<>(ScheduledTransaction.class);
        binder.forField(nextDate).asRequired()
                .bind(t -> t.getNextOccurrence() != null ? t.getNextOccurrence().toLocalDate() : null,
                        (t, v) -> t.setNextOccurrence(v.atStartOfDay()));
        binder.forField(amount).asRequired()
                .withValidator(v -> v.signum() > 0, getTranslation("dialog.amount_positive"))
                .bind(ScheduledTransaction::getAmount, ScheduledTransaction::setAmount);
        binder.bind(payee, ScheduledTransaction::getPayee, ScheduledTransaction::setPayee);
        binder.forField(account).asRequired()
                .bind(ScheduledTransactionService::primaryAccount, (t, v) -> { });
        binder.forField(toAccount)
                .withValidator(v -> selectedType[0] != Transaction.TransactionType.TRANSFER || v != null,
                        getTranslation("accounts.name_required"))
                .bind(t -> t.getType() == Transaction.TransactionType.TRANSFER ? t.getToAccount() : null, (t, v) -> { });
        binder.forField(pattern).asRequired()
                .bind(ScheduledTransaction::getRecurrencePattern, ScheduledTransaction::setRecurrencePattern);
        binder.bind(recValue, ScheduledTransaction::getRecurrenceValue, ScheduledTransaction::setRecurrenceValue);
        binder.bind(category, ScheduledTransaction::getCategory, ScheduledTransaction::setCategory);
        binder.bind(paymentMethod,
                stx -> stx.getPaymentMethod() != null ? stx.getPaymentMethod() : Transaction.PaymentMethod.NONE,
                ScheduledTransaction::setPaymentMethod);
        binder.bind(number, ScheduledTransaction::getNumber, ScheduledTransaction::setNumber);
        binder.bind(enabled, ScheduledTransaction::isEnabled, ScheduledTransaction::setEnabled);
        binder.bind(memo, ScheduledTransaction::getMemo, ScheduledTransaction::setMemo);
        binder.bind(tags, ScheduledTransaction::getTags, ScheduledTransaction::setTags);

        if (st.getId() == null) {
            st.setType(Transaction.TransactionType.EXPENSE);
            st.setNextOccurrence(LocalDateTime.now());
            st.setPaymentMethod(Transaction.PaymentMethod.NONE);
            st.setRecurrencePattern(ScheduledTransaction.RecurrencePattern.MONTHLY);
            st.setRecurrenceValue(1);
            st.setEnabled(true);
        }
        applyType.run();
        binder.readBean(st);
        updatePreview.run();

        // ── Assemble sections (same order as the transaction dialog) ──
        Div coreSection = createFormSection(null);
        HorizontalLayout row1 = new HorizontalLayout(nextDate, account);
        HorizontalLayout row2 = new HorizontalLayout(payee, category);
        HorizontalLayout row3 = new HorizontalLayout(toAccount, paymentMethod);
        for (HorizontalLayout row : List.of(row1, row2, row3)) {
            // wraps to single column on mobile
            row.setWidthFull(); row.setSpacing(false);
            row.getStyle().set("gap", "var(--vaadin-gap-m)").set("flex-wrap", "wrap");
            row.getChildren().forEach(c -> c.getElement().getStyle().set("flex", "1 1 200px").set("min-width", "0"));
        }
        coreSection.add(row1, row2, row3, tags, memo);

        Div recurrenceSection = createFormSection(getTranslation("scheduled.recurrence"));
        HorizontalLayout recurrenceRow = new HorizontalLayout(pattern, recValue);
        recurrenceRow.setWidthFull(); recurrenceRow.setSpacing(false);
        recurrenceRow.getStyle().set("gap", "var(--vaadin-gap-m)").set("flex-wrap", "wrap");
        pattern.getStyle().set("flex", "2 1 200px").set("min-width", "0");
        recValue.getStyle().set("flex", "1 1 120px").set("min-width", "0");
        recurrenceSection.add(recurrenceRow, preview, enabled);
        recurrenceSection.getStyle().set("border-top", "1px solid var(--vaadin-border-color-secondary)");

        Div extraSection = createFormSection(null);
        extraSection.add(number);
        extraSection.setVisible(false);

        Button moreBtn = new Button(getTranslation("dialog.more_details"), VaadinIcon.ANGLE_DOWN.create());
        moreBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        moreBtn.getStyle().set("font-size", "var(--aura-font-size-xs)").set("color", "var(--vaadin-text-color-secondary)");
        moreBtn.addClickListener(e -> {
            boolean v = !extraSection.isVisible();
            extraSection.setVisible(v);
            moreBtn.setIcon(v ? VaadinIcon.ANGLE_UP.create() : VaadinIcon.ANGLE_DOWN.create());
        });

        Div body = new Div(accentBar, typeRow, heroSection, coreSection, recurrenceSection, moreBtn, extraSection);
        body.setWidthFull();
        body.getStyle()
                .set("display", "flex").set("flex-direction", "column")
                .set("overflow-x", "hidden").set("box-sizing", "border-box");

        // ── Footer ────────────────────────────────────────────────────
        Button save = new Button(st.getId() == null ? getTranslation("dialog.add") : getTranslation("dialog.save"),
                VaadinIcon.CHECK.create(), e -> {
            if (!binder.writeBeanIfValid(st)) {
                return;
            }
            st.setType(selectedType[0]);
            Account[] sides = ScheduledTransactionService.accountsForType(selectedType[0],
                    account.getValue(),
                    selectedType[0] == Transaction.TransactionType.TRANSFER ? toAccount.getValue() : null);
            st.setFromAccount(sides[0]);
            st.setToAccount(sides[1]);
            st.setUser(currentUser);
            scheduledService.save(st);
            refreshGrids();
            dialog.close();
            com.cuenti.app.views.components.UiNotifier.success(getTranslation("dialog.saved"));
        });
        save.setId("st-save");
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancel = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        dialog.add(body);
        dialog.getFooter().add(cancel, save);
        dialog.open();
        amount.focus();
    }

    /** Post one occurrence with a different amount or date (variable bills); the schedule keeps its amount. */
    private void openAdjustPostDialog(ScheduledTransaction st) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("scheduled.post_adjust_title") + ": "
                + (st.getPayee() != null ? st.getPayee() : ""));
        dialog.setWidth("min(420px, 96vw)");

        BigDecimalField amount = new BigDecimalField(getTranslation("dialog.amount"));
        amount.setValue(st.getAmount());
        amount.setRequiredIndicatorVisible(true);
        amount.setWidthFull();

        DatePicker date = new DatePicker(getTranslation("scheduled.booking_date"));
        date.setValue(st.getNextOccurrence().toLocalDate());
        date.setRequiredIndicatorVisible(true);
        date.setWidthFull();

        VerticalLayout body = new VerticalLayout(amount, date);
        body.setPadding(false);
        dialog.add(body);

        Button post = new Button(getTranslation("scheduled.post"), VaadinIcon.CHECK.create(), e -> {
            if (amount.getValue() == null || amount.getValue().signum() <= 0 || date.getValue() == null) {
                amount.setInvalid(amount.getValue() == null || amount.getValue().signum() <= 0);
                date.setInvalid(date.getValue() == null);
                return;
            }
            scheduledService.post(st.getId(), amount.getValue(), date.getValue());
            refreshGrids();
            dialog.close();
            com.cuenti.app.views.components.UiNotifier.success(getTranslation("scheduled.posted"));
        });
        post.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        Button cancel = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(cancel, post);
        dialog.open();
        amount.focus();
    }

    /** Transactions previously posted from this schedule. */
    private void openHistoryDialog(ScheduledTransaction st) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("scheduled.history_title",
                st.getPayee() != null ? st.getPayee() : ""));
        dialog.setWidth("min(560px, 96vw)");

        List<Transaction> history = scheduledService.getHistory(st.getId());
        if (history.isEmpty()) {
            Span empty = new Span(getTranslation("scheduled.history_empty"));
            empty.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            dialog.add(empty);
        } else {
            Grid<Transaction> grid = new Grid<>(Transaction.class, false);
            grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COMPACT);
            grid.setAllRowsVisible(history.size() <= 12);
            if (history.size() > 12) {
                grid.setHeight("420px");
            }
            grid.addColumn(t -> t.getTransactionDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                    .setHeader(getTranslation("scheduled.booking_date")).setAutoWidth(true).setFlexGrow(0);
            grid.addComponentColumn(t -> createAmountSpan(t.getAmount(), t.getType()))
                    .setHeader(getTranslation("dialog.amount"))
                    .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END).setAutoWidth(true).setFlexGrow(0);
            grid.addColumn(t -> {
                Account a = t.getFromAccount() != null ? t.getFromAccount() : t.getToAccount();
                return a != null ? a.getAccountName() : "—";
            }).setHeader(getTranslation("dialog.account")).setFlexGrow(1);
            grid.setItems(history);
            dialog.add(grid);
        }

        Button close = new Button(getTranslation("dialog.close"), e -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(close);
        dialog.open();
    }

    /** Creates a padded section container with an optional all-caps label. */
    private Div createFormSection(String label) {
        Div section = new Div();
        section.setWidthFull();
        section.getStyle()
                .set("display", "flex").set("flex-direction", "column")
                .set("gap", "var(--vaadin-gap-s)")
                .set("padding", "var(--vaadin-gap-m) var(--vaadin-gap-l)")
                .set("box-sizing", "border-box");
        if (label != null && !label.isBlank()) {
            Span lbl = new Span(label.toUpperCase());
            lbl.addClassName("text-overline");
            section.add(lbl);
        }
        return section;
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
            case INCOME   -> { label = getTranslation("transaction.type.income");   bg = "var(--aura-green)"; fg = "white"; }
            case TRANSFER -> { label = getTranslation("transaction.type.transfer"); bg = "var(--aura-accent-color)"; fg = "white"; }
            default       -> { label = getTranslation("transaction.type.expense");  bg = "var(--aura-red)";   fg = "white"; }
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
        span.getStyle().set("font-weight", "700").set("font-size", "var(--aura-font-size-s)");
        if (type == Transaction.TransactionType.EXPENSE)
            span.getStyle().set("color", "var(--aura-red)");
        else if (type == Transaction.TransactionType.INCOME)
            span.getStyle().set("color", "var(--aura-green)");
        else
            span.getStyle().set("color", "var(--aura-accent-color)");
        return span;
    }

    /** Friendly recurrence pill using localized recurrence labels. */
    private Span createRecurrenceBadge(ScheduledTransaction.RecurrencePattern pattern, Integer value) {
        String text = getRecurrenceLabel(pattern);
        if (value != null && value > 1) text = getTranslation("scheduled.every_n").replace("{0}", String.valueOf(value)) + " " + text.toLowerCase();
        Span badge = new Span(text);
        badge.getStyle()
                .set("font-size", "var(--aura-font-size-xs)").set("font-weight", "500")
                .set("padding", "2px 8px").set("border-radius", "99px")
                .set("background", "var(--vaadin-background-container-strong)")
                .set("color", "var(--vaadin-text-color-secondary)")
                .set("white-space", "nowrap");
        return badge;
    }

    private String getRecurrenceLabel(ScheduledTransaction.RecurrencePattern pattern) {
        if (pattern == null) {
            return "";
        }
        return switch (pattern) {
            case DAILY -> getTranslation("scheduled.recurrence.daily");
            case WEEKLY -> getTranslation("scheduled.recurrence.weekly");
            case MONTHLY -> getTranslation("scheduled.recurrence.monthly");
            case MONTHLY_LAST_DAY -> getTranslation("scheduled.recurrence.monthly_last_day");
            case YEARLY -> getTranslation("scheduled.recurrence.yearly");
            case EVERY_FRIDAY -> getTranslation("scheduled.recurrence.every_friday");
            case EVERY_SATURDAY -> getTranslation("scheduled.recurrence.every_saturday");
            case EVERY_WEEKDAY -> getTranslation("scheduled.recurrence.every_weekday");
            case BI_WEEKLY -> getTranslation("scheduled.recurrence.bi_weekly");
        };
    }

    private void refreshGrids() {
        List<ScheduledTransaction> all = scheduledService.getByUser(currentUser);
        templateGrid.setItems(all);
        
        Integer selectedHorizon = horizonSelect.getValue();
        int days = selectedHorizon == null || selectedHorizon < 0 ? 36500 : selectedHorizon;

        LocalDateTime horizonEnd = ScheduledTransactionService.dueCutoff().plusDays(days);
        List<ScheduledTransaction> pending = all.stream()
                .filter(ScheduledTransaction::isEnabled)
                .filter(st -> st.getNextOccurrence().isBefore(horizonEnd))
                .sorted(Comparator.comparing(ScheduledTransaction::getNextOccurrence))
                .toList();
        pendingGrid.setItems(pending);

        postAllButton.setVisible(all.stream().anyMatch(ScheduledTransactionService::isDue));
    }

    private Div createCard() {
        Div card = new Div();
        card.getStyle()
                .set("background-color", "var(--aura-surface-color-solid)")
                .set("border-radius", "16px")
                .set("padding", "var(--vaadin-gap-l)")
                .set("box-shadow", "var(--aura-shadow-m)")
                .set("margin-bottom", "var(--vaadin-gap-m)");
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

    /** Account a schedule books on, taken from the side its type uses; transfers show both. */
    private String accountLabel(ScheduledTransaction st) {
        if (st.getType() == Transaction.TransactionType.TRANSFER) {
            String from = st.getFromAccount() != null ? st.getFromAccount().getAccountName() : "—";
            String to = st.getToAccount() != null ? st.getToAccount().getAccountName() : "—";
            return from + " → " + to;
        }
        Account account = ScheduledTransactionService.primaryAccount(st);
        return account != null ? account.getAccountName() : "—";
    }

    private String formatCurrency(BigDecimal amount) {
        // Was hardcoded to Locale.GERMANY; aligned with the user's locale like all other views
        return com.cuenti.app.util.CurrencyFormat.format(amount, currentUser.getDefaultCurrency(),
                Locale.forLanguageTag(currentUser.getLocale()));
    }
}
