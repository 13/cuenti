package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.*;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
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
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.component.select.Select;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Route(value = "scheduled", layout = MainLayout.class)
@PageTitle("Scheduled Transactions | Cuenti")
@PermitAll
public class ScheduledTransactionsView extends VerticalLayout {

    private final ScheduledTransactionService scheduledService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final UserService userService;
    private final SecurityUtils securityUtils;
    private final User currentUser;

    private final Grid<ScheduledTransaction> templateGrid = new Grid<>(ScheduledTransaction.class, false);
    private final Grid<ScheduledTransaction> pendingGrid = new Grid<>(ScheduledTransaction.class, false);
    private final Select<String> horizonSelect = new Select<>();

    public ScheduledTransactionsView(ScheduledTransactionService scheduledService, AccountService accountService,
                                     CategoryService categoryService, UserService userService, SecurityUtils securityUtils) {
        this.scheduledService = scheduledService;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.userService = userService;
        this.securityUtils = securityUtils;

        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        this.currentUser = userService.findByUsername(username);

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("background-color", "var(--lumo-contrast-5pct)");
        getStyle().set("overflow", "hidden");

        setupUI();
        refreshGrids();
    }

    private void setupUI() {
        H2 title = new H2(getTranslation("scheduled.title"));
        title.getStyle().set("margin-top", "0").set("color", "var(--lumo-primary-text-color)");

        horizonSelect.setLabel("Showing transactions due within:");
        horizonSelect.setItems("7 Days", "30 Days", "90 Days", "Unlimited (All)");
        horizonSelect.setValue("7 Days");
        horizonSelect.addValueChangeListener(e -> refreshGrids());
        horizonSelect.setWidth("200px");

        Button addButton = new Button(getTranslation("scheduled.new"), VaadinIcon.PLUS.create(), e -> openEditDialog(new ScheduledTransaction()));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(horizonSelect, addButton);
        toolbar.setAlignItems(Alignment.BASELINE);
        toolbar.setSpacing(true);

        setupTemplateGrid();
        setupPendingGrid();

        // Add title above the card
        add(title);

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

        HorizontalLayout cardHeader = new HorizontalLayout(toolbar);
        cardHeader.setWidthFull();
        cardHeader.setJustifyContentMode(JustifyContentMode.END);

        Div content = new Div();
        content.getStyle().set("overflow-y", "auto").set("flex-grow", "1");

        H3 pendingTitle = new H3(getTranslation("scheduled.pending_title"));
        H3 schedulesTitle = new H3(getTranslation("scheduled.list_title"));
        schedulesTitle.getStyle().set("margin-top", "var(--lumo-space-l)");

        content.add(pendingTitle, pendingGrid, schedulesTitle, templateGrid);

        card.add(cardHeader, content);
        add(card);
        expand(card);
    }

    private void setupTemplateGrid() {
        templateGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        templateGrid.setAllRowsVisible(true);
        
        templateGrid.addColumn(ScheduledTransaction::getPayee).setHeader(getTranslation("transactions.payee")).setAutoWidth(true).setSortable(true);
        
        templateGrid.addComponentColumn(st -> {
            Span span = new Span(formatCurrency(st.getAmount()));
            span.getStyle().set("font-weight", "bold");
            if (st.getType() == Transaction.TransactionType.EXPENSE) span.getStyle().set("color", "var(--lumo-error-text-color)");
            else if (st.getType() == Transaction.TransactionType.INCOME) span.getStyle().set("color", "var(--lumo-success-text-color)");
            return span;
        }).setHeader(getTranslation("dialog.amount")).setAutoWidth(true).setSortable(true);

        templateGrid.addColumn(st -> st.getRecurrencePattern().name() + (st.getRecurrenceValue() != null ? " (" + st.getRecurrenceValue() + ")" : ""))
                .setHeader(getTranslation("scheduled.recurrence")).setAutoWidth(true).setSortable(true);
        
        templateGrid.addColumn(st -> st.getNextOccurrence().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                .setHeader(getTranslation("scheduled.next_date")).setAutoWidth(true).setSortable(true);
        
        templateGrid.addComponentColumn(st -> {
            Checkbox enabled = new Checkbox(st.isEnabled());
            enabled.addValueChangeListener(e -> {
                st.setEnabled(e.getValue());
                scheduledService.save(st);
                refreshGrids();
            });
            return enabled;
        }).setHeader(getTranslation("scheduled.enabled")).setAutoWidth(true);

        templateGrid.addComponentColumn(st -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> openEditDialog(st));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            
            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e -> {
                scheduledService.delete(st);
                refreshGrids();
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            
            HorizontalLayout hl = new HorizontalLayout(editBtn, deleteBtn);
            hl.setSpacing(false);
            hl.getStyle().set("gap", "var(--lumo-space-xs)");
            return hl;
        }).setHeader(getTranslation("transactions.actions")).setFrozenToEnd(true).setAutoWidth(true);
    }

