package com.cuenti.app.views;

import com.cuenti.app.model.Account;
import com.cuenti.app.model.Payee;
import com.cuenti.app.model.ScheduledTransaction;
import com.cuenti.app.model.Tag;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import com.cuenti.app.repository.AccountRepository;
import com.cuenti.app.repository.PayeeRepository;
import com.cuenti.app.repository.ScheduledTransactionRepository;
import com.cuenti.app.repository.TagRepository;
import com.cuenti.app.repository.TransactionRepository;
import com.cuenti.app.repository.UserRepository;
import com.cuenti.app.service.AccountService;
import com.cuenti.app.service.PayeeService;
import com.cuenti.app.service.TagService;
import com.cuenti.app.service.TransactionService;
import com.cuenti.app.usecase.UseCase;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Data integrity: transactions always carry the account their type books on; renaming,
 * merging and deleting tags rewrites every stored use (with undo); balances can be
 * checked against the bookings and recalculated.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "uc113")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UC113DataIntegrityTest extends SpringBrowserlessTest {

    private static final String FIXTURE_MEMO = "UC113-fixture";

    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ScheduledTransactionRepository scheduledRepository;
    @Autowired private PayeeRepository payeeRepository;
    @Autowired private TagRepository tagRepository;
    @Autowired private TransactionService transactionService;
    @Autowired private TagService tagService;
    @Autowired private PayeeService payeeService;
    @Autowired private AccountService accountService;

    private User user;
    private Account account;
    private final List<Long> scheduleIds = new ArrayList<>();
    private final List<Long> payeeIds = new ArrayList<>();

    @BeforeAll
    void createUserAndAccount() {
        user = userRepository.findByUsername("uc113").orElseGet(() -> {
            User u = new User();
            u.setUsername("uc113");
            u.setEmail("uc113@test.local");
            u.setPassword("not-used");
            u.setFirstName("UC113");
            u.setLastName("Tester");
            u.setEnabled(true);
            u.setRoles(new HashSet<>(Set.of("ROLE_USER")));
            u.setLocale("de-DE");
            u.setDefaultCurrency("EUR");
            return userRepository.save(u);
        });
        Account a = new Account();
        a.setUser(user);
        a.setAccountName("UC113 Konto");
        a.setAccountNumber("UC113-0001");
        a.setAccountType(Account.AccountType.CURRENT);
        a.setStartBalance(new BigDecimal("100.00"));
        a.setBalance(new BigDecimal("100.00"));
        account = accountRepository.save(a);
    }

    @AfterEach
    void removeFixtures() {
        transactionRepository.findByUser(user).stream()
                .filter(t -> FIXTURE_MEMO.equals(t.getMemo()))
                .forEach(transactionService::deleteTransaction);
        scheduleIds.forEach(id -> scheduledRepository.findById(id).ifPresent(scheduledRepository::delete));
        scheduleIds.clear();
        payeeIds.forEach(id -> payeeRepository.findById(id).ifPresent(payeeRepository::delete));
        payeeIds.clear();
        tagRepository.findByUser(user).forEach(tagRepository::delete);
        Account fresh = accountRepository.findById(account.getId()).orElseThrow();
        fresh.setBalance(new BigDecimal("100.00"));
        account = accountRepository.save(fresh);
    }

    private Transaction book(Transaction.TransactionType type, Account from, Account to, String amount, String tags) {
        Transaction t = new Transaction();
        t.setType(type);
        t.setFromAccount(from);
        t.setToAccount(to);
        t.setAmount(new BigDecimal(amount));
        t.setTransactionDate(LocalDateTime.now().minusDays(1));
        t.setMemo(FIXTURE_MEMO);
        t.setTags(tags);
        return transactionService.saveTransaction(t);
    }

    private ScheduledTransaction schedule(String tags) {
        ScheduledTransaction st = scheduledRepository.save(ScheduledTransaction.builder()
                .user(user).type(Transaction.TransactionType.EXPENSE).fromAccount(account)
                .amount(BigDecimal.TEN).payee("UC113").memo(FIXTURE_MEMO).tags(tags)
                .recurrencePattern(ScheduledTransaction.RecurrencePattern.MONTHLY).recurrenceValue(1)
                .nextOccurrence(LocalDateTime.now().plusDays(3)).enabled(true).build());
        scheduleIds.add(st.getId());
        return st;
    }

    private Payee payee(String defaultTags) {
        Payee p = payeeService.savePayee(Payee.builder().name("UC113 Payee " + System.nanoTime())
                .defaultTags(defaultTags).build());
        payeeIds.add(p.getId());
        return p;
    }

    private BigDecimal balance() {
        return accountRepository.findById(account.getId()).orElseThrow().getBalance();
    }

    @Test
    @UseCase(id = "UC-113", scenario = "Income saved with its account on the wrong side is booked into it")
    void income_withSourceOnly_movesToTargetAndCredits() {
        Transaction saved = book(Transaction.TransactionType.INCOME, account, null, "40.00", null);

        assertThat(saved.getToAccount().getId()).isEqualTo(account.getId());
        assertThat(saved.getFromAccount()).isNull();
        assertThat(balance()).isEqualByComparingTo("140.00");
    }

    @Test
    @UseCase(id = "UC-113", scenario = "A transaction without any account is refused")
    void transaction_withoutAccount_isRefused() {
        assertThatThrownBy(() -> book(Transaction.TransactionType.EXPENSE, null, null, "1.00", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @UseCase(id = "UC-113", scenario = "Renaming a tag rewrites transactions, schedules and payee defaults")
    void renameTag_rewritesAllUses() {
        Tag tag = tagService.findOrCreate("Urlaub");
        Transaction t = book(Transaction.TransactionType.EXPENSE, account, null, "5.00", "Urlaub,Reise");
        ScheduledTransaction st = schedule("urlaub");
        Payee p = payee("Reise,Urlaub");

        tagService.rename(tag, "Ferien");

        assertThat(transactionRepository.findById(t.getId()).orElseThrow().getTags()).isEqualTo("Ferien,Reise");
        assertThat(scheduledRepository.findById(st.getId()).orElseThrow().getTags()).isEqualTo("Ferien");
        assertThat(payeeRepository.findById(p.getId()).orElseThrow().getDefaultTags()).isEqualTo("Reise,Ferien");
        assertThat(tagService.getAllTagNames()).contains("Ferien").doesNotContain("Urlaub");
    }

    @Test
    @UseCase(id = "UC-113", scenario = "Renaming onto an existing tag merges both")
    void renameOntoExisting_merges() {
        Tag urlaub = tagService.findOrCreate("Urlaub");
        tagService.findOrCreate("Reise");
        Transaction t = book(Transaction.TransactionType.EXPENSE, account, null, "5.00", "Urlaub,Reise");

        Tag result = tagService.rename(urlaub, "reise");

        assertThat(result.getName()).isEqualTo("Reise");
        assertThat(transactionRepository.findById(t.getId()).orElseThrow().getTags()).isEqualTo("Reise");
        assertThat(tagRepository.findByUser(user)).extracting(Tag::getName).containsExactly("Reise");
    }

    @Test
    @UseCase(id = "UC-113", scenario = "Deleting a tag removes it everywhere; undo restores it")
    void deleteTag_removesEverywhere_undoRestores() {
        Tag tag = tagService.findOrCreate("Abo");
        Transaction t = book(Transaction.TransactionType.EXPENSE, account, null, "5.00", "Abo,Fix");
        ScheduledTransaction st = schedule("Abo");

        TagService.TagUsage usage = tagService.usage("Abo");
        assertThat(usage.transactions()).isEqualTo(1);
        assertThat(usage.schedules()).isEqualTo(1);

        TagService.TagRemoval removal = tagService.deleteEverywhere(tag);
        assertThat(transactionRepository.findById(t.getId()).orElseThrow().getTags()).isEqualTo("Fix");
        assertThat(scheduledRepository.findById(st.getId()).orElseThrow().getTags()).isNull();

        tagService.restore(removal);
        assertThat(transactionRepository.findById(t.getId()).orElseThrow().getTags()).isEqualTo("Abo,Fix");
        assertThat(scheduledRepository.findById(st.getId()).orElseThrow().getTags()).isEqualTo("Abo");
        assertThat(tagRepository.findByUser(user)).extracting(Tag::getName).contains("Abo");
    }

    @Test
    @UseCase(id = "UC-113", scenario = "Balance check finds drift and recalculates")
    void balanceCheck_detectsDrift_andRecalculates() {
        book(Transaction.TransactionType.EXPENSE, account, null, "30.00", null);
        book(Transaction.TransactionType.INCOME, null, account, "10.00", null);
        assertThat(accountService.checkBalance(account).consistent()).isTrue();

        Account drifted = accountRepository.findById(account.getId()).orElseThrow();
        drifted.setBalance(new BigDecimal("999.00"));
        accountRepository.save(drifted);

        AccountService.BalanceCheck check = accountService.checkBalance(account);
        assertThat(check.computed()).isEqualByComparingTo("80.00");
        assertThat(check.difference()).isEqualByComparingTo("919.00");

        AccountManagementView view = navigate(AccountManagementView.class);
        view.openBalanceCheck(account);
        assertThat($(Span.class).withId("balance-verdict").single().getText()).contains("Abweichung");
        test($(Button.class).withId("balance-recalculate").single()).click();

        assertThat(balance()).isEqualByComparingTo("80.00");
    }
}
