package com.cuenti.app.views;

import com.cuenti.app.usecase.UseCase;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.textfield.BigDecimalField;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Budgets: create with validation, uniqueness per category, delete flow.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "demo")
class UC106BudgetsTest extends SpringBrowserlessTest {

    private static Predicate<Button> icon(String vaadinIcon) {
        return b -> b.getIcon() instanceof Icon i
                && vaadinIcon.equals(i.getElement().getAttribute("icon"));
    }

    @Test
    @UseCase(id = "UC-106", scenario = "Create and Delete Budget")
    void createBudget_thenDelete() {
        navigate(BudgetManagementView.class);
        Grid<?> grid = $(Grid.class).single();
        int before = test(grid).size();

        // open dialog, pick first category, set limit
        test($(Button.class).withCondition(icon("vaadin:plus")).single()).click();
        assertThat($(Dialog.class).exists()).isTrue();

        ComboBox<Object> category = $(ComboBox.class).single();
        var categoryTester = test(category);
        Object first = categoryTester.getSuggestionItems().get(0);
        categoryTester.selectItem(((com.cuenti.app.model.Category) first).getFullName());
        test($(BigDecimalField.class).single()).setValue(new BigDecimal("300"));
        test($(Button.class).withCondition(icon("vaadin:check")).single()).click();

        assertThat(test(grid).size()).isEqualTo(before + 1);

        // delete again (cleanup through the UI)
        var cell = test(grid).getCellComponent(before, grid.getColumns().size() - 1);
        test($(Button.class, cell).withCondition(icon("vaadin:trash")).single()).click();
        test($(ConfirmDialog.class).single()).confirm();
        assertThat(test(grid).size()).isEqualTo(before);
    }

    @Test
    @UseCase(id = "UC-106", scenario = "A1: Missing Limit Blocks Save")
    void missingLimit_marksFieldInvalid() {
        navigate(BudgetManagementView.class);
        test($(Button.class).withCondition(icon("vaadin:plus")).single()).click();

        ComboBox<Object> category = $(ComboBox.class).single();
        var categoryTester = test(category);
        Object first = categoryTester.getSuggestionItems().get(0);
        categoryTester.selectItem(((com.cuenti.app.model.Category) first).getFullName());
        test($(Button.class).withCondition(icon("vaadin:check")).single()).click();

        assertThat($(BigDecimalField.class).single().isInvalid()).isTrue();
        assertThat($(Dialog.class).exists()).isTrue();
    }
}
