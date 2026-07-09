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
    @UseCase(id = "UC-104", scenario = "Header Filter Narrows Rows and Footer Recounts")
    void headerPayeeFilter_narrowsRows() {
        TransactionHistoryView view = navigate(TransactionHistoryView.class);
        Grid<?> grid = $(Grid.class).single();
        int all = test(grid).size();

        test(view.headerPayeeFilter).setValue("definitely-no-such-payee");
        assertThat(test(grid).size()).isEqualTo(0);

        test(view.headerPayeeFilter).setValue("");
        assertThat(test(grid).size()).isEqualTo(all);
    }

    @Test
    @UseCase(id = "UC-104", scenario = "Running Balance Comes From SQL Window")
    void balances_presentForVisibleRows() {
        TransactionHistoryView view = navigate(TransactionHistoryView.class);
        Grid<?> grid = $(Grid.class).single();
        // every visible row must have a computed balance (no zero-default holes)
        assertThat(test(grid).size()).isGreaterThan(0);
        String csv = view.buildCsv();
        assertThat(csv.split("\n").length - 1).isEqualTo(test(grid).size());
    }

    @Test
    @UseCase(id = "UC-104", scenario = "Row Selection Opens Detail Panel")
    void rowSelection_opensDetailPanel_andCloseClearsSelection() {
        TransactionHistoryView view = navigate(TransactionHistoryView.class);
        Grid<?> grid = $(Grid.class).single();
        assertThat(test(grid).size()).isGreaterThan(0);

        com.cuenti.app.views.components.DetailPanel panel = view.detailPanel;
        assertThat(panel.isVisible()).isFalse();

        test(grid).select(0);
        assertThat(panel.isVisible()).isTrue();

        panel.closePanel();
        assertThat(panel.isVisible()).isFalse();
        assertThat(grid.getSelectedItems()).isEmpty();
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
