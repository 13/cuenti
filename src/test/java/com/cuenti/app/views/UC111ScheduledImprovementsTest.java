package com.cuenti.app.views;

import com.cuenti.app.model.Account;
import com.cuenti.app.model.ScheduledTransaction;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import com.cuenti.app.repository.AccountRepository;
import com.cuenti.app.repository.ScheduledTransactionRepository;
import com.cuenti.app.repository.TransactionRepository;
import com.cuenti.app.repository.UserRepository;
import com.cuenti.app.service.ScheduledTransactionService;
import com.cuenti.app.usecase.UseCase;
import com.cuenti.app.util.ScheduledChangeBroadcaster;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.select.Select;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scheduled-transaction follow-ups: one "due" cutoff shared by badge, list and
 * post-all; date-only schedules; split badge tooltip; per-user badge look-ahead
 * and list horizon; adjust-and-post with booking history; midnight badge refresh;
 * amount column placed so it never scrolls out of view.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "uc111")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UC111ScheduledImprovementsTest extends SpringBrowserlessTest {

    private static final String FIXTURE_MEMO = "UC111-fixture";

    @Autowired
    private ScheduledTransactionRepository scheduledRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ScheduledTransactionService scheduledService;

    private User user;
    private Account account;
    private final List<Long> fixtureIds = new ArrayList<>();

    @BeforeAll
    void createUserAndAccount() {
        user = userRepository.findByUsername("uc111").orElseGet(() -> {
            User u = new User();
            u.setUsername("uc111");
            u.setEmail("uc111@test.local");
            u.setPassword("not-used");
            u.setFirstName("UC111");
            u.setLastName("Tester");
            u.setEnabled(true);
            u.setRoles(new HashSet<>(Set.of("ROLE_USER")));
            u.setLocale("de-DE");
            u.setDefaultCurrency("EUR");
            return userRepository.save(u);
        });
        account = accountRepository.findByUserOrderBySortOrderAsc(user).stream().findFirst()
                .orElseGet(() -> {
                    Account a = new Account();
                    a.setUser(user);
                    a.setAccountName("UC111 Konto");
                    a.setAccountNumber("UC111-0001");
                    a.setAccountType(Account.AccountType.CURRENT);
                    return accountRepository.save(a);
                });
    }

    @AfterEach
    void removeFixtures() {
        fixtureIds.forEach(id -> scheduledRepository.findById(id).ifPresent(scheduledRepository::delete));
        fixtureIds.clear();
        transactionRepository.findByUser(user).stream()
                .filter(t -> FIXTURE_MEMO.equals(t.getMemo()))
                .forEach(transactionRepository::delete);
        User fresh = userRepository.findById(user.getId()).orElseThrow();
        fresh.setScheduledBadgeDays(null);
        fresh.setScheduledHorizonDays(null);
        user = userRepository.save(fresh);
    }

    private ScheduledTransaction schedule(LocalDateTime next, Transaction.TransactionType type, String amount) {
        ScheduledTransaction st = scheduledRepository.save(ScheduledTransaction.builder()
                .user(user)
                .type(type)
                .fromAccount(account)
                .toAccount(type == Transaction.TransactionType.TRANSFER ? account : null)
                .amount(new BigDecimal(amount))
                .payee("UC111 Fixture")
                .memo(FIXTURE_MEMO)
                .recurrencePattern(ScheduledTransaction.RecurrencePattern.MONTHLY)
                .recurrenceValue(1)
                .nextOccurrence(next)
                .enabled(true)
                .build());
        fixtureIds.add(st.getId());
        return st;
    }

    private ScheduledTransaction expense(LocalDateTime next) {
        return schedule(next, Transaction.TransactionType.EXPENSE, "42.00");
    }

    private long badgeCount() {
        return $(Span.class).withClassName("nav-badge").exists()
                ? Long.parseLong($(Span.class).withClassName("nav-badge").single().getText())
                : 0;
    }

    @Test
    @UseCase(id = "UC-111", scenario = "Schedules are stored as dates without time of day")
    void nextOccurrence_isTruncatedToDate() {
        ScheduledTransaction st = expense(LocalDate.now().atTime(23, 59));

        LocalDateTime stored = scheduledRepository.findById(st.getId()).orElseThrow().getNextOccurrence();
        assertThat(stored).isEqualTo(LocalDate.now().atStartOfDay());
    }

    @Test
    @UseCase(id = "UC-111", scenario = "Badge and post-all use the same due cutoff")
    void findDue_andPostAllDue_agree() {
        ScheduledTransaction overdue = expense(LocalDateTime.now().minusDays(1));
        ScheduledTransaction today = expense(LocalDate.now().atTime(22, 0));
        expense(LocalDateTime.now().plusDays(1));

        List<Long> dueIds = scheduledService.findDue(user).stream().map(ScheduledTransaction::getId).toList();
        assertThat(dueIds).containsExactlyInAnyOrder(overdue.getId(), today.getId());

        int posted = scheduledService.postAllDue();

        assertThat(posted).isEqualTo(2);
        assertThat(scheduledService.findDue(user)).isEmpty();
    }

    @Test
    @UseCase(id = "UC-111", scenario = "Badge tooltip splits expenses and transfers instead of summing them")
    void badgeTooltip_splitsByType() {
        expense(LocalDateTime.now().minusDays(1));
        schedule(LocalDateTime.now().minusDays(1), Transaction.TransactionType.TRANSFER, "250.00");
        navigate(DashboardView.class);

        String tooltip = $(Span.class).withClassName("nav-badge").single().getElement().getAttribute("title");
        assertThat(tooltip).contains("2 fällig").contains("Ausgaben 42,00").contains("Umbuchungen 250,00")
                .doesNotContain("292");
    }

    @Test
    @UseCase(id = "UC-111", scenario = "Badge look-ahead setting counts upcoming schedules")
    void badgeLookAhead_countsUpcoming() {
        expense(LocalDateTime.now().plusDays(3));
        navigate(DashboardView.class);
        assertThat(badgeCount()).isZero();

        User fresh = userRepository.findById(user.getId()).orElseThrow();
        fresh.setScheduledBadgeDays(7);
        user = userRepository.save(fresh);

        assertThat(scheduledService.findDue(user)).hasSize(1);
    }

    @Test
    @UseCase(id = "UC-111", scenario = "Adjust & post books a different amount and shows up in the history")
    void adjustPost_recordsOverrideAndHistory() {
        ScheduledTransaction st = expense(LocalDateTime.now().minusDays(1));
        LocalDate bookingDate = LocalDate.now().minusDays(2);

        scheduledService.post(st.getId(), new BigDecimal("47.11"), bookingDate);

        List<Transaction> history = scheduledService.getHistory(st.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getAmount()).isEqualByComparingTo("47.11");
        assertThat(history.get(0).getTransactionDate()).isEqualTo(bookingDate.atStartOfDay());
        ScheduledTransaction after = scheduledRepository.findById(st.getId()).orElseThrow();
        assertThat(after.getAmount()).isEqualByComparingTo("42.00");
        assertThat(after.getNextOccurrence()).isAfter(LocalDateTime.now());
    }

    @Test
    @UseCase(id = "UC-111", scenario = "List horizon is remembered per user")
    @SuppressWarnings("unchecked")
    void horizonSelection_isRemembered() {
        navigate(ScheduledTransactionsView.class);
        Select<Integer> horizon = $(Select.class).single();
        test(horizon).selectItem("30 Tage");

        navigate(DashboardView.class);
        navigate(ScheduledTransactionsView.class);

        assertThat($(Select.class).single().getValue()).isEqualTo(30);
    }

    @Test
    @UseCase(id = "UC-111", scenario = "Amount column sits next to the due date in the pending list")
    void pendingGrid_amountBeforeAccountAndPayee() {
        navigate(ScheduledTransactionsView.class);
        Grid<?> pending = $(Grid.class).all().stream()
                .map(g -> (Grid<?>) g)
                .filter(g -> g.getColumnByKey("pending-amount") != null)
                .findFirst().orElseThrow();

        List<String> keys = pending.getColumns().stream().map(Grid.Column::getKey).toList();
        assertThat(keys.indexOf("pending-amount")).isEqualTo(1);
        assertThat(keys.indexOf("pending-amount")).isLessThan(keys.indexOf("pending-payee"));
        assertThat(pending.getColumns().get(pending.getColumns().size() - 1).isFrozenToEnd()).isTrue();
    }

    @Test
    @UseCase(id = "UC-111", scenario = "Midnight refresh notifies every open badge")
    void broadcastAll_notifiesAllUsers() {
        AtomicInteger calls = new AtomicInteger();
        Runnable unregisterA = ScheduledChangeBroadcaster.register(-1001L, calls::incrementAndGet);
        Runnable unregisterB = ScheduledChangeBroadcaster.register(-1002L, calls::incrementAndGet);
        try {
            scheduledService.refreshBadgesAtMidnight();
            assertThat(calls.get()).isEqualTo(2);
        } finally {
            unregisterA.run();
            unregisterB.run();
        }
    }
}
