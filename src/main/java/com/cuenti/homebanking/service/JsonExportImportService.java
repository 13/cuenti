package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.*;
import com.cuenti.homebanking.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JsonExportImportService {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final PayeeService payeeService;
    private final TagService tagService;
    private final AssetService assetService;
    private final ScheduledTransactionService scheduledTransactionService;
    private final CurrencyRepository currencyRepository;
    private final TransactionRepository transactionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Export all user data to JSON format
     */
    @Transactional(readOnly = true)
    public void exportUserData(User user, OutputStream outputStream) throws Exception {
        log.info("Exporting data for user: {}", user.getUsername());

        UserDataExport export = new UserDataExport();

        // Export accounts
        export.accounts = accountService.getAccountsByUser(user).stream()
                .map(this::convertAccountToDTO)
                .collect(Collectors.toList());

        // Export transactions
        export.transactions = transactionService.getTransactionsByUser(user).stream()
                .map(this::convertTransactionToDTO)
                .collect(Collectors.toList());

        // Export categories
        export.categories = categoryService.getAllCategories().stream()
                .map(this::convertCategoryToDTO)
                .collect(Collectors.toList());

        // Export payees
        export.payees = payeeService.getAllPayees().stream()
                .map(this::convertPayeeToDTO)
                .collect(Collectors.toList());

        // Export tags
        export.tags = tagService.getAllTags().stream()
                .map(Tag::getName)
                .collect(Collectors.toList());

        // Export assets
        export.assets = assetService.getAllAssets().stream()
                .map(this::convertAssetToDTO)
                .collect(Collectors.toList());

        // Export scheduled transactions
        export.scheduledTransactions = scheduledTransactionService.getByUser(user).stream()
                .map(this::convertScheduledTransactionToDTO)
                .collect(Collectors.toList());

        // Export metadata
        export.exportDate = new java.util.Date();
        export.username = user.getUsername();
        export.version = "1.0";

        objectMapper.writeValue(outputStream, export);
        log.info("Export completed successfully");
    }

    /**
     * Import user data from JSON format
     */
    @Transactional
    public void importUserData(User user, InputStream inputStream) throws Exception {
        log.info("Importing data for user: {}", user.getUsername());

        UserDataExport data = objectMapper.readValue(inputStream, UserDataExport.class);

        // Maps to track imported entities by their old IDs
        Map<String, Category> categoryMap = new HashMap<>();
        Map<String, Payee> payeeMap = new HashMap<>();
        Map<String, Account> accountMap = new HashMap<>();
        Map<String, Asset> assetMap = new HashMap<>();

        int importedTransactionsSortOrder = getMaxSortOrder() + 1;

        // 1. Import tags
        if (data.tags != null) {
            for (String tagName : data.tags) {
                ensureTagExists(tagName);
            }
        }

        // 2. Import categories (handle hierarchy)
        if (data.categories != null) {
            // First pass: create all categories without parent
            for (CategoryDTO dto : data.categories) {
                Category cat = Category.builder()
                        .name(dto.name)
                        .type(Category.CategoryType.valueOf(dto.type))
                        .build();
                categoryMap.put(dto.id, categoryService.saveCategory(cat));
            }

            // Second pass: set parent relationships
            for (CategoryDTO dto : data.categories) {
                if (dto.parentId != null && !dto.parentId.isEmpty()) {
                    Category cat = categoryMap.get(dto.id);
                    Category parent = categoryMap.get(dto.parentId);
                    if (parent != null) {
                        cat.setParent(parent);
                        categoryService.saveCategory(cat);
                    }
                }
            }
        }

        // 3. Import payees
        if (data.payees != null) {
            for (PayeeDTO dto : data.payees) {
                Payee payee = Payee.builder()
                        .name(dto.name)
                        .build();

                if (dto.defaultCategoryId != null && categoryMap.containsKey(dto.defaultCategoryId)) {
                    payee.setDefaultCategory(categoryMap.get(dto.defaultCategoryId));
                }

                payeeMap.put(dto.id, payeeService.savePayee(payee));
            }
        }

        // 4. Import assets
        if (data.assets != null) {
            for (AssetDTO dto : data.assets) {
                // Check if asset already exists by symbol
                List<Asset> existing = assetService.searchAssets(dto.symbol);
                Asset asset = existing.stream()
                        .filter(a -> a.getSymbol().equals(dto.symbol))
                        .findFirst()
                        .orElse(null);

                if (asset == null) {
                    asset = Asset.builder()
                            .symbol(dto.symbol)
                            .name(dto.name)
                            .type(Asset.AssetType.valueOf(dto.type))
                            .currentPrice(dto.currentPrice)
                            .currency(dto.currency)
                            .build();
                    asset = assetService.saveAsset(asset);
                }

                assetMap.put(dto.id, asset);
            }
        }

        // 5. Import accounts
        if (data.accounts != null) {
            for (AccountDTO dto : data.accounts) {
                Account account = Account.builder()
                        .user(user)
                        .accountName(dto.accountName)
                        .accountNumber(dto.accountNumber)
                        .accountType(Account.AccountType.valueOf(dto.accountType))
                        .accountGroup(dto.accountGroup)
                        .institution(dto.institution)
                        .currency(dto.currency)
                        .startBalance(dto.startBalance)
                        .balance(dto.startBalance) // Will be updated by transactions
                        .sortOrder(dto.sortOrder != null ? dto.sortOrder : 0)
                        .build();

                accountMap.put(dto.id, accountService.saveAccount(account));
            }
        }

        // 6. Import transactions
        if (data.transactions != null) {
            for (TransactionDTO dto : data.transactions) {
                Transaction transaction = new Transaction();
                transaction.setType(Transaction.TransactionType.valueOf(dto.type));
                transaction.setAmount(dto.amount);
                transaction.setTransactionDate(dto.transactionDate);
                transaction.setMemo(dto.memo);
                transaction.setTags(dto.tags);
                transaction.setPayee(dto.payee);
                transaction.setNumber(dto.number);
                transaction.setStatus(Transaction.TransactionStatus.valueOf(dto.status));
                transaction.setSortOrder(importedTransactionsSortOrder++);

                if (dto.fromAccountId != null && accountMap.containsKey(dto.fromAccountId)) {
                    transaction.setFromAccount(accountMap.get(dto.fromAccountId));
                }

                if (dto.toAccountId != null && accountMap.containsKey(dto.toAccountId)) {
                    transaction.setToAccount(accountMap.get(dto.toAccountId));
                }

                if (dto.categoryId != null && categoryMap.containsKey(dto.categoryId)) {
                    transaction.setCategory(categoryMap.get(dto.categoryId));
                }

                if (dto.assetId != null && assetMap.containsKey(dto.assetId)) {
                    transaction.setAsset(assetMap.get(dto.assetId));
                    transaction.setUnits(dto.units);
                }

                if (dto.paymentMethod != null) {
                    transaction.setPaymentMethod(Transaction.PaymentMethod.valueOf(dto.paymentMethod));
                }

                transactionService.saveTransaction(transaction);
            }
        }

        // 8. Import scheduled transactions
        if (data.scheduledTransactions != null) {
            for (ScheduledTransactionDTO dto : data.scheduledTransactions) {
                ScheduledTransaction st = ScheduledTransaction.builder()
                        .user(user)
                        .type(Transaction.TransactionType.valueOf(dto.type))
                        .amount(dto.amount)
                        .nextOccurrence(dto.nextOccurrence)
                        .memo(dto.memo)
                        .tags(dto.tags)
                        .payee(dto.payee)
                        .recurrencePattern(ScheduledTransaction.RecurrencePattern.valueOf(dto.recurrencePattern))
                        .recurrenceValue(dto.recurrenceValue)
                        .enabled(dto.enabled)
                        .build();

                if (dto.fromAccountId != null && accountMap.containsKey(dto.fromAccountId)) {
                    st.setFromAccount(accountMap.get(dto.fromAccountId));
                }

                if (dto.toAccountId != null && accountMap.containsKey(dto.toAccountId)) {
                    st.setToAccount(accountMap.get(dto.toAccountId));
                }

                if (dto.categoryId != null && categoryMap.containsKey(dto.categoryId)) {
                    st.setCategory(categoryMap.get(dto.categoryId));
                }

                scheduledTransactionService.save(st);
            }
        }

        log.info("Import completed successfully");
    }

    private int getMaxSortOrder() {
        List<Transaction> allTransactions = transactionRepository.findAll();
        return allTransactions.stream()
                .mapToInt(Transaction::getSortOrder)
                .max()
                .orElse(0);
    }

    private void ensureTagExists(String tagName) {
        if (tagName == null || tagName.isEmpty()) return;
        if (tagService.searchTags(tagName).stream().noneMatch(t -> t.getName().equalsIgnoreCase(tagName))) {
            tagService.saveTag(Tag.builder().name(tagName).build());
        }
    }

    // DTO conversion methods

    private AccountDTO convertAccountToDTO(Account account) {
        AccountDTO dto = new AccountDTO();
        dto.id = account.getId().toString();
        dto.accountName = account.getAccountName();
        dto.accountNumber = account.getAccountNumber();
        dto.accountType = account.getAccountType().name();
        dto.accountGroup = account.getAccountGroup();
        dto.institution = account.getInstitution();
        dto.currency = account.getCurrency();
        dto.startBalance = account.getStartBalance();
        dto.balance = account.getBalance();
        dto.sortOrder = account.getSortOrder();
        return dto;
    }

    private TransactionDTO convertTransactionToDTO(Transaction t) {
        TransactionDTO dto = new TransactionDTO();
        dto.id = t.getId().toString();
        dto.type = t.getType().name();
        dto.amount = t.getAmount();
        dto.transactionDate = t.getTransactionDate();
        dto.status = t.getStatus().name();
        dto.payee = t.getPayee();
        dto.memo = t.getMemo();
        dto.tags = t.getTags();
        dto.number = t.getNumber();
        dto.sortOrder = t.getSortOrder();
        dto.units = t.getUnits();

        if (t.getFromAccount() != null) dto.fromAccountId = t.getFromAccount().getId().toString();
        if (t.getToAccount() != null) dto.toAccountId = t.getToAccount().getId().toString();
        if (t.getCategory() != null) dto.categoryId = t.getCategory().getId().toString();
        if (t.getAsset() != null) dto.assetId = t.getAsset().getId().toString();
        if (t.getPaymentMethod() != null) dto.paymentMethod = t.getPaymentMethod().name();

        return dto;
    }

    private CategoryDTO convertCategoryToDTO(Category category) {
        CategoryDTO dto = new CategoryDTO();
        dto.id = category.getId().toString();
        dto.name = category.getName();
        dto.type = category.getType().name();
        if (category.getParent() != null) {
            dto.parentId = category.getParent().getId().toString();
        }
        return dto;
    }

    private PayeeDTO convertPayeeToDTO(Payee payee) {
        PayeeDTO dto = new PayeeDTO();
        dto.id = payee.getId().toString();
        dto.name = payee.getName();
        if (payee.getDefaultCategory() != null) {
            dto.defaultCategoryId = payee.getDefaultCategory().getId().toString();
        }
        return dto;
    }

    private AssetDTO convertAssetToDTO(Asset asset) {
        AssetDTO dto = new AssetDTO();
        dto.id = asset.getId().toString();
        dto.symbol = asset.getSymbol();
        dto.name = asset.getName();
        dto.type = asset.getType().name();
        dto.currentPrice = asset.getCurrentPrice();
        dto.currency = asset.getCurrency();
        return dto;
    }

    private ScheduledTransactionDTO convertScheduledTransactionToDTO(ScheduledTransaction st) {
        ScheduledTransactionDTO dto = new ScheduledTransactionDTO();
        dto.id = st.getId().toString();
        dto.type = st.getType().name();
        dto.amount = st.getAmount();
        dto.nextOccurrence = st.getNextOccurrence();
        dto.payee = st.getPayee();
        dto.memo = st.getMemo();
        dto.tags = st.getTags();
        dto.recurrencePattern = st.getRecurrencePattern().name();
        dto.recurrenceValue = st.getRecurrenceValue();
        dto.enabled = st.isEnabled();

        if (st.getFromAccount() != null) dto.fromAccountId = st.getFromAccount().getId().toString();
        if (st.getToAccount() != null) dto.toAccountId = st.getToAccount().getId().toString();
        if (st.getCategory() != null) dto.categoryId = st.getCategory().getId().toString();

        return dto;
    }

    // Inner DTO classes

    public static class UserDataExport {
        public String version;
        public String username;
        public Date exportDate;
        public List<AccountDTO> accounts;
        public List<TransactionDTO> transactions;
        public List<CategoryDTO> categories;
        public List<PayeeDTO> payees;
        public List<String> tags;
        public List<AssetDTO> assets;
        public List<ScheduledTransactionDTO> scheduledTransactions;
    }

    public static class AccountDTO {
        public String id;
        public String accountName;
        public String accountNumber;
        public String accountType;
        public String accountGroup;
        public String institution;
        public String currency;
        public java.math.BigDecimal startBalance;
        public java.math.BigDecimal balance;
        public Integer sortOrder;
    }

    public static class TransactionDTO {
        public String id;
        public String type;
        public java.math.BigDecimal amount;
        public java.time.LocalDateTime transactionDate;
        public String status;
        public String payee;
        public String memo;
        public String tags;
        public String number;
        public String fromAccountId;
        public String toAccountId;
        public String categoryId;
        public String assetId;
        public java.math.BigDecimal units;
        public String paymentMethod;
        public Integer sortOrder;
    }

    public static class CategoryDTO {
        public String id;
        public String name;
        public String type;
        public String parentId;
    }

    public static class PayeeDTO {
        public String id;
        public String name;
        public String defaultCategoryId;
    }

    public static class AssetDTO {
        public String id;
        public String symbol;
        public String name;
        public String type;
        public java.math.BigDecimal currentPrice;
        public String currency;
    }

    public static class ScheduledTransactionDTO {
        public String id;
        public String type;
        public java.math.BigDecimal amount;
        public java.time.LocalDateTime nextOccurrence;
        public String payee;
        public String memo;
        public String tags;
        public String fromAccountId;
        public String toAccountId;
        public String categoryId;
        public String recurrencePattern;
        public Integer recurrenceValue;
        public Boolean enabled;
    }
}
