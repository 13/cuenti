package com.cuenti.app.views;

import com.cuenti.app.model.Budget;
import com.cuenti.app.model.Category;
import com.cuenti.app.model.User;
import com.cuenti.app.security.SecurityUtils;
import com.cuenti.app.service.BudgetService;
import com.cuenti.app.service.CategoryService;
import com.cuenti.app.service.UserService;
import com.cuenti.app.util.CurrencyFormat;
import com.cuenti.app.views.components.DeleteConfirm;
import com.cuenti.app.views.components.EmptyStateNotice;
import com.cuenti.app.views.components.UiNotifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

/**
 * Monthly category budgets with live spend tracking.
 */
@Route(value = "budgets", layout = MainLayout.class)
@PermitAll
public class BudgetManagementView extends VerticalLayout implements HasDynamicTitle {

    @Override
    public String getPageTitle() {
        return getTranslation("budgets.title") + " | " + getTranslation("app.name");
    }

    private final BudgetService budgetService;
    private final CategoryService categoryService;
    private final User currentUser;

    private final Grid<Budget> grid = new Grid<>(Budget.class, false);
    private Map<Long, BigDecimal> spentByCategory = Map.of();

    public BudgetManagementView(BudgetService budgetService, CategoryService categoryService,
                                UserService userService, SecurityUtils securityUtils) {
        this.budgetService = budgetService;
        this.categoryService = categoryService;

        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        this.currentUser = userService.findByUsername(username);

        addClassNames("page-scroll", "page-shell");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle().set("padding", "var(--vaadin-gap-m)").set("overflow", "hidden");

        setupUI();
        refreshGrid();
    }

    private void setupUI() {
        Span title = new Span(getTranslation("budgets.title"));
        title.addComponentAsFirst(VaadinIcon.PIGGY_BANK.create());
        title.addClassName("page-title");

        Button addButton = new Button(getTranslation("budgets.add"), VaadinIcon.PLUS.create(),
                e -> openBudgetDialog(Budget.builder().user(currentUser).build()));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(addButton);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(JustifyContentMode.END);
        toolbar.addClassName("card-toolbar");

        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        grid.setSizeFull();
        grid.addItemDoubleClickListener(e -> openBudgetDialog(e.getItem()));

        Button emptyAdd = new Button(getTranslation("empty.hint"),
                e -> openBudgetDialog(Budget.builder().user(currentUser).build()));
        emptyAdd.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        grid.setEmptyStateComponent(new EmptyStateNotice(
                VaadinIcon.PIGGY_BANK, getTranslation("empty.title"), null, emptyAdd));

        grid.addColumn(b -> b.getCategory().getFullName())
                .setHeader(getTranslation("budgets.category"))
                .setSortable(true).setAutoWidth(true);

        grid.addColumn(b -> formatCurrency(b.getMonthlyLimit()))
                .setHeader(getTranslation("budgets.limit"))
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setSortable(true)
                .setComparator(Comparator.comparing(Budget::getMonthlyLimit))
                .setAutoWidth(true);

        grid.addColumn(b -> formatCurrency(spent(b)))
                .setHeader(getTranslation("budgets.spent"))
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addComponentColumn(this::progressCell)
                .setHeader(getTranslation("budgets.progress"))
                .setFlexGrow(1);

        grid.addComponentColumn(b -> {
            Span remaining = new Span(formatCurrency(b.getMonthlyLimit().subtract(spent(b))));
            remaining.addClassName(spent(b).compareTo(b.getMonthlyLimit()) > 0
                    ? "amount-negative" : "amount-positive");
            return remaining;
        }).setHeader(getTranslation("budgets.remaining"))
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addComponentColumn(b -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> openBudgetDialog(b));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            editBtn.setTooltipText(getTranslation("transactions.edit"));
            editBtn.getElement().setAttribute("aria-label", getTranslation("transactions.edit"));

            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e ->
                    DeleteConfirm.show(
                            getTranslation("dialog.confirm_delete"),
                            getTranslation("dialog.confirm_delete_message") + " \"" + b.getCategory().getFullName() + "\"?",
                            getTranslation("dialog.delete"),
                            getTranslation("dialog.cancel"),
                            getTranslation("error.delete_failed"),
                            () -> {
                                budgetService.deleteBudget(b);
                                refreshGrid();
                                UiNotifier.success(getTranslation("budgets.deleted"));
                            }));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            deleteBtn.setTooltipText(getTranslation("transactions.delete"));
            deleteBtn.getElement().setAttribute("aria-label", getTranslation("transactions.delete"));

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader(getTranslation("transactions.actions")).setFrozenToEnd(true).setAutoWidth(true);

