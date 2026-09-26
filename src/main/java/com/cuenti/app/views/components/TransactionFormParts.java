package com.cuenti.app.views.components;

import com.cuenti.app.model.Category;
import com.cuenti.app.model.ScheduledTransaction;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import com.cuenti.app.service.CategoryService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.util.List;

/**
 * Building blocks shared by the transaction and the scheduled-transaction dialog so
 * both look and behave the same: type pills with accent bar, amount hero, padded
 * sections, two-column rows, and creating a category from typed text.
 */
public final class TransactionFormParts {

    /** Pill order everywhere: expense, income, transfer. */
    public static final Transaction.TransactionType[] TYPES = {
            Transaction.TransactionType.EXPENSE, Transaction.TransactionType.INCOME, Transaction.TransactionType.TRANSFER};

    private static final String[] TYPE_COLORS = {
            "var(--aura-red)",
            "var(--aura-green)",
            "var(--aura-accent-color)"
    };

    private TransactionFormParts() {
    }

    /** Thin coloured bar on top of the dialog that follows the selected type. */
    public static Div accentBar() {
        Div bar = new Div();
        bar.setWidthFull();
        bar.setHeight("4px");
        bar.getStyle()
                .set("border-radius", "var(--vaadin-radius-l) var(--vaadin-radius-l) 0 0")
                .set("transition", "background 0.2s");
        return bar;
    }

    /** Expense/income/transfer pill buttons, in {@link #TYPES} order. */
    public static Button[] typeButtons(Component i18n) {
        return new Button[]{
                new Button(i18n.getTranslation("transaction.type.expense")),
                new Button(i18n.getTranslation("transaction.type.income")),
                new Button(i18n.getTranslation("transaction.type.transfer"))};
    }

    public static HorizontalLayout typeRow(Button[] buttons) {
        HorizontalLayout row = new HorizontalLayout(buttons);
        row.setSpacing(false);
        row.getStyle()
                .set("gap", "var(--vaadin-gap-xs)")
                .set("padding", "var(--vaadin-gap-m) var(--vaadin-gap-l)")
                .set("flex-wrap", "wrap");
        return row;
    }

    /** Colours the active pill and the accent bar for {@code type}. */
    public static void applyType(Button[] buttons, Div accentBar, Transaction.TransactionType type) {
        int selected = Math.max(0, List.of(TYPES).indexOf(type));
        for (int i = 0; i < buttons.length; i++) {
            boolean active = i == selected;
            buttons[i].getElement().getStyle()
                    .set("background", active ? TYPE_COLORS[i] : "var(--vaadin-background-container)")
                    .set("color", active ? "white" : "var(--vaadin-text-color-secondary)")
                    .set("border", "none")
                    .set("border-radius", "99px")
                    .set("font-weight", active ? "700" : "500")
                    .set("font-size", "var(--aura-font-size-s)")
                    .set("padding", "var(--vaadin-gap-xs) var(--vaadin-gap-m)")
                    .set("cursor", "pointer")
                    .set("transition", "all 0.15s");
        }
        accentBar.getStyle().set("background", TYPE_COLORS[selected]);
    }

    /** Big amount input styling used in the hero. */
    public static void styleHeroAmount(Component amountField) {
        amountField.getElement().getStyle()
                .set("font-size", "var(--cuenti-font-size-xxl)")
                .set("font-weight", "800")
                .set("--vaadin-text-field-default-width", "100%");
    }

    /** Shaded block with the overline label and the amount (plus optional trailing controls). */
    public static Div heroSection(Component i18n, Component content) {
        Span label = new Span(i18n.getTranslation("dialog.amount").toUpperCase());
        label.addClassName("text-overline");
        Div hero = new Div(label, content);
        hero.setWidthFull();
        hero.getStyle()
                .set("padding", "var(--vaadin-gap-m) var(--vaadin-gap-l) var(--vaadin-gap-l)")
                .set("background", "var(--vaadin-background-container)")
                .set("border-bottom", "1px solid var(--vaadin-border-color-secondary)")
                .set("box-sizing", "border-box");
        return hero;
    }

    /** Padded column with an optional all-caps label. */
    public static Div formSection(String label) {
        Div section = new Div();
        section.setWidthFull();
        section.getStyle()
                .set("display", "flex").set("flex-direction", "column")
                .set("gap", "var(--vaadin-gap-s)")
                .set("padding", "var(--vaadin-gap-m) var(--vaadin-gap-l)")
                .set("box-sizing", "border-box");
        if (label != null && !label.isBlank()) {
            Span overline = new Span(label.toUpperCase());
            overline.addClassName("text-overline");
            section.add(overline);
        }
        return section;
    }

    /** Two (or more) fields side by side that wrap to one column on phones. */
    public static HorizontalLayout row(Component... fields) {
        HorizontalLayout row = new HorizontalLayout(fields);
        row.setWidthFull();
        row.setSpacing(false);
        row.getStyle().set("gap", "var(--vaadin-gap-m)").set("flex-wrap", "wrap");
        row.getChildren().forEach(c -> c.getElement().getStyle().set("flex", "1 1 200px").set("min-width", "0"));
        return row;
    }

    /**
     * Creates the category typed into a category box. "Parent:Child" creates the child
     * under that parent, creating the parent first when it does not exist yet.
     */
    public static Category createCategory(Component i18n, CategoryService categoryService, User user,
                                          String text, Category.CategoryType type) {
        if (text != null && text.contains(":")) {
            String[] parts = text.split(":", 2);
            String parentName = parts[0].trim();
            String childName = parts[1].trim();
            Category parent = categoryService.getAllCategories().stream()
                    .filter(c -> c.getName().equals(parentName) && c.getParent() == null && c.getType() == type)
                    .findFirst().orElse(null);
            if (parent == null) {
                parent = categoryService.saveCategory(Category.builder()
                        .name(parentName).type(type).user(user).parent(null).build());
                Notification.show(i18n.getTranslation("categories.parent_created") + ": " + parentName,
                        3000, Notification.Position.MIDDLE);
            }
            return categoryService.saveCategory(Category.builder()
                    .name(childName).type(type).parent(parent).user(user).build());
        }
        return categoryService.saveCategory(Category.builder()
                .name(text).type(type).user(user).build());
    }

    /** Localized name of a recurrence pattern. */
    public static String recurrenceLabel(Component i18n, ScheduledTransaction.RecurrencePattern pattern) {
        if (pattern == null) {
            return "";
        }
        return switch (pattern) {
            case DAILY -> i18n.getTranslation("scheduled.recurrence.daily");
            case WEEKLY -> i18n.getTranslation("scheduled.recurrence.weekly");
            case MONTHLY -> i18n.getTranslation("scheduled.recurrence.monthly");
            case MONTHLY_LAST_DAY -> i18n.getTranslation("scheduled.recurrence.monthly_last_day");
            case YEARLY -> i18n.getTranslation("scheduled.recurrence.yearly");
            case EVERY_FRIDAY -> i18n.getTranslation("scheduled.recurrence.every_friday");
            case EVERY_SATURDAY -> i18n.getTranslation("scheduled.recurrence.every_saturday");
            case EVERY_WEEKDAY -> i18n.getTranslation("scheduled.recurrence.every_weekday");
            case BI_WEEKLY -> i18n.getTranslation("scheduled.recurrence.bi_weekly");
        };
    }
}
