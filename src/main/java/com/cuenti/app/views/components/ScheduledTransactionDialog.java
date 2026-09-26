package com.cuenti.app.views.components;

import com.cuenti.app.model.Account;
import com.cuenti.app.model.Category;
import com.cuenti.app.model.Payee;
import com.cuenti.app.model.ScheduledTransaction;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import com.cuenti.app.service.AccountService;
import com.cuenti.app.service.CategoryService;
import com.cuenti.app.service.PayeeService;
import com.cuenti.app.service.ScheduledTransactionService;
import com.cuenti.app.service.TagService;
import com.cuenti.app.util.AccountSides;
import com.cuenti.app.util.TagNames;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Create/edit form for a schedule, laid out like {@link TransactionDialog}: type pills,
 * amount hero, date + account, payee + category, tags, memo, then the repetition
 * (pattern, interval, optional end) with a preview of the next dates. Edits are
 * buffered, so cancelling leaves the schedule untouched.
 */
public class ScheduledTransactionDialog extends Dialog {

    /** How a schedule ends. */
    enum EndMode { NEVER, DATE, COUNT }

    private final AccountService accountService;
    private final CategoryService categoryService;
    private final PayeeService payeeService;
    private final TagService tagService;
    private final ScheduledTransactionService scheduledService;
    private final User currentUser;
    private Transaction.TransactionType selectedType;

