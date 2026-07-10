package com.cuenti.app.views;

import com.cuenti.app.model.Transaction;
import com.cuenti.app.repository.AuditLogRepository;
import com.cuenti.app.usecase.UseCase;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.BigDecimalField;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bulk transaction actions, saved filter views and the audit trail.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "demo", roles = {"USER", "ADMIN"})
class UC108BulkViewsAuditTest extends SpringBrowserlessTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    private static Predicate<Button> icon(String vaadinIcon) {
        return b -> b.getIcon() instanceof Icon i
                && vaadinIcon.equals(i.getElement().getAttribute("icon"));
    }

    private Button dialogSaveButton() {
        Dialog dialog = $(Dialog.class).single();
        return $(Button.class, dialog)
                .withCondition(b -> b.getElement().getThemeList().contains("primary"))
                .single();
    }

    @Test
    @UseCase(id = "UC-108", scenario = "Bulk mode selects rows and applies a category")
    void bulkMode_setCategoryOnSelectedRows() {
        navigate(TransactionHistoryView.class);
        TransactionHistoryView view = (TransactionHistoryView) getCurrentView();
        Grid<Transaction> grid = $(Grid.class).single();
        assertThat(test(grid).size()).isGreaterThan(0);

        test(view.bulkBtn).click();
        assertThat(view.bulkBar.isVisible()).isTrue();

        Transaction first = (Transaction) test(grid).getRow(0);
        grid.select(first);
        assertThat(grid.getSelectedItems()).hasSize(1);

        // open the bulk category dialog from the bulk bar
        Button categoryBtn = $(Button.class, view.bulkBar)
                .withCondition(icon("vaadin:sitemap")).single();
        test(categoryBtn).click();

        ComboBox<?> combo = $(ComboBox.class, $(Dialog.class).single()).single();
        var tester = test(combo);
        Object cat = tester.getSuggestionItems().get(0);
        tester.selectItem(((com.cuenti.app.model.Category) cat).getFullName());
        test(dialogSaveButton()).click();

        assertThat($(Notification.class).exists()).isTrue();
        // grid refreshed, selection cleared, bulk mode still active
        assertThat(grid.getSelectedItems()).isEmpty();

        test(view.bulkBtn).click();
        assertThat(view.bulkBar.isVisible()).isFalse();
    }

    @Test
    @UseCase(id = "UC-108", scenario = "Saved view round-trips the filter state")
    void savedView_serializeAndApply_restoresFilters() {
        navigate(TransactionHistoryView.class);
        TransactionHistoryView view = (TransactionHistoryView) getCurrentView();

        test(view.searchField).setValue("Miete");
        String params = view.serializeFilters();
        assertThat(params).contains("q=Miete");

        test(view.searchField).setValue("");
        view.applyFilterParams(params);
        assertThat(view.searchField.getValue()).isEqualTo("Miete");
    }

    @Test
    @UseCase(id = "UC-108", scenario = "Data changes appear in the audit log")
    void budgetCreation_writesAuditEntry_andAuditViewRenders() {
        long before = auditLogRepository.count();

        // create a budget through the UI (audited in BudgetService)
        navigate(BudgetManagementView.class);
        test($(Button.class).withCondition(icon("vaadin:plus")).first()).click();
        var comboTester = test($(ComboBox.class, $(Dialog.class).single()).single());
        Object cat = comboTester.getSuggestionItems().get(comboTester.getSuggestionItems().size() - 1);
        comboTester.selectItem(((com.cuenti.app.model.Category) cat).getFullName());
        test($(BigDecimalField.class).first()).setValue(new BigDecimal("77.77"));
        test(dialogSaveButton()).click();

        assertThat(auditLogRepository.count()).isGreaterThan(before);

        // admin audit view renders and shows the entry
        navigate(AuditLogView.class);
        AuditLogView auditView = (AuditLogView) getCurrentView();
        Grid<?> auditGrid = $(Grid.class).single();
        assertThat(test(auditGrid).size()).isGreaterThan(0);

        test(auditView.filterField).setValue("Budget");
        assertThat(test(auditGrid).size()).isGreaterThan(0);

        // cleanup: delete the budget created above (also audited)
        navigate(BudgetManagementView.class);
        Grid<?> budgetGrid = $(Grid.class).single();
        int rows = test(budgetGrid).size();
        for (int i = 0; i < rows; i++) {
            String limit = test(budgetGrid).getCellText(i, 1);
            if (limit.contains("77,77") || limit.contains("77.77")) {
                var cell = test(budgetGrid).getCellComponent(i, budgetGrid.getColumns().size() - 1);
                Button trash = $(Button.class, cell).withCondition(icon("vaadin:trash")).single();
                test(trash).click();
                test($(ConfirmDialog.class).single()).confirm();
                break;
            }
        }
    }
}
