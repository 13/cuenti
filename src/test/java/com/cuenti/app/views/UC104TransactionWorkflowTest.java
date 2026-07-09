package com.cuenti.app.views;

import com.cuenti.app.usecase.UseCase;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Transaction workflow additions: CSV export of the filtered rows and the
 * Alt+N shortcut for opening the new-transaction dialog.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "demo")
class UC104TransactionWorkflowTest extends SpringBrowserlessTest {

    @Test
    @UseCase(id = "UC-104", scenario = "CSV Export Matches Filtered Rows")
    void csvExport_containsHeaderAndFilteredRows() {
        TransactionHistoryView view = navigate(TransactionHistoryView.class);
        Grid<?> grid = $(Grid.class).single();

        String csv = view.buildCsv();
        String[] lines = csv.split("\n");

        assertThat(lines[0]).isEqualTo("Date,Type,Payee,Account,Category,Tags,Amount,Memo");
        assertThat(lines.length - 1).isEqualTo(test(grid).size());
    }

    @Test
    @UseCase(id = "UC-104", scenario = "Alt+N Opens New Transaction Dialog")
    void altN_opensNewTransactionDialog() {
        navigate(TransactionHistoryView.class);
        assertThat($(Dialog.class).exists()).isFalse();

        fireShortcut(Key.KEY_N, KeyModifier.ALT);

        assertThat($(Dialog.class).exists()).isTrue();
    }
}
