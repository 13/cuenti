package com.cuenti.app.views;

import com.cuenti.app.model.Account;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import com.cuenti.app.repository.TransactionRepository;
import com.cuenti.app.service.AccountService;
import com.cuenti.app.service.UserService;
import com.cuenti.app.usecase.UseCase;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.grid.Grid;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
 * Loading a large window must not issue a query per transaction (EAGER splits)
 * or reload once per filter control when a saved view is applied.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "demo")
class UC104TransactionGridPerformanceTest extends SpringBrowserlessTest {

    private static final int SEEDED = 500;

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final List<Long> seededIds = new ArrayList<>();
    private Statistics statistics;

    @BeforeEach
    void seedTransactions() {
        User demo = userService.findByUsername("demo");
        Account account = accountService.getAccountsByUser(demo).stream()
                .filter(a -> a.getAccountType() == Account.AccountType.CURRENT)
                .findFirst().orElseThrow();
        List<Transaction> batch = new ArrayList<>();
        for (int i = 0; i < SEEDED; i++) {
            Transaction t = new Transaction();
            t.setType(Transaction.TransactionType.EXPENSE);
            t.setFromAccount(account);
            t.setAmount(new BigDecimal("1.00"));
            t.setPayee("Perf " + i);
            t.setTransactionDate(LocalDateTime.of(2020, 1, 1, 12, 0).plusHours(i));
            t.setStatus(Transaction.TransactionStatus.COMPLETED);
            batch.add(t);
        }
        transactionRepository.saveAll(batch).forEach(t -> seededIds.add(t.getId()));

        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @AfterEach
    void cleanUp() {
        statistics.setStatisticsEnabled(false);
        transactionRepository.deleteAllByIdInBatch(seededIds);
    }

    @Test
    @UseCase(id = "UC-104", scenario = "Whole History Loads Without Per-Row Queries")
    void applyingWholeHistoryView_usesBoundedQueries() {
        TransactionHistoryView view = navigate(TransactionHistoryView.class);

        statistics.clear();
        long started = System.nanoTime();
        view.applyFilterParams("account=all|type=ALL|from=|to=|q=");
        long millis = (System.nanoTime() - started) / 1_000_000;
        long statements = statistics.getPrepareStatementCount();
        System.out.printf("whole history: %d rows, %d statements, %d ms%n",
                test($(Grid.class).single()).size(), statements, millis);

        assertThat(test($(Grid.class).single()).size()).isGreaterThanOrEqualTo(SEEDED);
        // one window load: grid query, split batches, running balances, accounts, parents
        assertThat(statements).isLessThan(40);
        // applying a saved view reloads the window once, not once per control
        // (statistics are global, so count the grid query itself, not all queries)
        long windowLoads = java.util.Arrays.stream(statistics.getQueries())
                .filter(q -> q.startsWith("SELECT DISTINCT t FROM Transaction t") && q.contains(":from"))
                .mapToLong(q -> statistics.getQueryStatistics(q).getExecutionCount())
                .sum();
        assertThat(windowLoads).isEqualTo(1);
    }
}