    public ScheduledTransactionDialog(TransactionDialogServices services, User currentUser,
                                      ScheduledTransaction st, Runnable onSaved) {
        this.accountService = services.getAccountService();
        this.categoryService = services.getCategoryService();
        this.payeeService = services.getPayeeService();
        this.tagService = services.getTagService();
        this.scheduledService = services.getScheduledTransactionService();
        this.currentUser = currentUser;

        setCloseOnOutsideClick(false);
        setWidth("min(700px, 96vw)");
        setResizable(false);
        getElement().getStyle().set("padding", "0").set("overflow-x", "hidden");

        if (st.getId() == null && st.getType() == null) {
            st.setType(Transaction.TransactionType.EXPENSE);
        }
        if (st.getNextOccurrence() == null) {
            st.setNextOccurrence(LocalDateTime.now());
        }
        if (st.getPaymentMethod() == null) {
            st.setPaymentMethod(Transaction.PaymentMethod.NONE);
        }
        if (st.getRecurrencePattern() == null) {
            st.setRecurrencePattern(ScheduledTransaction.RecurrencePattern.MONTHLY);
            st.setRecurrenceValue(1);
        }
        if (st.getId() == null) {
            st.setEnabled(true);
        }
        selectedType = st.getType();

        // ── Type pills + hero ─────────────────────────────────────────
        Button[] typeBtns = TransactionFormParts.typeButtons(this);
        Div accentBar = TransactionFormParts.accentBar();
        HorizontalLayout typeRow = TransactionFormParts.typeRow(typeBtns);

        BigDecimalField amount = new BigDecimalField();
        amount.setId("st-amount");
        amount.setWidthFull();
        amount.setRequiredIndicatorVisible(true);
        TransactionFormParts.styleHeroAmount(amount);
        Div heroSection = TransactionFormParts.heroSection(this, amount);

        // ── Date + account ────────────────────────────────────────────
        DatePicker nextDate = new DatePicker(getTranslation("scheduled.next_date"));
        nextDate.setId("st-next-date");
        LocalizedDatePicker.applyLocale(nextDate, userLocale());
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

        ComboBox<Transaction.PaymentMethod> paymentMethod = new ComboBox<>(getTranslation("dialog.payment_method"));
        paymentMethod.setItems(Transaction.PaymentMethod.values());
        paymentMethod.setItemLabelGenerator(pm -> pm == Transaction.PaymentMethod.NONE ? getTranslation("dialog.none") : pm.getLabel());
        paymentMethod.setWidthFull();

        TextField number = new TextField(getTranslation("dialog.number"));
        number.setWidthFull();

        TagField tags = new TagField(tagService, getTranslation("dialog.tags"));
        tags.setId("st-tags");
        tags.setSuggestionsFor(st.getPayee());
        payee.addValueChangeListener(e -> tags.setSuggestionsFor(e.getValue()));

        TextArea memo = new TextArea(getTranslation("dialog.memo"));
        memo.setWidthFull();
        memo.setMinHeight("60px");
        memo.setMaxHeight("100px");

        // ── Repetition ────────────────────────────────────────────────
        ComboBox<ScheduledTransaction.RecurrencePattern> pattern = new ComboBox<>(getTranslation("scheduled.recurrence"));
        pattern.setId("st-pattern");
        List<ScheduledTransaction.RecurrencePattern> patterns = new ArrayList<>(ScheduledTransaction.RecurrencePattern.SELECTABLE);
        if (!patterns.contains(st.getRecurrencePattern())) {
            patterns.add(st.getRecurrencePattern()); // legacy value on an old schedule stays visible
        }
        pattern.setItems(patterns);
        pattern.setItemLabelGenerator(p -> TransactionFormParts.recurrenceLabel(this, p));
        pattern.setWidthFull();

        IntegerField interval = new IntegerField(getTranslation("scheduled.every_x"));
        interval.setId("st-interval");
        interval.setMin(1);
        interval.setStepButtonsVisible(true);
        interval.setWidthFull();
        Span intervalUnit = new Span();
        interval.setSuffixComponent(intervalUnit);

        Select<EndMode> endMode = new Select<>();
        endMode.setId("st-end-mode");
        endMode.setLabel(getTranslation("scheduled.end"));
        endMode.setItems(EndMode.values());
        endMode.setItemLabelGenerator(m -> getTranslation("scheduled.end." + m.name().toLowerCase(Locale.ROOT)));
        endMode.setWidthFull();

        DatePicker endDate = new DatePicker(getTranslation("scheduled.end_date"));
        endDate.setId("st-end-date");
        LocalizedDatePicker.applyLocale(endDate, userLocale());
        endDate.setWidthFull();

        IntegerField remaining = new IntegerField(getTranslation("scheduled.remaining"));
        remaining.setId("st-remaining");
        remaining.setMin(1);
        remaining.setStepButtonsVisible(true);
        remaining.setWidthFull();

        Span preview = new Span();
        preview.setId("st-preview");
        preview.getStyle()
                .set("font-size", "var(--aura-font-size-s)")
                .set("color", "var(--vaadin-text-color-secondary)");

        Checkbox enabled = new Checkbox(getTranslation("scheduled.enabled"));
        enabled.setHelperText(getTranslation("scheduled.enabled_helper"));

        Runnable updateRepetition = () -> {
            ScheduledTransaction.RecurrencePattern p = pattern.getValue();
            boolean hasInterval = p != null && p.hasInterval();
            interval.setVisible(hasInterval);
            intervalUnit.setText(p == null ? "" : unitLabel(p, interval.getValue()));
            EndMode mode = endMode.getValue() != null ? endMode.getValue() : EndMode.NEVER;
            endDate.setVisible(mode == EndMode.DATE);
            remaining.setVisible(mode == EndMode.COUNT);

            if (nextDate.getValue() == null || p == null) {
                preview.setVisible(false);
                return;
            }
            ScheduledTransaction probe = new ScheduledTransaction();
            probe.setRecurrencePattern(p);
            probe.setRecurrenceValue(interval.getValue());
            DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(userLocale());
            int limit = mode == EndMode.COUNT && remaining.getValue() != null ? Math.min(3, remaining.getValue()) : 3;
            List<String> dates = new ArrayList<>();
            LocalDateTime current = nextDate.getValue().atStartOfDay();
            for (int i = 0; i < limit; i++) {
                if (mode == EndMode.DATE && endDate.getValue() != null && current.toLocalDate().isAfter(endDate.getValue())) {
                    break;
                }
                dates.add(current.format(fmt));
                current = ScheduledTransactionService.advanceOccurrence(current, probe);
            }
            preview.setText(dates.isEmpty()
                    ? getTranslation("scheduled.preview_none")
                    : getTranslation("scheduled.preview", String.join(" · ", dates)));
            preview.setVisible(true);
        };
        nextDate.addValueChangeListener(e -> updateRepetition.run());
        pattern.addValueChangeListener(e -> updateRepetition.run());
        interval.addValueChangeListener(e -> updateRepetition.run());
        endMode.addValueChangeListener(e -> updateRepetition.run());
        endDate.addValueChangeListener(e -> updateRepetition.run());
        remaining.addValueChangeListener(e -> updateRepetition.run());

        // ── Category list follows the type ────────────────────────────
        Runnable updateCategoryItems = () -> {
            Category currentCat = category.getValue();
            if (selectedType == Transaction.TransactionType.INCOME) {
                category.setItems(categoryService.getCategoriesByType(Category.CategoryType.INCOME));
            } else if (selectedType == Transaction.TransactionType.EXPENSE) {
                category.setItems(categoryService.getCategoriesByType(Category.CategoryType.EXPENSE));
            } else {
                category.setItems(categoryService.getAllCategories());
            }
            if (currentCat != null) category.setValue(currentCat);
        };
        category.addCustomValueSetListener(e -> {
            Category.CategoryType categoryType = selectedType == Transaction.TransactionType.INCOME
                    ? Category.CategoryType.INCOME : Category.CategoryType.EXPENSE;
            Category saved = TransactionFormParts.createCategory(this, categoryService, currentUser, e.getDetail(), categoryType);
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
                        tags.addTags(TagNames.parse(p.getDefaultTags()));
                    });
        });

        // ── Type switching ────────────────────────────────────────────
        Runnable applyType = () -> {
            TransactionFormParts.applyType(typeBtns, accentBar, selectedType);
            boolean isTransfer = selectedType == Transaction.TransactionType.TRANSFER;
            toAccount.setVisible(isTransfer);
            toAccount.setRequired(isTransfer);
            paymentMethod.setVisible(!isTransfer);
            account.setLabel(isTransfer ? getTranslation("dialog.from") : getTranslation("dialog.account"));
            updateCategoryItems.run();
        };
        for (int i = 0; i < typeBtns.length; i++) {
            Transaction.TransactionType type = TransactionFormParts.TYPES[i];
            typeBtns[i].addClickListener(e -> { selectedType = type; applyType.run(); });
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
                .withValidator(v -> selectedType != Transaction.TransactionType.TRANSFER || v != null,
                        getTranslation("accounts.name_required"))
                .bind(t -> t.getType() == Transaction.TransactionType.TRANSFER ? t.getToAccount() : null, (t, v) -> { });
        binder.forField(pattern).asRequired()
                .bind(ScheduledTransaction::getRecurrencePattern, ScheduledTransaction::setRecurrencePattern);
        binder.bind(interval, ScheduledTransaction::getRecurrenceValue, ScheduledTransaction::setRecurrenceValue);
        binder.bind(endMode,
                t -> t.getRemainingOccurrences() != null ? EndMode.COUNT
                        : t.getEndDate() != null ? EndMode.DATE : EndMode.NEVER,
                (t, v) -> { });
        binder.forField(endDate)
                .withValidator(v -> endMode.getValue() != EndMode.DATE || v != null, getTranslation("scheduled.end_date_required"))
                .withValidator(v -> endMode.getValue() != EndMode.DATE || v == null || nextDate.getValue() == null
                        || !v.isBefore(nextDate.getValue()), getTranslation("scheduled.end_before_next"))
                .bind(ScheduledTransaction::getEndDate, (t, v) -> { });
        binder.forField(remaining)
                .withValidator(v -> endMode.getValue() != EndMode.COUNT || (v != null && v > 0),
                        getTranslation("scheduled.remaining_required"))
                .bind(ScheduledTransaction::getRemainingOccurrences, (t, v) -> { });
        binder.bind(category, ScheduledTransaction::getCategory, ScheduledTransaction::setCategory);
        binder.bind(paymentMethod,
                stx -> stx.getPaymentMethod() != null ? stx.getPaymentMethod() : Transaction.PaymentMethod.NONE,
                ScheduledTransaction::setPaymentMethod);
        binder.bind(number, ScheduledTransaction::getNumber, ScheduledTransaction::setNumber);
        binder.bind(enabled, ScheduledTransaction::isEnabled, ScheduledTransaction::setEnabled);
        binder.bind(memo, ScheduledTransaction::getMemo, ScheduledTransaction::setMemo);
        binder.bind(tags, ScheduledTransaction::getTags, ScheduledTransaction::setTags);

        applyType.run();
        binder.readBean(st);
        updateRepetition.run();

        // ── Assemble sections (same order as the transaction dialog) ──
        Div coreSection = TransactionFormParts.formSection(null);
        coreSection.add(TransactionFormParts.row(nextDate, account),
                TransactionFormParts.row(payee, category),
                TransactionFormParts.row(toAccount, paymentMethod),
                tags, memo);

        Div recurrenceSection = TransactionFormParts.formSection(getTranslation("scheduled.recurrence"));
        HorizontalLayout recurrenceRow = new HorizontalLayout(pattern, interval);
        recurrenceRow.setWidthFull(); recurrenceRow.setSpacing(false);
        recurrenceRow.getStyle().set("gap", "var(--vaadin-gap-m)").set("flex-wrap", "wrap");
        pattern.getStyle().set("flex", "2 1 200px").set("min-width", "0");
        interval.getStyle().set("flex", "1 1 140px").set("min-width", "0");
        recurrenceSection.add(recurrenceRow, TransactionFormParts.row(endMode, endDate, remaining), preview, enabled);
        recurrenceSection.getStyle().set("border-top", "1px solid var(--vaadin-border-color-secondary)");

        Div extraSection = TransactionFormParts.formSection(null);
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
            st.setType(selectedType);
            Account[] sides = AccountSides.forType(selectedType, account.getValue(),
                    selectedType == Transaction.TransactionType.TRANSFER ? toAccount.getValue() : null);
            st.setFromAccount(sides[0]);
            st.setToAccount(sides[1]);
            EndMode mode = endMode.getValue() != null ? endMode.getValue() : EndMode.NEVER;
            st.setEndDate(mode == EndMode.DATE ? endDate.getValue() : null);
            st.setRemainingOccurrences(mode == EndMode.COUNT ? remaining.getValue() : null);
            if (!st.getRecurrencePattern().hasInterval()) {
                st.setRecurrenceValue(1);
            }
            st.setUser(currentUser);
            scheduledService.save(st);
            onSaved.run();
            close();
            UiNotifier.success(getTranslation("dialog.saved"));
        });
        save.setId("st-save");
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancel = new Button(getTranslation("dialog.cancel"), e -> close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        add(body);
        getFooter().add(cancel, save);
        addOpenedChangeListener(e -> { if (e.isOpened()) amount.focus(); });
    }

    private Locale userLocale() {
        return currentUser.getLocale() != null ? Locale.forLanguageTag(currentUser.getLocale()) : getLocale();
    }

    /** "Tage", "Wochen", ... after the interval, singular when it is 1. */
    private String unitLabel(ScheduledTransaction.RecurrencePattern p, Integer value) {
        boolean one = value == null || value == 1;
        String key = switch (p) {
            case DAILY -> "day";
            case WEEKLY -> "week";
            case MONTHLY, MONTHLY_LAST_DAY -> "month";
            case YEARLY -> "year";
            default -> null;
        };
        return key == null ? "" : getTranslation("scheduled.unit." + key + (one ? "" : "s"));
    }

    /** End text for the list: e.g. "noch 3" or "bis 31.12.2026"; empty for open-ended schedules. */
    public static String endLabel(com.vaadin.flow.component.Component i18n, ScheduledTransaction st, Locale locale) {
        if (st.getRemainingOccurrences() != null) {
            return i18n.getTranslation("scheduled.remaining_badge", st.getRemainingOccurrences());
        }
        if (st.getEndDate() != null) {
            return i18n.getTranslation("scheduled.until_badge",
                    st.getEndDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)));
        }
        return "";
    }
}
