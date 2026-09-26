package com.cuenti.app.views;

import com.cuenti.app.model.Account;
import com.cuenti.app.model.Payee;
import com.cuenti.app.model.ScheduledTransaction;
import com.cuenti.app.model.Tag;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import com.cuenti.app.repository.AccountRepository;
import com.cuenti.app.repository.ScheduledTransactionRepository;
import com.cuenti.app.repository.TagRepository;
import com.cuenti.app.repository.TransactionRepository;
import com.cuenti.app.repository.UserRepository;
import com.cuenti.app.service.PayeeService;
import com.cuenti.app.service.ScheduledTransactionService;
import com.cuenti.app.service.TagService;
import com.cuenti.app.service.TransactionService;
import com.cuenti.app.usecase.UseCase;
import com.cuenti.app.util.TagNames;
import com.cuenti.app.views.components.TagField;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Span;
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
 * Posting an income schedule books into the right account (and its balance) without
 * duplicate tags; the scheduled dialog mirrors the transaction dialog with a single
 * account field; the shared tag field reuses tags ignoring case and merges defaults.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "uc112")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UC112ScheduledFormAndTagsTest extends SpringBrowserlessTest {

    private static final String FIXTURE_MEMO = "UC112-fixture";

    @Autowired private ScheduledTransactionRepository scheduledRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TagRepository tagRepository;
    @Autowired private ScheduledTransactionService scheduledService;
    @Autowired private TransactionService transactionService;
    @Autowired private TagService tagService;
    @Autowired private PayeeService payeeService;

    private User user;
    private Account account;
    private final List<Long> fixtureIds = new ArrayList<>();

    @BeforeAll
    void createUserAndAccount() {
        user = userRepository.findByUsername("uc112").orElseGet(() -> {
            User u = new User();
            u.setUsername("uc112");
            u.setEmail("uc112@test.local");
            u.setPassword("not-used");
            u.setFirstName("UC112");
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
                    a.setAccountName("UC112 Konto");
                    a.setAccountNumber("UC112-0001");
                    a.setAccountType(Account.AccountType.CURRENT);
                    a.setBalance(BigDecimal.ZERO);
                    a.setStartBalance(BigDecimal.ZERO);
                    return accountRepository.save(a);
                });
    }

    @AfterEach
    void removeFixtures() {
        transactionRepository.findByUser(user).stream()
                .filter(t -> FIXTURE_MEMO.equals(t.getMemo()))
                .forEach(transactionService::deleteTransaction);
        fixtureIds.forEach(id -> scheduledRepository.findById(id).ifPresent(scheduledRepository::delete));
        fixtureIds.clear();
    }

    private ScheduledTransaction scheduleRaw(Transaction.TransactionType type, Account from, Account to, String tags) {
        ScheduledTransaction st = scheduledRepository.save(ScheduledTransaction.builder()
                .user(user)
                .type(type)
                .fromAccount(from)
                .toAccount(to)
                .amount(new BigDecimal("1000.00"))
                .payee("UC112 Arbeitgeber")
                .memo(FIXTURE_MEMO)
                .tags(tags)
                .recurrencePattern(ScheduledTransaction.RecurrencePattern.MONTHLY)
                .recurrenceValue(1)
                .nextOccurrence(LocalDate.now().atStartOfDay())
                .enabled(true)
                .build());
        fixtureIds.add(st.getId());
        return st;
    }

    private BigDecimal balance() {
        return accountRepository.findById(account.getId()).orElseThrow().getBalance();
    }

    @Test
    @UseCase(id = "UC-112", scenario = "Posting a legacy income schedule books into the account and credits it")
    void postIncome_withAccountStoredAsFrom_creditsToAccount() {
        ScheduledTransaction st = scheduleRaw(Transaction.TransactionType.INCOME, account, null, "Gehalt");
        BigDecimal before = balance();

        scheduledService.post(st.getId());

        Transaction posted = scheduledService.getHistory(st.getId()).get(0);
        assertThat(posted.getToAccount()).isNotNull();
        assertThat(posted.getToAccount().getId()).isEqualTo(account.getId());
        assertThat(posted.getFromAccount()).isNull();
        assertThat(balance()).isEqualByComparingTo(before.add(new BigDecimal("1000.00")));
    }

    @Test
    @UseCase(id = "UC-112", scenario = "Posting never duplicates tags")
    void post_normalizesDuplicateTags() {
        ScheduledTransaction st = scheduleRaw(Transaction.TransactionType.INCOME, null, account,
                "Gehalt, gehalt,Fix,,Gehalt");

        scheduledService.post(st.getId());

        assertThat(scheduledService.getHistory(st.getId()).get(0).getTags()).isEqualTo("Gehalt,Fix");
    }

    @Test
    @UseCase(id = "UC-112", scenario = "Saving an income schedule stores its account on the income side")
    void saveIncomeSchedule_movesAccountToIncomeSide() {
        ScheduledTransaction st = scheduleRaw(Transaction.TransactionType.INCOME, account, null, null);

        scheduledService.save(st);

        ScheduledTransaction stored = scheduledRepository.findById(st.getId()).orElseThrow();
        assertThat(stored.getFromAccount()).isNull();
        assertThat(stored.getToAccount().getId()).isEqualTo(account.getId());
    }

    @Test
    @UseCase(id = "UC-112", scenario = "Posted income opens with its account filled in")
    @SuppressWarnings("unchecked")
    void postedIncome_showsAccountInTransactionDialog() {
        ScheduledTransaction st = scheduleRaw(Transaction.TransactionType.INCOME, account, null, "Gehalt");
        scheduledService.post(st.getId());
        Transaction posted = scheduledService.getHistory(st.getId()).get(0);

        TransactionHistoryView view = navigate(TransactionHistoryView.class);
        view.openTransactionDialog(posted);

        ComboBox<Account> accountCombo = $(ComboBox.class).all().stream()
                .map(c -> (ComboBox<Account>) c)
                .filter(c -> "Konto".equals(c.getLabel()))
                .findFirst().orElseThrow();
        assertThat(accountCombo.getValue()).isNotNull();
        assertThat(accountCombo.getValue().getId()).isEqualTo(account.getId());
        assertThat($(TagField.class).withId("tx-tags").single().getValue()).isEqualTo("Gehalt");
    }

    @Test
    @UseCase(id = "UC-112", scenario = "Scheduled dialog shows one account field and keeps income on the income side")
    @SuppressWarnings("unchecked")
    void scheduledDialog_singleAccountField_forIncome() {
        ScheduledTransaction st = scheduleRaw(Transaction.TransactionType.INCOME, null, account, "Gehalt,gehalt");

        ScheduledTransactionsView view = navigate(ScheduledTransactionsView.class);
        view.openEditDialog(st);

        ComboBox<Account> accountField = $(ComboBox.class).withId("st-account").single();
        assertThat(accountField.getLabel()).isEqualTo("Konto");
        assertThat(accountField.getValue().getId()).isEqualTo(account.getId());
        assertThat($(ComboBox.class).withId("st-to-account").exists()).isFalse();
        assertThat($(TagField.class).withId("st-tags").single().getValue()).isEqualTo("Gehalt");
        assertThat($(Span.class).withId("st-preview").single().getText()).startsWith("Nächste Termine:");

        test($(Button.class).withId("st-save").single()).click();

        ScheduledTransaction stored = scheduledRepository.findById(st.getId()).orElseThrow();
        assertThat(stored.getToAccount().getId()).isEqualTo(account.getId());
        assertThat(stored.getFromAccount()).isNull();
        assertThat(stored.getTags()).isEqualTo("Gehalt");
    }

    @Test
    @UseCase(id = "UC-112", scenario = "Cancelling the scheduled dialog leaves the schedule unchanged")
    void scheduledDialog_cancelDoesNotMutate() {
        ScheduledTransaction st = scheduleRaw(Transaction.TransactionType.EXPENSE, account, null, null);

        ScheduledTransactionsView view = navigate(ScheduledTransactionsView.class);
        view.openEditDialog(st);
        test($(com.vaadin.flow.component.textfield.BigDecimalField.class).withId("st-amount").single())
                .setValue(new BigDecimal("5.00"));

        assertThat(st.getAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    @UseCase(id = "UC-112", scenario = "Tag field reuses existing tags ignoring case")
    void tagField_reusesExistingTagIgnoringCase() {
        tagService.findOrCreate("Urlaub");
        long before = tagRepository.findByUser(user).size();

        TagField field = new TagField(tagService, "Tags");
        field.addTags(List.of("urlaub", " URLAUB ", "Reise"));

        assertThat(field.getValue()).isEqualTo("Urlaub,Reise");
        assertThat(tagRepository.findByUser(user)).hasSize((int) before + 1);
        assertThat(tagRepository.findByUser(user).stream().map(Tag::getName)
                .filter(n -> TagNames.same(n, "urlaub"))).hasSize(1);
    }

    @Test
    @UseCase(id = "UC-112", scenario = "Tag suggestions come from earlier transactions with the payee")
    void suggestions_fromPayeeHistory() {
        Transaction t = new Transaction();
        t.setType(Transaction.TransactionType.EXPENSE);
        t.setFromAccount(account);
        t.setAmount(new BigDecimal("9.99"));
        t.setTransactionDate(LocalDateTime.now().minusDays(1));
        t.setPayee("UC112 Streaming");
        t.setMemo(FIXTURE_MEMO);
        t.setTags("Abo,Freizeit");
        transactionService.saveTransaction(t);

        assertThat(tagService.suggestTagNames("uc112 streaming", 5)).containsExactlyInAnyOrder("Abo", "Freizeit");
    }

    @Test
    @UseCase(id = "UC-112", scenario = "Payee default tags are added to the selection, not replacing it")
    @SuppressWarnings("unchecked")
    void payeeDefaults_mergeIntoSelectedTags() {
        Payee payee = payeeService.savePayee(Payee.builder().name("UC112 Bäcker").defaultTags("Essen").build());

        navigate(TransactionHistoryView.class);
        fireShortcut(com.vaadin.flow.component.Key.KEY_N, com.vaadin.flow.component.KeyModifier.ALT);
        TagField tags = $(TagField.class).withId("tx-tags").single();
        tags.addTags(List.of("Bar"));

        ComboBox<String> payeeCombo = $(ComboBox.class).all().stream()
                .map(c -> (ComboBox<String>) c)
                .filter(c -> "Empfänger".equals(c.getLabel()))
                .findFirst().orElseThrow();
        test(payeeCombo).selectItem(payee.getName());

        assertThat(TagNames.parse(tags.getValue())).containsExactlyInAnyOrder("Bar", "Essen");
    }

    @Test
    @UseCase(id = "UC-112", scenario = "Tag names are trimmed and de-duplicated ignoring case")
    void tagNames_normalize() {
        assertThat(TagNames.normalize(" A, a ,B,, b ,C")).isEqualTo("A,B,C");
        assertThat(TagNames.normalize(" , ")).isNull();
        assertThat(TagNames.normalize(null)).isNull();
    }
}
