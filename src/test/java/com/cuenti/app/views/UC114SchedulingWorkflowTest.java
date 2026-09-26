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
import com.cuenti.app.service.TransactionService;
import com.cuenti.app.usecase.UseCase;
import com.cuenti.app.views.components.TagField;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scheduling workflow: adjust-and-post through the full transaction form, schedules
 * that end after a count or a date, interval-aware repetition, and creating a schedule
 * from an existing transaction.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "uc114")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UC114SchedulingWorkflowTest extends SpringBrowserlessTest {

    private static final String FIXTURE_MEMO = "UC114-fixture";

    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ScheduledTransactionRepository scheduledRepository;
    @Autowired private ScheduledTransactionService scheduledService;
    @Autowired private TransactionService transactionService;

    private User user;
    private Account account;
    private final List<Long> scheduleIds = new ArrayList<>();

    @BeforeAll
    void createUserAndAccount() {
        user = userRepository.findByUsername("uc114").orElseGet(() -> {
            User u = new User();
            u.setUsername("uc114");
            u.setEmail("uc114@test.local");
            u.setPassword("not-used");
            u.setFirstName("UC114");
            u.setLastName("Tester");
            u.setEnabled(true);
            u.setRoles(new HashSet<>(Set.of("ROLE_USER")));
            u.setLocale("de-DE");
            u.setDefaultCurrency("EUR");
            return userRepository.save(u);
        });
        Account a = new Account();
        a.setUser(user);
        a.setAccountName("UC114 Konto");
        a.setAccountNumber("UC114-0001");
        a.setAccountType(Account.AccountType.CURRENT);
        account = accountRepository.save(a);
    }

    @AfterEach
    void removeFixtures() {
        transactionRepository.findByUser(user).stream()
                .filter(t -> FIXTURE_MEMO.equals(t.getMemo()))
                .forEach(transactionService::deleteTransaction);
        scheduledRepository.findByUser(user).forEach(scheduledRepository::delete);
        scheduleIds.clear();
    }

    private ScheduledTransaction schedule(ScheduledTransaction.RecurrencePattern pattern, int value) {
        ScheduledTransaction st = scheduledRepository.save(ScheduledTransaction.builder()
                .user(user).type(Transaction.TransactionType.EXPENSE).fromAccount(account)
                .amount(new BigDecimal("50.00")).payee("UC114 Strom").memo(FIXTURE_MEMO).tags("Fix")
                .recurrencePattern(pattern).recurrenceValue(value)
                .nextOccurrence(LocalDate.now().atStartOfDay()).enabled(true).build());
        scheduleIds.add(st.getId());
        return st;
    }

    @Test
    @UseCase(id = "UC-114", scenario = "Adjust & post books an edited occurrence and advances the schedule")
    void adjustPost_opensTransactionForm_andPostsEditedValues() {
        ScheduledTransaction st = schedule(ScheduledTransaction.RecurrencePattern.MONTHLY, 1);

        ScheduledTransactionsView view = navigate(ScheduledTransactionsView.class);
        view.openAdjustPostDialog(st);

        TagField tags = $(TagField.class).withId("tx-tags").single();
        assertThat(tags.getValue()).isEqualTo("Fix");
        assertThat($(Button.class).withText("Hinzufügen & Weiter").exists()).isFalse();
        tags.addTags(List.of("Nachzahlung"));
        BigDecimalField amount = $(BigDecimalField.class).all().stream()
                .filter(f -> f.getValue() != null && f.getValue().compareTo(new BigDecimal("50.00")) == 0)
                .findFirst().orElseThrow();
        test(amount).setValue(new BigDecimal("63.20"));
        test($(Button.class).withText("Buchen").single()).click();

        List<Transaction> history = scheduledService.getHistory(st.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getAmount()).isEqualByComparingTo("63.20");
        assertThat(history.get(0).getTags()).isEqualTo("Fix,Nachzahlung");
        assertThat(history.get(0).getFromAccount().getId()).isEqualTo(account.getId());
        ScheduledTransaction after = scheduledRepository.findById(st.getId()).orElseThrow();
        assertThat(after.getAmount()).isEqualByComparingTo("50.00");
        assertThat(after.getNextOccurrence().toLocalDate()).isEqualTo(LocalDate.now().plusMonths(1));
    }

    @Test
    @UseCase(id = "UC-114", scenario = "A schedule with a count switches off after its last booking")
    void remainingCount_disablesAfterLastPost() {
        ScheduledTransaction st = schedule(ScheduledTransaction.RecurrencePattern.MONTHLY, 1);
        st.setRemainingOccurrences(2);
        scheduledRepository.save(st);

        scheduledService.post(st.getId());
        assertThat(scheduledRepository.findById(st.getId()).orElseThrow().isEnabled()).isTrue();
        scheduledService.skip(st.getId());

        ScheduledTransaction after = scheduledRepository.findById(st.getId()).orElseThrow();
        assertThat(after.getRemainingOccurrences()).isZero();
        assertThat(after.isEnabled()).isFalse();
    }

    @Test
    @UseCase(id = "UC-114", scenario = "A schedule with an end date switches off once the next date passes it")
    void endDate_disablesWhenPassed() {
        ScheduledTransaction st = schedule(ScheduledTransaction.RecurrencePattern.MONTHLY, 1);
        st.setEndDate(LocalDate.now().plusDays(10));
        scheduledRepository.save(st);

        scheduledService.post(st.getId());

        assertThat(scheduledRepository.findById(st.getId()).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    @UseCase(id = "UC-114", scenario = "Monthly-last-day honours the interval")
    void monthlyLastDay_usesInterval() {
        ScheduledTransaction probe = new ScheduledTransaction();
        probe.setRecurrencePattern(ScheduledTransaction.RecurrencePattern.MONTHLY_LAST_DAY);
        probe.setRecurrenceValue(3);

        LocalDateTime next = ScheduledTransactionService.advanceOccurrence(LocalDate.of(2026, 1, 31).atStartOfDay(), probe);

        assertThat(next.toLocalDate()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    @UseCase(id = "UC-114", scenario = "Dialog: interval hidden for every weekday, end after count is saved")
    @SuppressWarnings("unchecked")
    void scheduledDialog_intervalAndEndCount() {
        ScheduledTransaction st = schedule(ScheduledTransaction.RecurrencePattern.MONTHLY, 1);

        ScheduledTransactionsView view = navigate(ScheduledTransactionsView.class);
        view.openEditDialog(st);

        assertThat($(IntegerField.class).withId("st-interval").single().isVisible()).isTrue();
        ComboBox<ScheduledTransaction.RecurrencePattern> pattern = $(ComboBox.class).withId("st-pattern").single();
        assertThat(pattern.getListDataView().getItems().toList())
                .doesNotContain(ScheduledTransaction.RecurrencePattern.BI_WEEKLY, ScheduledTransaction.RecurrencePattern.EVERY_FRIDAY);
        test(pattern).selectItem("Jeden Werktag");
        assertThat($(IntegerField.class).withId("st-interval").exists()).isFalse();
        test(pattern).selectItem("Monatlich");

        Select<Object> endMode = $(Select.class).withId("st-end-mode").single();
        test(endMode).selectItem("Nach Anzahl");
        test($(IntegerField.class).withId("st-remaining").single()).setValue(2);
        assertThat($(Span.class).withId("st-preview").single().getText()).contains("·");

        test($(Button.class).withId("st-save").single()).click();

        ScheduledTransaction stored = scheduledRepository.findById(st.getId()).orElseThrow();
        assertThat(stored.getRemainingOccurrences()).isEqualTo(2);
        assertThat(stored.getEndDate()).isNull();
    }

    @Test
    @UseCase(id = "UC-114", scenario = "End date before the next date is refused")
    @SuppressWarnings("unchecked")
    void scheduledDialog_endDateBeforeNext_isInvalid() {
        ScheduledTransaction st = schedule(ScheduledTransaction.RecurrencePattern.MONTHLY, 1);
        ScheduledTransactionsView view = navigate(ScheduledTransactionsView.class);
        view.openEditDialog(st);

        test($(Select.class).withId("st-end-mode").single()).selectItem("Am Datum");
        test($(DatePicker.class).withId("st-end-date").single()).setValue(LocalDate.now().minusDays(1));
        test($(Button.class).withId("st-save").single()).click();

        assertThat($(DatePicker.class).withId("st-end-date").single().isInvalid()).isTrue();
        assertThat(scheduledRepository.findById(st.getId()).orElseThrow().getEndDate()).isNull();
    }

    @Test
    @UseCase(id = "UC-114", scenario = "An existing transaction can be saved as a schedule")
    void transaction_saveAsSchedule() {
        Transaction t = new Transaction();
        t.setType(Transaction.TransactionType.INCOME);
        t.setToAccount(account);
        t.setAmount(new BigDecimal("2500.00"));
        t.setTransactionDate(LocalDate.now().atStartOfDay());
        t.setPayee("UC114 Arbeitgeber");
        t.setMemo(FIXTURE_MEMO);
        t.setTags("Gehalt");
        Transaction saved = transactionService.saveTransaction(t);

        TransactionHistoryView view = navigate(TransactionHistoryView.class);
        view.openTransactionDialog(saved);
        test($(Button.class).withId("tx-save-as-schedule").single()).click();

        assertThat($(ComboBox.class).withId("st-account").single().getValue()).isEqualTo(account);
        test($(Button.class).withId("st-save").single()).click();

        List<ScheduledTransaction> schedules = scheduledRepository.findByUser(user);
        assertThat(schedules).hasSize(1);
        ScheduledTransaction created = schedules.get(0);
        assertThat(created.getType()).isEqualTo(Transaction.TransactionType.INCOME);
        assertThat(created.getToAccount().getId()).isEqualTo(account.getId());
        assertThat(created.getAmount()).isEqualByComparingTo("2500.00");
        assertThat(created.getTags()).isEqualTo("Gehalt");
        assertThat(created.getNextOccurrence().toLocalDate()).isEqualTo(LocalDate.now().plusMonths(1));
    }

    @Test
    @UseCase(id = "UC-114", scenario = "New bookings are placed last on their day")
    void nextSortOrder_placesAfterExisting() {
        Transaction t = new Transaction();
        t.setType(Transaction.TransactionType.EXPENSE);
        t.setFromAccount(account);
        t.setAmount(BigDecimal.ONE);
        t.setTransactionDate(LocalDate.now().atStartOfDay());
        t.setMemo(FIXTURE_MEMO);
        t.setSortOrder(40);
        transactionService.saveTransaction(t);

        assertThat(transactionService.nextSortOrder(account, LocalDate.now())).isEqualTo(50);
        assertThat(transactionService.nextSortOrder(account, LocalDate.now().minusDays(5))).isEqualTo(10);
    }

    @Test
    @UseCase(id = "UC-114", scenario = "Tag chips carry their colour class")
    void tagChips_haveColourClass() {
        String cls = TagField.chipClassFor("Urlaub");
        assertThat(cls).matches("tagc-[0-9A-F]{6}-[ld]");
        assertThat(TagField.chipClassFor("urlaub")).isEqualTo(cls);
    }
}
