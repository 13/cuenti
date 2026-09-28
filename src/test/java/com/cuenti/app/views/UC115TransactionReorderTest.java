package com.cuenti.app.views;

import com.cuenti.app.model.Account;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import com.cuenti.app.repository.AccountRepository;
import com.cuenti.app.repository.TransactionRepository;
import com.cuenti.app.repository.UserRepository;
import com.cuenti.app.service.TransactionService;
import com.cuenti.app.usecase.UseCase;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.grid.Grid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Same-day reorder buttons: each click moves the row exactly one visible place,
 * regardless of time of day, duplicate sort orders or rows hidden by the type tab.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "uc115")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UC115TransactionReorderTest extends SpringBrowserlessTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 14);

    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionService transactionService;

    private Account account;

    @BeforeAll
    void createUserAndAccount() {
        User user = userRepository.findByUsername("uc115").orElseGet(() -> {
            User u = new User();
            u.setUsername("uc115");
            u.setEmail("uc115@test.local");
            u.setPassword("not-used");
            u.setFirstName("UC115");
            u.setLastName("Tester");
            u.setEnabled(true);
            u.setRoles(new HashSet<>(Set.of("ROLE_USER")));
            u.setLocale("de-DE");
            u.setDefaultCurrency("EUR");
            return userRepository.save(u);
        });
        Account a = new Account();
        a.setUser(user);
        a.setAccountName("UC115 Konto");
        a.setAccountNumber("UC115-0001");
        a.setAccountType(Account.AccountType.CURRENT);
        a.setStartBalance(BigDecimal.ZERO);
        a.setBalance(BigDecimal.ZERO);
        account = accountRepository.save(a);
    }

    @AfterEach
    void removeFixtures() {
        transactionRepository.findByAccount(account).forEach(transactionService::deleteTransaction);
    }

    private void book(String payee, Transaction.TransactionType type, LocalTime time, int sortOrder) {
        Transaction t = new Transaction();
        t.setType(type);
        if (type == Transaction.TransactionType.INCOME) {
            t.setToAccount(account);
        } else {
            t.setFromAccount(account);
        }
        t.setAmount(BigDecimal.TEN);
        t.setPayee(payee);
        t.setTransactionDate(DAY.atTime(time));
        t.setSortOrder(sortOrder);
        transactionService.saveTransaction(t);
    }

    private TransactionHistoryView openAccount(String type) {
        TransactionHistoryView view = navigate(TransactionHistoryView.class);
        view.applyFilterParams("account=" + account.getId() + "|type=" + type + "|from=|to=|q=");
        return view;
    }

    @SuppressWarnings("unchecked")
    private List<Transaction> rows() {
        Grid<Transaction> grid = $(Grid.class).single();
        List<Transaction> rows = new ArrayList<>();
        for (int i = 0; i < test(grid).size(); i++) {
            rows.add((Transaction) test(grid).getRow(i));
        }
        return rows;
    }

    private List<String> payees() {
        return rows().stream().map(Transaction::getPayee).toList();
    }

    private Transaction row(String payee) {
        return rows().stream().filter(t -> payee.equals(t.getPayee())).findFirst().orElseThrow();
    }

    @Test
    @UseCase(id = "UC-115", scenario = "Reorder Wins Over Time of Day")
    void move_ignoresTimeOfDay() {
        book("Morning", Transaction.TransactionType.EXPENSE, LocalTime.of(9, 0), 20);
        book("Evening", Transaction.TransactionType.EXPENSE, LocalTime.of(18, 0), 10);
        TransactionHistoryView view = openAccount("ALL");
        assertThat(payees()).containsExactly("Morning", "Evening");

        view.moveTransaction(row("Morning"), 1);
        assertThat(payees()).containsExactly("Evening", "Morning");

        view.moveTransaction(row("Morning"), -1);
        assertThat(payees()).containsExactly("Morning", "Evening");
    }

    @Test
    @UseCase(id = "UC-115", scenario = "Duplicate Sort Orders Move One Place")
    void move_withDuplicateSortOrders_movesExactlyOnePlace() {
        book("A", Transaction.TransactionType.EXPENSE, LocalTime.MIDNIGHT, 0);
        book("B", Transaction.TransactionType.EXPENSE, LocalTime.MIDNIGHT, 0);
        book("C", Transaction.TransactionType.EXPENSE, LocalTime.MIDNIGHT, 0);
        TransactionHistoryView view = openAccount("ALL");
        List<String> before = payees();

        view.moveTransaction(row(before.get(2)), -1);
        assertThat(payees()).containsExactly(before.get(0), before.get(2), before.get(1));
    }

    @Test
    @UseCase(id = "UC-115", scenario = "Hidden Rows Are Skipped")
    void move_swapsWithVisibleNeighbour_notHiddenRow() {
        book("Top", Transaction.TransactionType.EXPENSE, LocalTime.MIDNIGHT, 30);
        book("Salary", Transaction.TransactionType.INCOME, LocalTime.MIDNIGHT, 20);
        book("Bottom", Transaction.TransactionType.EXPENSE, LocalTime.MIDNIGHT, 10);
        TransactionHistoryView view = openAccount("EXPENSE");
        assertThat(payees()).containsExactly("Top", "Bottom");

        view.moveTransaction(row("Bottom"), -1);
        assertThat(payees()).containsExactly("Bottom", "Top");

        // the hidden income kept its place relative to the moved rows
        assertThat(transactionRepository.findByAccount(account).stream().map(Transaction::getPayee))
                .containsExactly("Bottom", "Salary", "Top");
    }
}