        Div card = new Div(toolbar, grid);
        card.setSizeFull();
        card.addClassName("card");
        card.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--vaadin-gap-s)");

        add(title, card);
        expand(card);
    }

    private BigDecimal spent(Budget b) {
        return spentByCategory.getOrDefault(b.getCategory().getId(), BigDecimal.ZERO);
    }

    private Div progressCell(Budget b) {
        BigDecimal limit = b.getMonthlyLimit();
        BigDecimal spent = spent(b);
        double ratio = limit.compareTo(BigDecimal.ZERO) > 0
                ? spent.divide(limit, 4, RoundingMode.HALF_UP).doubleValue() : 0;
        boolean over = ratio > 1.0;

        ProgressBar bar = new ProgressBar(0, 1, Math.min(1.0, ratio));
        bar.setWidthFull();
        if (over) {
            bar.getStyle().set("--vaadin-progress-bar-fill-background", "var(--aura-red)");
        } else if (ratio > 0.85) {
            bar.getStyle().set("--vaadin-progress-bar-fill-background", "var(--aura-orange)");
        }

        Span pct = new Span(Math.round(ratio * 100) + "%");
        pct.getStyle().set("font-size", "var(--aura-font-size-xs)")
                .set("color", over ? "var(--aura-red-text)" : "var(--vaadin-text-color-secondary)")
                .set("flex-shrink", "0").set("min-width", "40px").set("text-align", "right");

        Div cell = new Div(bar, pct);
        cell.getStyle().set("display", "flex").set("align-items", "center")
                .set("gap", "var(--vaadin-gap-s)").set("width", "100%");
        return cell;
    }

    private void openBudgetDialog(Budget budget) {
        Dialog dialog = new Dialog();
        dialog.setCloseOnOutsideClick(false);
        dialog.setWidth("min(420px, 96vw)");
        dialog.setHeaderTitle(budget.getId() == null
                ? getTranslation("budgets.add") : getTranslation("budgets.edit"));

        ComboBox<Category> categoryCombo = new ComboBox<>(getTranslation("budgets.category"));
        categoryCombo.setItems(categoryService.getAllCategories().stream()
                .filter(c -> c.getType() == Category.CategoryType.EXPENSE)
                .sorted(Comparator.comparing(Category::getFullName))
                .toList());
        categoryCombo.setItemLabelGenerator(Category::getFullName);
        categoryCombo.setWidthFull();
        categoryCombo.setValue(budget.getCategory());

        BigDecimalField limitField = new BigDecimalField(getTranslation("budgets.limit"));
        limitField.setWidthFull();
        limitField.setPrefixComponent(VaadinIcon.MONEY.create());
        limitField.setValue(budget.getMonthlyLimit());

        Div body = new Div(categoryCombo, limitField);
        body.getStyle().set("display", "flex").set("flex-direction", "column")
                .set("gap", "var(--vaadin-gap-s)")
                .set("padding", "var(--vaadin-gap-m) var(--vaadin-gap-l)");
        dialog.add(body);

        Button saveButton = new Button(getTranslation("dialog.save"), e -> {
            Category category = categoryCombo.getValue();
            BigDecimal limit = limitField.getValue();
            if (category == null || limit == null || limit.compareTo(BigDecimal.ZERO) <= 0) {
                categoryCombo.setInvalid(category == null);
                limitField.setInvalid(limit == null || limit.compareTo(BigDecimal.ZERO) <= 0);
                return;
            }
            if (budgetService.existsForCategory(currentUser, category.getId(), budget.getId())) {
                categoryCombo.setInvalid(true);
                categoryCombo.setErrorMessage(getTranslation("budgets.exists"));
                return;
            }
            budget.setCategory(category);
            budget.setMonthlyLimit(limit);
            try {
                budgetService.saveBudget(budget);
                refreshGrid();
                dialog.close();
                UiNotifier.success(getTranslation("budgets.saved"));
            } catch (Exception ex) {
                UiNotifier.error(getTranslation("error.generic"));
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickShortcut(com.vaadin.flow.component.Key.ENTER);

        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
        categoryCombo.focus();
    }

    private void refreshGrid() {
        spentByCategory = budgetService.getSpentThisMonth(currentUser);
        grid.setItems(budgetService.getBudgets(currentUser));
    }

    private String formatCurrency(BigDecimal amount) {
        return CurrencyFormat.format(amount, currentUser.getDefaultCurrency(),
                Locale.forLanguageTag(currentUser.getLocale()));
    }
}
