package com.cuenti.app.views;

import com.cuenti.app.usecase.UseCase;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.router.QueryParameters;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Global quick search: Ctrl+K opens the dialog; ?q= pre-filters the
 * transactions view.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "demo")
class UC107QuickSearchTest extends SpringBrowserlessTest {

    @Test
    @UseCase(id = "UC-107", scenario = "Ctrl+K Opens Quick Search")
    void ctrlK_opensQuickSearch() {
        navigate(DashboardView.class);
        assertThat($(Dialog.class).exists()).isFalse();

        fireShortcut(Key.KEY_K, KeyModifier.CONTROL);

        assertThat($(Dialog.class).exists()).isTrue();
    }

    @Test
    @UseCase(id = "UC-107", scenario = "Deep Link Pre-Filters Transactions")
    void queryParameter_prefiltersTransactions() {
        navigate(DashboardView.class);
        UI.getCurrent().navigate(TransactionHistoryView.class, QueryParameters.of("q", "Global Tech"));

        TransactionHistoryView view = (TransactionHistoryView) getCurrentView();
        assertThat(view.searchFieldValue()).isEqualTo("Global Tech");
    }
}
