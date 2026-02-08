package com.cuenti.homebanking;

import com.cuenti.homebanking.model.*;
import com.cuenti.homebanking.repository.*;
import com.cuenti.homebanking.service.AccountService;
import com.cuenti.homebanking.service.AssetService;
import com.cuenti.homebanking.service.GlobalSettingService;
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
    private final PayeeRepository payeeRepository;
    private final TagRepository tagRepository;
    private final CurrencyRepository currencyRepository;
    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final AssetRepository assetRepository;
    private final AssetService assetService;
    private final ScheduledTransactionRepository scheduledTransactionRepository;
    private final GlobalSettingService globalSettingService;
    private final PasswordEncoder passwordEncoder;
    private final Random random = new Random();

    public DataInitializer(UserRepository userRepository,
                           CategoryRepository categoryRepository,
                           PayeeRepository payeeRepository,
                           TagRepository tagRepository,
                           CurrencyRepository currencyRepository,
                           AccountService accountService,
                           TransactionRepository transactionRepository,
                           AssetRepository assetRepository,
                           AssetService assetService,
                           ScheduledTransactionRepository scheduledTransactionRepository,
                           GlobalSettingService globalSettingService,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.payeeRepository = payeeRepository;
        this.tagRepository = tagRepository;
        this.currencyRepository = currencyRepository;
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
        this.assetRepository = assetRepository;
        this.assetService = assetService;
        this.scheduledTransactionRepository = scheduledTransactionRepository;
        this.globalSettingService = globalSettingService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Skip demo data initialization in production
        if ("production".equals(activeProfile)) {
            log.info("Production mode detected - skipping demo data initialization");
            log.info("To create users, use the registration page or create them manually");

            // Only initialize essential reference data in production
            initializeEssentialData();
            return;
        }

        if (userRepository.count() > 0) {
            log.info("Data already initialized, skipping...");
            return;
        }

        log.info("Initializing comprehensive German demo data...");

        // 1. Global Settings
        globalSettingService.setRegistrationEnabled(true);

        // 2. Users (create first so we can reference them)
        User demoUser = new User();
        demoUser.setUsername("demo");
        demoUser.setEmail("demo@cuenti.de");
        demoUser.setPassword(passwordEncoder.encode("demo123"));
        demoUser.setFirstName("Max");
        demoUser.setLastName("Mustermann");
        demoUser.setEnabled(true);
        demoUser.setRoles(new HashSet<>(Collections.singletonList("ROLE_USER")));
        demoUser.getRoles().add("ROLE_ADMIN");
        demoUser.setLocale("de-DE");
        demoUser.setDarkMode(true);
        demoUser.setDefaultCurrency("EUR");
        demoUser = userRepository.save(demoUser);

        User demo1User = new User();
        demo1User.setUsername("demo1");
        demo1User.setEmail("demo1@cuenti.de");
        demo1User.setPassword(passwordEncoder.encode("demo123"));
        demo1User.setFirstName("Regular");
        demo1User.setLastName("User");
        demo1User.setEnabled(true);
        demo1User.setRoles(new HashSet<>(Collections.singletonList("ROLE_USER")));
        demo1User.setLocale("de-DE");
        demo1User.setDarkMode(true);
        demo1User.setDefaultCurrency("EUR");
        demo1User = userRepository.save(demo1User);

        // 3. Currencies (for demo user)
        Currency eur = Currency.builder()
                .user(demoUser)
                .code("EUR")
                .name("Euro")
                .symbol("€")
                .decimalChar(",")
                .groupingChar(".")
                .fracDigits(2)
                .build();
        eur = currencyRepository.save(eur);

        Currency usd = Currency.builder()
                .user(demoUser)
                .code("USD")
                .name("US Dollar")
                .symbol("$")
                .decimalChar(".")
                .groupingChar(",")
                .fracDigits(2)
                .build();
        usd = currencyRepository.save(usd);

        // 4. Categories (for demo user)
        Category housing = createCategory(demoUser, "Wohnen", Category.CategoryType.EXPENSE, null);
        Category rent = createCategory(demoUser, "Miete", Category.CategoryType.EXPENSE, housing);
        Category electricity = createCategory(demoUser, "Strom", Category.CategoryType.EXPENSE, housing);
        Category internet = createCategory(demoUser, "Internet", Category.CategoryType.EXPENSE, housing);

        Category food = createCategory(demoUser, "Verpflegung", Category.CategoryType.EXPENSE, null);
        Category groceries = createCategory(demoUser, "Supermarkt", Category.CategoryType.EXPENSE, food);
        Category restaurant = createCategory(demoUser, "Restaurant", Category.CategoryType.EXPENSE, food);

        Category transport = createCategory(demoUser, "Transport", Category.CategoryType.EXPENSE, null);
        Category car = createCategory(demoUser, "Auto", Category.CategoryType.EXPENSE, transport);
        Category fuel = createCategory(demoUser, "Tanken", Category.CategoryType.EXPENSE, car);
        Category insurance = createCategory(demoUser, "Versicherung", Category.CategoryType.EXPENSE, car);
        Category publicTransport = createCategory(demoUser, "ÖPNV", Category.CategoryType.EXPENSE, transport);

        Category leisure = createCategory(demoUser, "Freizeit", Category.CategoryType.EXPENSE, null);
        Category kino = createCategory(demoUser, "Kino", Category.CategoryType.EXPENSE, leisure);
        Category streaming = createCategory(demoUser, "Streaming", Category.CategoryType.EXPENSE, leisure);

        Category incomeCat = createCategory(demoUser, "Einkommen", Category.CategoryType.INCOME, null);
        Category salary = createCategory(demoUser, "Gehalt", Category.CategoryType.INCOME, incomeCat);
        createCategory(demoUser, "Dividenden", Category.CategoryType.INCOME, incomeCat);
        createCategory(demoUser, "Zinsen", Category.CategoryType.INCOME, incomeCat);

        // 5. Tags (for demo user)
        createTag(demoUser, "Monatlich");
        createTag(demoUser, "Arbeit");
        createTag(demoUser, "Hobby");

        // 6. Payees (for demo user)
        createPayee(demoUser, "REWE Markt", groceries);
        createPayee(demoUser, "Lidl", groceries);
        createPayee(demoUser, "Deutsche Telekom", internet);
        createPayee(demoUser, "Global Tech GmbH", salary);
        createPayee(demoUser, "Shell", fuel);
        createPayee(demoUser, "BVG", publicTransport);

        // 7. Assets (for demo user)
        Asset vwce = createAsset(demoUser, "VWCE.DE", "Vanguard FTSE All-World", Asset.AssetType.ETF);
        Asset amzn = createAsset(demoUser, "AMZN", "Amazon.com, Inc.", Asset.AssetType.STOCK);
        Asset amd = createAsset(demoUser, "AMD", "Advanced Micro Devices, Inc.", Asset.AssetType.STOCK);
        Asset btc = createAsset(demoUser, "BTC-EUR", "Bitcoin EUR", Asset.AssetType.CRYPTO);

        // 8. Accounts (for demo user)
        BigDecimal n26Start = new BigDecimal("5000.00");
        Account n26 = new Account();
        n26.setUser(demoUser);
        n26.setAccountName("N26 Girokonto");
        n26.setAccountNumber("DE12345678901234567890");
        n26.setInstitution("N26");
        n26.setAccountGroup("Bargeld");
        n26.setAccountType(Account.AccountType.CURRENT);
        n26.setCurrency("EUR");
        n26.setStartBalance(n26Start);
        n26.setBalance(n26Start); // Will be updated by transactions
        n26 = accountService.saveAccountForUser(n26, demoUser);

        BigDecimal ingStart = new BigDecimal("20000.00");
        Account ing = new Account();
        ing.setUser(demoUser);
        ing.setAccountName("ING Extra-Konto");
        ing.setAccountNumber("DE99887766554433221100");
        ing.setInstitution("ING");
        ing.setAccountGroup("Ersparnisse");
        ing.setAccountType(Account.AccountType.SAVINGS);
        ing.setCurrency("EUR");
        ing.setStartBalance(ingStart);
        ing.setBalance(ingStart); // Will be updated by transactions
        ing = accountService.saveAccountForUser(ing, demoUser);

        Account invest = new Account();
        invest.setUser(demoUser);
        invest.setAccountName("Portfolio");
        invest.setAccountNumber("ASSET-001");
        invest.setInstitution("Various");
        invest.setAccountGroup("Investments");
        invest.setAccountType(Account.AccountType.ASSET);
        invest.setCurrency("EUR");
        invest.setBalance(BigDecimal.ZERO);
        invest = accountService.saveAccountForUser(invest, demoUser);

        // 9. Asset Acquisition Transactions (for demo user) - creates TRANSFER transactions to asset account
        createAssetTransaction(demoUser, vwce, new BigDecimal("668.24008"), new BigDecimal("105.50"), n26, invest);
        createAssetTransaction(demoUser, amd, new BigDecimal("20.0"), new BigDecimal("165.20"), n26, invest);
        createAssetTransaction(demoUser, amzn, new BigDecimal("10.0"), new BigDecimal("145.80"), n26, invest);
        createAssetTransaction(demoUser, btc, new BigDecimal("0.10579528"), new BigDecimal("42500.00"), n26, invest);

        // 10. Monthly Data Simulation (for demo user)
        LocalDateTime startDate = LocalDateTime.of(2025, 1, 1, 0, 0);
        for (int i = 0; i < 24; i++) {
            LocalDateTime monthDate = startDate.plusMonths(i);
            saveSimpleTransaction(Transaction.TransactionType.INCOME, null, n26, new BigDecimal("3450.00"), "Global Tech GmbH", salary, "Gehalt " + monthDate.getMonth().name(), monthDate.withDayOfMonth(25).withHour(9).withMinute(0));
            saveSimpleTransaction(Transaction.TransactionType.EXPENSE, n26, null, new BigDecimal("1200.00"), "Hausverwaltung Schmidt", rent, "Warmmiete", monthDate.withDayOfMonth(1).withHour(10).withMinute(0));
            saveSimpleTransaction(Transaction.TransactionType.TRANSFER, n26, ing, new BigDecimal("500.00"), null, null, "Sparrate", monthDate.withDayOfMonth(26).withHour(10).withMinute(0));
        }

        // 11. Scheduled Transactions (for demo user)
        createScheduledTransaction(demoUser, Transaction.TransactionType.INCOME, null, n26, new BigDecimal("3450.00"), "Global Tech GmbH", salary, "Monthly Salary", ScheduledTransaction.RecurrencePattern.MONTHLY, 1, LocalDateTime.now().withDayOfMonth(25).withHour(9).withMinute(0));
        createScheduledTransaction(demoUser, Transaction.TransactionType.EXPENSE, n26, null, new BigDecimal("1200.00"), "Hausverwaltung Schmidt", rent, "Monthly Rent", ScheduledTransaction.RecurrencePattern.MONTHLY, 1, LocalDateTime.now().plusMonths(1).withDayOfMonth(1).withHour(10).withMinute(0));
        createScheduledTransaction(demoUser, Transaction.TransactionType.EXPENSE, n26, null, new BigDecimal("39.95"), "Deutsche Telekom", internet, "Internet Bill", ScheduledTransaction.RecurrencePattern.MONTHLY, 1, LocalDateTime.now().plusMonths(1).withDayOfMonth(5).withHour(11).withMinute(0));
        createScheduledTransaction(demoUser, Transaction.TransactionType.EXPENSE, n26, null, new BigDecimal("15.99"), "Netflix", streaming, "Netflix Subscription", ScheduledTransaction.RecurrencePattern.MONTHLY, 1, LocalDateTime.now().minusDays(2).withHour(8).withMinute(0)); // Late one

        log.info("Comprehensive demo data initialization complete!");
    }

    /**
     * Initialize only essential reference data for production (no demo users or transactions)
     */
    private void initializeEssentialData() {
        log.info("Essential data for production mode:");
        log.info("- Each user will create their own currencies, categories, tags, and payees");
        log.info("- Users can register via the registration page");
        log.info("Production mode initialization complete");
    }

    private Asset createAsset(User user, String symbol, String name, Asset.AssetType type) {
        Asset asset = Asset.builder()
                .user(user)
                .symbol(symbol)
                .name(name)
                .type(type)
                .build();
        return assetRepository.save(asset);
    }

    private void createAssetTransaction(User user, Asset asset, BigDecimal quantity, BigDecimal avgPrice, Account fromAccount, Account toAccount) {
        // Create a TRANSFER transaction from bank account to asset account
        BigDecimal totalCost = quantity.multiply(avgPrice);

        Transaction t = Transaction.builder()
                .type(Transaction.TransactionType.TRANSFER)
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(totalCost)
                .asset(asset)
                .units(quantity)
                .payee(asset.getName())
                .memo("Acquisition of " + quantity + " units of " + asset.getSymbol())
                .transactionDate(LocalDateTime.now().minusMonths(6))
                .status(Transaction.TransactionStatus.COMPLETED)
                .paymentMethod(Transaction.PaymentMethod.TRADE)
                .build();
        transactionRepository.save(t);
    }

    private void createScheduledTransaction(User user, Transaction.TransactionType type, Account from, Account to, BigDecimal amount, String payee, Category cat, String memo, ScheduledTransaction.RecurrencePattern pattern, Integer value, LocalDateTime next) {
        ScheduledTransaction st = ScheduledTransaction.builder()
                .user(user)
                .type(type)
                .fromAccount(from)
                .toAccount(to)
                .amount(amount)
                .payee(payee)
                .category(cat)
                .memo(memo)
                .recurrencePattern(pattern)
                .recurrenceValue(value)
                .nextOccurrence(next)
                .enabled(true)
                .build();
        scheduledTransactionRepository.save(st);
    }

    private void saveSimpleTransaction(Transaction.TransactionType type, Account from, Account to, BigDecimal amount, String payee, Category cat, String memo, LocalDateTime date) {
        Transaction t = new Transaction();
        t.setType(type);
        t.setFromAccount(from);
        t.setToAccount(to);
        t.setAmount(amount);
        t.setPayee(payee);
        t.setCategory(cat);
        t.setMemo(memo);
        t.setTransactionDate(date);
        t.setStatus(Transaction.TransactionStatus.COMPLETED);
        transactionRepository.save(t);
    }

    private Category createCategory(User user, String name, Category.CategoryType type, Category parent) {
        Category cat = new Category();
        cat.setUser(user);
        cat.setName(name);
        cat.setType(type);
        cat.setParent(parent);
        return categoryRepository.save(cat);
    }

    private Tag createTag(User user, String name) {
        Tag tag = new Tag();
        tag.setUser(user);
        tag.setName(name);
        return tagRepository.save(tag);
    }

    private Payee createPayee(User user, String name, Category defaultCategory) {
        Payee payee = new Payee();
        payee.setUser(user);
        payee.setName(name);
        payee.setDefaultCategory(defaultCategory);
        return payeeRepository.save(payee);
    }
}
