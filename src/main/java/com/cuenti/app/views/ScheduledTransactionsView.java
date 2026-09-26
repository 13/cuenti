package com.cuenti.app.views;

import com.cuenti.app.model.*;
import com.cuenti.app.security.SecurityUtils;
import com.cuenti.app.service.*;
import com.cuenti.app.views.components.ScheduledTransactionDialog;
import com.cuenti.app.views.components.TagColorUtil;
import com.cuenti.app.views.components.TransactionDialog;
import com.cuenti.app.views.components.TransactionDialogServices;
import com.cuenti.app.views.components.TransactionFormParts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
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
    private final TransactionDialogServices dialogServices;
    private final UserService userService;
    private final SecurityUtils securityUtils;
    private final User currentUser;

    private final Grid<ScheduledTransaction> templateGrid = new Grid<>(ScheduledTransaction.class, false);
    private final Grid<ScheduledTransaction> pendingGrid = new Grid<>(ScheduledTransaction.class, false);
    private final Select<Integer> horizonSelect = new Select<>();
    private final Button postAllButton = new Button();

    public ScheduledTransactionsView(ScheduledTransactionService scheduledService,
                                     UserService userService, SecurityUtils securityUtils,
                                     TransactionDialogServices dialogServices) {
        this.dialogServices = dialogServices;
        this.scheduledService = scheduledService;
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
        templateGrid.addComponentColumn(st -> {
                    Span badge = createRecurrenceBadge(st.getRecurrencePattern(), st.getRecurrenceValue());
                    String end = ScheduledTransactionDialog.endLabel(this, st, Locale.forLanguageTag(currentUser.getLocale()));
                    if (end.isEmpty()) return badge;
                    Span endSpan = new Span(end);
                    endSpan.getStyle().set("font-size", "var(--aura-font-size-xs)")
                            .set("color", "var(--vaadin-text-color-secondary)").set("white-space", "nowrap");
                    HorizontalLayout cell = new HorizontalLayout(badge, endSpan);
                    cell.setSpacing(false);
                    cell.setAlignItems(Alignment.CENTER);
                    cell.getStyle().set("gap", "var(--vaadin-gap-xs)");
                    return cell;
                })
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
        new ScheduledTransactionDialog(dialogServices, currentUser, st, this::refreshGrids).open();
    }

    /**
     * Post one occurrence after reviewing it in the full transaction form: amount, date,
     * account, category, tags... may differ for this booking; the schedule keeps its values.
     */
    void openAdjustPostDialog(ScheduledTransaction st) { // package-visible for tests
        Transaction draft = scheduledService.draftOccurrence(st.getId());
        TransactionDialog.Options options = new TransactionDialog.Options()
                .allowAddAnother(false)
                .saveLabel(getTranslation("scheduled.post"))
                .saver(tx -> scheduledService.postEdited(st.getId(), tx))
                .onSaved(this::refreshGrids);
        TransactionDialog dialog = new TransactionDialog(dialogServices, currentUser, draft, options);
        dialog.setHeaderTitle(getTranslation("scheduled.post_adjust_title") + ": "
                + (st.getPayee() != null ? st.getPayee() : ""));
        dialog.open();
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
        String text = TransactionFormParts.recurrenceLabel(this, pattern);
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