    private void setupPendingGrid() {
        pendingGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        pendingGrid.setAllRowsVisible(true);
        
        pendingGrid.addComponentColumn(st -> {
            Span date = new Span(st.getNextOccurrence().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            if (st.getNextOccurrence().isBefore(LocalDateTime.now())) {
                date.getStyle().set("color", "var(--lumo-error-color)").set("font-weight", "bold");
                date.getElement().setAttribute("title", getTranslation("scheduled.late"));
                
                Icon warnIcon = VaadinIcon.WARNING.create();
                warnIcon.getStyle().set("color", "var(--lumo-error-color)").set("font-size", "var(--lumo-font-size-s)");
                HorizontalLayout hl = new HorizontalLayout(warnIcon, date);
                hl.setSpacing(false);
                hl.getStyle().set("gap", "var(--lumo-space-xs)");
                return hl;
            }
            return date;
        }).setHeader(getTranslation("scheduled.due_date")).setAutoWidth(true).setSortable(true);

        pendingGrid.addColumn(ScheduledTransaction::getPayee).setHeader(getTranslation("transactions.payee")).setAutoWidth(true);
        
        pendingGrid.addComponentColumn(st -> {
            Span span = new Span(formatCurrency(st.getAmount()));
            span.getStyle().set("font-weight", "bold");
            if (st.getType() == Transaction.TransactionType.EXPENSE) span.getStyle().set("color", "var(--lumo-error-text-color)");
            else if (st.getType() == Transaction.TransactionType.INCOME) span.getStyle().set("color", "var(--lumo-success-text-color)");
            return span;
        }).setHeader(getTranslation("dialog.amount")).setAutoWidth(true);

        pendingGrid.addComponentColumn(st -> {
            Button postBtn = new Button(getTranslation("scheduled.post"), VaadinIcon.CHECK.create(), e -> {
                scheduledService.post(st.getId());
                refreshGrids();
                Notification.show(getTranslation("scheduled.posted"));
            });
            postBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

            Button skipBtn = new Button(getTranslation("scheduled.skip"), VaadinIcon.STEP_FORWARD.create(), e -> {
                scheduledService.skip(st.getId());
                refreshGrids();
                Notification.show(getTranslation("scheduled.skipped"));
            });
            skipBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> openEditDialog(st));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            HorizontalLayout actions = new HorizontalLayout(postBtn, skipBtn, editBtn);
            actions.setSpacing(false);
            actions.getStyle().set("gap", "var(--lumo-space-xs)");
            return actions;
        }).setHeader(getTranslation("transactions.actions")).setFrozenToEnd(true).setAutoWidth(true);
    }

    private void openEditDialog(ScheduledTransaction st) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(st.getId() == null ? getTranslation("dialog.add_transaction") : getTranslation("dialog.edit_transaction"));
        dialog.setWidth("600px");

        FormLayout form = new FormLayout();
        
        RadioButtonGroup<Transaction.TransactionType> type = new RadioButtonGroup<>(getTranslation("dialog.type"));
        type.setItems(Transaction.TransactionType.values());
        type.addThemeVariants(RadioGroupVariant.LUMO_HELPER_ABOVE_FIELD);
        
