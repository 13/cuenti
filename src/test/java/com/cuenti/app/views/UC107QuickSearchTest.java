package com.cuenti.app.views;

import com.cuenti.app.model.Account;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import com.cuenti.app.repository.TransactionRepository;
import com.cuenti.app.service.AccountService;
import com.cuenti.app.service.UserService;
import com.cuenti.app.usecase.UseCase;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.QueryParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Global quick search: Ctrl+K opens the dialog; ?q= / ?payee= / ?category= /
 * ?tag= pre-filter the transactions view over the whole history.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "demo")
class UC107QuickSearchTest extends SpringBrowserlessTest {

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private AccountService accountService;

    /** Tagged transaction outside the default current-month window. */
    private Transaction oldTagged;

    @BeforeEach
    void createOldTaggedTransaction() {
        User demo = userService.findByUsername("demo");
        Account account = accountService.getAccountsByUser(demo).stream()
                .filter(a -> a.getAccountType() == Account.AccountType.CURRENT)
                .findFirst().orElseThrow();
        Transaction t = new Transaction();
        t.setType(Transaction.TransactionType.EXPENSE);
        t.setFromAccount(account);
        t.setAmount(new BigDecimal("12.34"));
        t.setPayee("EVIVA Radsport");
        t.setTags("Hobby, ebike");
        t.setMemo("uc107");
        t.setTransactionDate(LocalDateTime.of(2025, 2, 3, 12, 0));
        t.setStatus(Transaction.TransactionStatus.COMPLETED);
        oldTagged = transactionRepository.save(t);
    }

    @AfterEach
    void deleteOldTaggedTransaction() {
        transactionRepository.deleteById(oldTagged.getId());
    }

    @SuppressWarnings("unchecked")
    private List<Long> visibleIds() {
        Grid<Transaction> grid = $(Grid.class).single();
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < test(grid).size(); i++) {
            ids.add(((Transaction) test(grid).getRow(i)).getId());
        }
        return ids;
    }

    private TransactionHistoryView deepLink(String filter, String value) {
        navigate(DashboardView.class);
        UI.getCurrent().navigate(TransactionHistoryView.class, QueryParameters.of(filter, value));
        return (TransactionHistoryView) getCurrentView();
    }

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
        TransactionHistoryView view = deepLink("q", "Global Tech");
        assertThat(view.searchFieldValue()).isEqualTo("Global Tech");
    }

    @Test
    @UseCase(id = "UC-107", scenario = "Free Text Search Matches Tags Across Whole History")
    void queryParameter_matchesTagsOutsideCurrentMonth() {
        deepLink("q", "ebike");
        assertThat(visibleIds()).containsExactly(oldTagged.getId());
    }

    @Test
    @UseCase(id = "UC-107", scenario = "Tag Result Sets Tag Filter")
    void tagParameter_setsTagFilterAndSearchesWholeHistory() {
        TransactionHistoryView view = deepLink("tag", "EBIKE");
        assertThat(view.headerTagFilter.getValue()).isEqualTo("ebike");
        assertThat(visibleIds()).containsExactly(oldTagged.getId());
    }

    @Test
    @UseCase(id = "UC-107", scenario = "Payee Result Sets Payee Filter")
    void payeeParameter_setsPayeeFilterAndSearchesWholeHistory() {
        TransactionHistoryView view = deepLink("payee", "EVIVA");
        assertThat(view.headerPayeeFilter.getValue()).isEqualTo("EVIVA");
        assertThat(visibleIds()).containsExactly(oldTagged.getId());
    }

    @Test
    @UseCase(id = "UC-107", scenario = "Tags Used Only On Transactions Are Suggested")
    void quickSearch_suggestsTagsFromTransactions() {
        navigate(DashboardView.class);
        fireShortcut(Key.KEY_K, KeyModifier.CONTROL);
        Dialog dialog = $(Dialog.class).single();

        test($(TextField.class, dialog).single()).setValue("ebi");

        assertThat($(Button.class, dialog).withText("ebike").exists()).isTrue();
    }
}
