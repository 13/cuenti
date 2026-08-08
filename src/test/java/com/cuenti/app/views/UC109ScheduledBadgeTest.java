package com.cuenti.app.views;

import com.cuenti.app.model.ScheduledTransaction;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import com.cuenti.app.repository.AccountRepository;
import com.cuenti.app.repository.ScheduledTransactionRepository;
import com.cuenti.app.repository.TransactionRepository;
import com.cuenti.app.service.UserService;
import com.cuenti.app.usecase.UseCase;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
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
 * The scheduled-transactions nav badge must reflect the current due-soon count
 * after posting or skipping a schedule, mark overdue schedules, carry a total-
 * amount tooltip, refresh on navigation, and the due reminder toast must fire
 * again when schedules turn due that the session has not yet announced.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "demo")
class UC109ScheduledBadgeTest extends SpringBrowserlessTest {

    private static final String FIXTURE_MEMO = "UC109-fixture";

    @Autowired
    private ScheduledTransactionRepository scheduledRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private UserService userService;

    private User demo;
    private final List<Long> fixtureIds = new ArrayList<>();

    @BeforeEach
    void createOverdueFixture() {
        demo = userService.findByUsername("demo");
        createDueSchedule(LocalDateTime.now().minusHours(2));
    }

    @AfterEach
    void removeFixtures() {
        fixtureIds.forEach(id -> scheduledRepository.findById(id).ifPresent(scheduledRepository::delete));
        fixtureIds.clear();
        transactionRepository.findByUser(demo).stream()
                .filter(t -> FIXTURE_MEMO.equals(t.getMemo()))
                .forEach(transactionRepository::delete);
    }

    private void createDueSchedule(LocalDateTime nextOccurrence) {
        ScheduledTransaction st = scheduledRepository.save(ScheduledTransaction.builder()
                .user(demo)
                .type(Transaction.TransactionType.EXPENSE)
                .fromAccount(accountRepository.findByUserOrderBySortOrderAsc(demo).get(0))
                .amount(new BigDecimal("42.00"))
                .payee("UC109 Fixture")
                .memo(FIXTURE_MEMO)
                .recurrencePattern(ScheduledTransaction.RecurrencePattern.MONTHLY)
                .recurrenceValue(1)
                .nextOccurrence(nextOccurrence)
                .enabled(true)
                .build());
        fixtureIds.add(st.getId());
    }

    private long badgeCount() {
        return $(Span.class).withClassName("nav-badge").exists()
                ? Long.parseLong($(Span.class).withClassName("nav-badge").single().getText())
                : 0;
    }

    private Span badge() {
        return $(Span.class).withClassName("nav-badge").single();
    }

    private Grid<?> pendingGrid() {
        return $(Grid.class).all().stream()
                .map(g -> (Grid<?>) g)
                .filter(g -> g.getColumns().stream()
                        .anyMatch(c -> "pending-account".equals(c.getKey())))
                .findFirst().orElseThrow();
    }

    /** Click the action button at the given index (0 = Post, 1 = Skip) of the first pending row. */
    private void clickRowAction(int buttonIndex) {
        Grid<?> grid = pendingGrid();
        HorizontalLayout actions = (HorizontalLayout) test(grid)
                .getCellComponent(0, grid.getColumns().size() - 1);
        test((Button) actions.getComponentAt(buttonIndex)).click();
    }

    @Test
    @UseCase(id = "UC-109", scenario = "Posting a due schedule refreshes the nav badge")
    void postingDueSchedule_updatesNavBadge() {
        navigate(ScheduledTransactionsView.class);
        long before = badgeCount();
        assertThat(before).isGreaterThan(0);

        clickRowAction(0);

        assertThat(badgeCount()).isEqualTo(before - 1);
    }

    @Test
    @UseCase(id = "UC-109", scenario = "Skipping a due schedule refreshes the nav badge")
    void skippingDueSchedule_updatesNavBadge() {
        navigate(ScheduledTransactionsView.class);
        long before = badgeCount();
        assertThat(before).isGreaterThan(0);

        clickRowAction(1);

        assertThat(badgeCount()).isEqualTo(before - 1);
    }

    @Test
    @UseCase(id = "UC-109", scenario = "Badge disappears when nothing is due")
    void badgeDisappears_whenNothingDue() {
        navigate(ScheduledTransactionsView.class);
        assertThat(badgeCount()).isGreaterThan(0);

        int guard = 0;
        while ($(Span.class).withClassName("nav-badge").exists() && guard++ < 25) {
            clickRowAction(1);
        }

        assertThat($(Span.class).withClassName("nav-badge").exists()).isFalse();
    }

    @Test
    @UseCase(id = "UC-109", scenario = "Badge marks overdue schedules and carries an amount tooltip")
    void badgeMarksOverdue_andShowsAmountTooltip() {
        navigate(ScheduledTransactionsView.class);

        assertThat(badge().getClassNames()).contains("nav-badge-overdue");
        String tooltip = badge().getElement().getAttribute("title");
        assertThat(tooltip).isNotBlank().containsPattern("\\d");
    }

    @Test
    @UseCase(id = "UC-109", scenario = "Badge refreshes on navigation when schedules changed elsewhere")
    void newDueSchedule_refreshesBadgeOnNavigation() {
        navigate(ScheduledTransactionsView.class);
        long before = badgeCount();

        // repository write bypasses the service broadcast — only navigation can pick it up
        createDueSchedule(LocalDateTime.now().plusDays(1));
        navigate(DashboardView.class);

        assertThat(badgeCount()).isEqualTo(before + 1);
    }

    @Test
    @UseCase(id = "UC-109", scenario = "Skip can be undone from the toast")
    void skipUndo_restoresScheduleAndBadge() {
        navigate(ScheduledTransactionsView.class);
        long before = badgeCount();
        assertThat(before).isGreaterThan(0);

        clickRowAction(1);
        assertThat(badgeCount()).isEqualTo(before - 1);

        // demo user locale is de-DE
        Button undo = $(Button.class).withText("Rückgängig").last();
        test(undo).click();

        assertThat(badgeCount()).isEqualTo(before);
    }

    @Test
    @UseCase(id = "UC-109", scenario = "Due reminder toast repeats for newly due schedules only")
    void dueToast_repeatsForNewSchedules_notForKnown() {
        navigate(ScheduledTransactionsView.class);
        int afterFirstNav = $(Notification.class).all().size();
        assertThat(afterFirstNav).isGreaterThan(0);

        navigate(DashboardView.class);
        assertThat($(Notification.class).all().size()).isEqualTo(afterFirstNav);

        createDueSchedule(LocalDateTime.now().plusDays(2));
        navigate(ScheduledTransactionsView.class);
        assertThat($(Notification.class).all().size()).isEqualTo(afterFirstNav + 1);
    }
}
