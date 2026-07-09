package com.cuenti.app.views;

import com.cuenti.app.usecase.UseCase;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.browserless.SpringBrowserlessTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Payee management: deleting is guarded by a confirmation dialog and
 * cancelling leaves the data untouched.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "demo")
class UC102ManagePayeesTest extends SpringBrowserlessTest {

    private static Predicate<Button> icon(String vaadinIcon) {
        return b -> b.getIcon() instanceof Icon i
                && vaadinIcon.equals(i.getElement().getAttribute("icon"));
    }

    @Test
    @UseCase(id = "UC-102", scenario = "A1: Delete Requires Confirmation")
    void deletePayee_requiresConfirmation_cancelKeepsRow() {
        navigate(PayeeManagementView.class);

        Grid<?> grid = $(Grid.class).single();
        int initialSize = test(grid).size();
        assertThat(initialSize).isGreaterThan(0); // demo data payees

        var cell = test(grid).getCellComponent(0, grid.getColumns().size() - 1);
        test($(Button.class, cell).withCondition(icon("vaadin:trash")).single()).click();

        assertThat($(ConfirmDialog.class).exists()).isTrue();
        test($(ConfirmDialog.class).single()).cancel();

        assertThat(test(grid).size()).isEqualTo(initialSize);
    }
}
