package com.cuenti.homebanking;

import com.cuenti.homebanking.data.*;
import com.cuenti.homebanking.services.AccountService;
import com.cuenti.homebanking.services.GlobalSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final CurrencyRepository currencyRepository;
    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final GlobalSettingService globalSettingService;
    private final PasswordEncoder passwordEncoder;
    private final Random random = new Random();

    public DataInitializer(UserRepository userRepository,
                           CategoryRepository categoryRepository,
                           CurrencyRepository currencyRepository,
                           AccountService accountService,
                           AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           GlobalSettingService globalSettingService,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.currencyRepository = currencyRepository;
        this.accountService = accountService;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.globalSettingService = globalSettingService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Skip demo data initialization in production
        if ("production".equals(activeProfile)) {
            log.info("Production mode detected - skipping demo data initialization");
            return;
        }

        if (userRepository.count() > 0) {
            log.info("Data already initialized, skipping...");
            return;
        }

        log.info("Initializing demo data...");

        // Enable registration
        globalSettingService.setRegistrationEnabled(true);

        // Create demo user
        User demoUser = new User();
        demoUser.setUsername("demo");
        demoUser.setEmail("demo@cuenti.de");
        demoUser.setPassword(passwordEncoder.encode("demo123"));
        demoUser.setFirstName("Max");
        demoUser.setLastName("Mustermann");
        demoUser.setEnabled(true);
        demoUser.setRoles(new HashSet<>(Collections.singletonList("ROLE_USER")));
        demoUser.setLocale("de-DE");
        demoUser.setDarkMode(true);
        demoUser.setDefaultCurrency("EUR");
        demoUser.getRoles().add("ROLE_ADMIN");
        demoUser = userRepository.save(demoUser);

        // Create currencies
        createCurrency(demoUser, "EUR", "Euro", "€", ",", ".", 2);
        createCurrency(demoUser, "USD", "US Dollar", "$", ".", ",", 2);

        // Create categories
        Category housing = createCategory(demoUser, "Housing", Category.CategoryType.EXPENSE, null);
        Category rent = createCategory(demoUser, "Rent", Category.CategoryType.EXPENSE, housing);
        Category food = createCategory(demoUser, "Food", Category.CategoryType.EXPENSE, null);
        Category groceries = createCategory(demoUser, "Groceries", Category.CategoryType.EXPENSE, food);
        Category income = createCategory(demoUser, "Income", Category.CategoryType.INCOME, null);
        Category salary = createCategory(demoUser, "Salary", Category.CategoryType.INCOME, income);

        // Create accounts
        Account checking = new Account();
        checking.setUser(demoUser);
        checking.setAccountName("Checking Account");
        checking.setAccountNumber("DE" + String.format("%018d", Math.abs(random.nextLong() % 1000000000000000000L)));
        checking.setAccountType(Account.AccountType.CURRENT);
        checking.setCurrency("EUR");
        checking.setStartBalance(new BigDecimal("5000.00"));
        checking.setBalance(new BigDecimal("5000.00"));
        checking = accountRepository.save(checking);

        Account savings = new Account();
        savings.setUser(demoUser);
        savings.setAccountName("Savings Account");
        savings.setAccountNumber("DE" + String.format("%018d", Math.abs(random.nextLong() % 1000000000000000000L)));
        savings.setAccountType(Account.AccountType.SAVINGS);
        savings.setCurrency("EUR");
        savings.setStartBalance(new BigDecimal("10000.00"));
        savings.setBalance(new BigDecimal("10000.00"));
        savings = accountRepository.save(savings);

        // Create sample transactions
        createTransaction(checking, null, Transaction.TransactionType.EXPENSE,
                new BigDecimal("45.50"), "Supermarket", groceries);
        createTransaction(null, checking, Transaction.TransactionType.INCOME,
                new BigDecimal("3500.00"), "Employer", salary);
        createTransaction(checking, null, Transaction.TransactionType.EXPENSE,
                new BigDecimal("850.00"), "Landlord", rent);

        log.info("Demo data initialized successfully!");
        log.info("Demo credentials: username=demo, password=demo123");
    }

    private Currency createCurrency(User user, String code, String name, String symbol,
                                     String decimalChar, String groupingChar, int fracDigits) {
        Currency currency = new Currency();
        currency.setUser(user);
        currency.setCode(code);
        currency.setName(name);
        currency.setSymbol(symbol);
        currency.setDecimalChar(decimalChar);
        currency.setGroupingChar(groupingChar);
        currency.setFracDigits(fracDigits);
        return currencyRepository.save(currency);
    }

    private Category createCategory(User user, String name, Category.CategoryType type, Category parent) {
        Category category = new Category();
        category.setUser(user);
        category.setName(name);
        category.setType(type);
        category.setParent(parent);
        return categoryRepository.save(category);
    }

    private Transaction createTransaction(Account from, Account to, Transaction.TransactionType type,
                                          BigDecimal amount, String payee, Category category) {
        Transaction tx = new Transaction();
        tx.setFromAccount(from);
        tx.setToAccount(to);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setPayee(payee);
        tx.setCategory(category);
        tx.setTransactionDate(LocalDateTime.now().minusDays(random.nextInt(30)));
        tx.setStatus(Transaction.TransactionStatus.COMPLETED);

        // Update account balances
        if (type == Transaction.TransactionType.EXPENSE && from != null) {
            from.setBalance(from.getBalance().subtract(amount));
            accountRepository.save(from);
        } else if (type == Transaction.TransactionType.INCOME && to != null) {
            to.setBalance(to.getBalance().add(amount));
            accountRepository.save(to);
        }

        return transactionRepository.save(tx);
    }
}