        DatePicker nextDate = new DatePicker(getTranslation("scheduled.next_date"));
        BigDecimalField amount = new BigDecimalField(getTranslation("dialog.amount"));
        TextField payee = new TextField(getTranslation("transactions.payee"));
        
        ComboBox<Account> fromAccount = new ComboBox<>(getTranslation("dialog.from"));
        fromAccount.setItems(accountService.getAccountsByUser(currentUser));
        fromAccount.setItemLabelGenerator(Account::getAccountName);
        
        ComboBox<Account> toAccount = new ComboBox<>(getTranslation("dialog.to"));
        toAccount.setItems(accountService.getAccountsByUser(currentUser));
        toAccount.setItemLabelGenerator(Account::getAccountName);
        toAccount.setVisible(false);

        ComboBox<ScheduledTransaction.RecurrencePattern> pattern = new ComboBox<>(getTranslation("scheduled.recurrence"));
        pattern.setItems(ScheduledTransaction.RecurrencePattern.values());
        
        IntegerField recValue = new IntegerField("Every X");
        recValue.setMin(1);
        recValue.setStepButtonsVisible(true);

        ComboBox<Category> category = new ComboBox<>(getTranslation("transactions.category"));
        category.setItems(categoryService.getAllCategories());
        category.setItemLabelGenerator(Category::getFullName);

        TextArea memo = new TextArea(getTranslation("dialog.memo"));

        type.addValueChangeListener(e -> {
            toAccount.setVisible(e.getValue() == Transaction.TransactionType.TRANSFER);
        });

        Binder<ScheduledTransaction> binder = new Binder<>(ScheduledTransaction.class);
        binder.forField(type).asRequired().bind(ScheduledTransaction::getType, ScheduledTransaction::setType);
        binder.forField(nextDate).asRequired().bind(t -> t.getNextOccurrence().toLocalDate(), (t, v) -> t.setNextOccurrence(v.atStartOfDay()));
        binder.forField(amount).asRequired().bind(ScheduledTransaction::getAmount, ScheduledTransaction::setAmount);
        binder.bind(payee, ScheduledTransaction::getPayee, ScheduledTransaction::setPayee);
        binder.forField(fromAccount).asRequired().bind(ScheduledTransaction::getFromAccount, ScheduledTransaction::setFromAccount);
        binder.bind(toAccount, ScheduledTransaction::getToAccount, ScheduledTransaction::setToAccount);
        binder.forField(pattern).asRequired().bind(ScheduledTransaction::getRecurrencePattern, ScheduledTransaction::setRecurrencePattern);
        binder.bind(recValue, ScheduledTransaction::getRecurrenceValue, ScheduledTransaction::setRecurrenceValue);
        binder.bind(category, ScheduledTransaction::getCategory, ScheduledTransaction::setCategory);
        binder.bind(memo, ScheduledTransaction::getMemo, ScheduledTransaction::setMemo);

        if (st.getId() != null) {
            binder.setBean(st);
            toAccount.setVisible(st.getType() == Transaction.TransactionType.TRANSFER);
        } else {
            st.setType(Transaction.TransactionType.EXPENSE);
            st.setNextOccurrence(LocalDateTime.now());
            binder.setBean(st);
        }

        form.add(type, nextDate, amount, fromAccount, toAccount, payee, category, pattern, recValue, memo);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));
        form.setColspan(type, 2);
        form.setColspan(memo, 2);

        Button save = new Button(getTranslation("dialog.save"), e -> {
            if (binder.validate().isOk()) {
                st.setUser(currentUser);
                scheduledService.save(st);
                refreshGrids();
                dialog.close();
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button(getTranslation("dialog.cancel"), e -> dialog.close());

        dialog.add(form);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private void refreshGrids() {
        List<ScheduledTransaction> all = scheduledService.getByUser(currentUser);
        templateGrid.setItems(all);
        
        int days = switch (horizonSelect.getValue()) {
            case "7 Days" -> 7;
            case "30 Days" -> 30;
            case "90 Days" -> 90;
            default -> 36500; // ~100 years for unlimited
        };

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
