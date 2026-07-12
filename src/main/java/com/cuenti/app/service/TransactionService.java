package com.cuenti.app.service;

import com.cuenti.app.model.Account;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import com.cuenti.app.repository.TransactionRepository;
import com.cuenti.app.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for transaction management and money transfers.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final UserService userService;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    /**
     * Create or update a transaction and update account balances.
     */
    @Transactional
    public Transaction saveTransaction(Transaction transaction) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);

        validateAmountNotNegative(transaction);
        checkAccountOwnership(transaction, currentUser);

        // If updating, verify user owns the existing transaction
        boolean created = transaction.getId() == null;
        if (!created) {
            Transaction existing = transactionRepository.findById(transaction.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
            User existingUser = getTransactionUser(existing);
            if (!existingUser.getId().equals(currentUser.getId())) {
                throw new SecurityException("Cannot modify transaction belonging to another user");
            }
            reverseBalanceEffect(existing);
        }

        return finishSave(transaction, currentUser, created);
    }

    /**
     * Updates a transaction by id, applying {@code mutator} to a freshly loaded, writable
     * managed entity inside this single write transaction.
     *
     * <p>Unlike a naive "load (read-only) -> mutate in caller -> save" flow, this method
     * loads the entity itself and reverses its balance effect using the OLD amount/type/
     * accounts BEFORE the mutator runs. That ordering matters: if a caller instead loaded
     * the entity via a {@code readOnly} transaction, mutated it there, and then called
     * {@code saveTransaction}, the two calls could share the same OSIV first-level-cache
     * instance, so the balance-reversal step would see the caller's already-mutated (new)
     * values instead of the true old ones - corrupting account balances. Loading and
     * reversing here, before any mutation, avoids that entirely.
     */
    @Transactional
    public Transaction updateTransaction(Long id, java.util.function.Consumer<Transaction> mutator) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);

        Transaction existing = transactionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        User existingUser = getTransactionUser(existing);
        if (!existingUser.getId().equals(currentUser.getId())) {
            throw new SecurityException("Cannot modify transaction belonging to another user");
        }

        // Reverse using the OLD amount/type/accounts before the mutator changes anything.
        reverseBalanceEffect(existing);

        mutator.accept(existing);

        // Re-validate against the NEW state the mutator produced.
        validateAmountNotNegative(existing);
        checkAccountOwnership(existing, currentUser);

        return finishSave(existing, currentUser, false);
    }

    private void validateAmountNotNegative(Transaction transaction) {
        if (transaction.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
    }

    /** Verifies all accounts referenced by the transaction belong to currentUser. */
    private void checkAccountOwnership(Transaction transaction, User currentUser) {
        if (transaction.getFromAccount() != null &&
            !transaction.getFromAccount().getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Cannot use account belonging to another user");
        }
        if (transaction.getToAccount() != null &&
            !transaction.getToAccount().getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Cannot use account belonging to another user");
        }
    }

    /**
     * Shared tail of create/update: reload accounts as managed entities, apply the
     * (new) balance effect, persist, and audit-log. Assumes any balance reversal for
     * an update has already happened.
     */
    private Transaction finishSave(Transaction transaction, User currentUser, boolean created) {
        // Reload accounts from repository to ensure we work with managed entities
        // This prevents double balance updates when the same account is referenced by different instances
        if (transaction.getFromAccount() != null && transaction.getFromAccount().getId() != null) {
            transaction.setFromAccount(accountService.getAccountById(transaction.getFromAccount().getId()));
        }
        if (transaction.getToAccount() != null && transaction.getToAccount().getId() != null) {
            transaction.setToAccount(accountService.getAccountById(transaction.getToAccount().getId()));
        }

        applyBalanceEffect(transaction);

        transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        Transaction saved = transactionRepository.save(transaction);
        auditService.log(currentUser, created ? "CREATE" : "UPDATE", "Transaction", saved.getId(),
                auditDetails(saved));
        return saved;
    }

    private static String auditDetails(Transaction t) {
        return t.getType() + " " + t.getAmount()
                + (t.getPayee() != null ? " " + t.getPayee() : "")
                + " " + t.getTransactionDate();
    }

    private User getTransactionUser(Transaction transaction) {
        if (transaction.getFromAccount() != null) {
            return transaction.getFromAccount().getUser();
        } else if (transaction.getToAccount() != null) {
            return transaction.getToAccount().getUser();
        }
        throw new IllegalStateException("Transaction has no associated account");
    }

    private void applyBalanceEffect(Transaction t) {
        BigDecimal amount = t.getAmount();
        switch (t.getType()) {
            case EXPENSE:
                Account expenseAcc = t.getFromAccount();
                if (expenseAcc != null) {
                    BigDecimal balance = expenseAcc.getBalance() != null ? expenseAcc.getBalance() : BigDecimal.ZERO;
                    expenseAcc.setBalance(balance.subtract(amount));
                    accountService.saveAccount(expenseAcc);
                }
                break;
            case INCOME:
                Account incomeAcc = t.getToAccount();
                if (incomeAcc != null) {
                    BigDecimal balance = incomeAcc.getBalance() != null ? incomeAcc.getBalance() : BigDecimal.ZERO;
                    incomeAcc.setBalance(balance.add(amount));
                    accountService.saveAccount(incomeAcc);
                }
                break;
            case TRANSFER:
                Account fromAcc = t.getFromAccount();
                Account toAcc = t.getToAccount();
                if (fromAcc != null && toAcc != null) {
                    BigDecimal fromBalance = fromAcc.getBalance() != null ? fromAcc.getBalance() : BigDecimal.ZERO;
                    BigDecimal toBalance = toAcc.getBalance() != null ? toAcc.getBalance() : BigDecimal.ZERO;
                    fromAcc.setBalance(fromBalance.subtract(amount));
                    toAcc.setBalance(toBalance.add(amount));
                    accountService.saveAccount(fromAcc);
                    accountService.saveAccount(toAcc);
                }
                break;
        }
    }

    private void reverseBalanceEffect(Transaction t) {
        BigDecimal amount = t.getAmount();
        switch (t.getType()) {
            case EXPENSE:
                Account eAcc = t.getFromAccount();
                if (eAcc != null) {
                    BigDecimal balance = eAcc.getBalance() != null ? eAcc.getBalance() : BigDecimal.ZERO;
                    eAcc.setBalance(balance.add(amount));
                    accountService.saveAccount(eAcc);
                }
                break;
            case INCOME:
                Account iAcc = t.getToAccount();
                if (iAcc != null) {
                    BigDecimal balance = iAcc.getBalance() != null ? iAcc.getBalance() : BigDecimal.ZERO;
                    iAcc.setBalance(balance.subtract(amount));
                    accountService.saveAccount(iAcc);
                }
                break;
            case TRANSFER:
                Account fAcc = t.getFromAccount();
                Account tAcc = t.getToAccount();
                if (fAcc != null && tAcc != null) {
                    BigDecimal fromBalance = fAcc.getBalance() != null ? fAcc.getBalance() : BigDecimal.ZERO;
                    BigDecimal toBalance = tAcc.getBalance() != null ? tAcc.getBalance() : BigDecimal.ZERO;
                    fAcc.setBalance(fromBalance.add(amount));
                    tAcc.setBalance(toBalance.subtract(amount));
                    accountService.saveAccount(fAcc);
                    accountService.saveAccount(tAcc);
                }
                break;
        }
    }

    /**
     * Get all transactions for an account.
     */
    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsByAccount(Account account) {
        return transactionRepository.findByAccount(account);
    }

    /** Grid window query: filters applied by the database. */
    public List<Transaction> getTransactionsFiltered(User user, Account account,
            Transaction.TransactionType type, java.time.LocalDateTime from, java.time.LocalDateTime to) {
        return transactionRepository.findFiltered(user, account, type, from, to);
    }

    /** id → running balance for the filter window, computed via SQL window function. */
    public java.util.Map<Long, BigDecimal> getRunningBalances(User user, Account account,
            Transaction.TransactionType type, java.time.LocalDateTime from, java.time.LocalDateTime to) {
        List<Object[]> rows = account == null
                ? transactionRepository.runningBalancesForUser(user.getId(), from, to,
                        type != null ? type.name() : null)
                : transactionRepository.runningBalancesForAccount(account.getId(), from, to,
                        type != null ? type.name() : null);
        java.util.Map<Long, BigDecimal> result = new java.util.HashMap<>();
        for (Object[] row : rows) {
            result.put(((Number) row[0]).longValue(), (BigDecimal) row[1]);
        }
        return result;
    }

    /**
     * Get all transactions for a specific user.
     */
    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsByUser(User user) {
        return transactionRepository.findByUser(user);
    }

    /** Paged, filtered search backing the REST API's GET /api/transactions. */
    @Transactional(readOnly = true)
    public Page<Transaction> search(User user, Long accountId, Transaction.TransactionType type,
                                    Long categoryId, java.time.LocalDateTime from, java.time.LocalDateTime to,
                                    String payee, String tag, String search, Pageable pageable) {
        return transactionRepository.searchByUser(user, accountId, type, categoryId,
                from, to, payee, tag, search, pageable);
    }

    @Transactional
    public void deleteTransaction(Transaction transaction) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);

        transactionRepository.findById(transaction.getId()).ifPresent(t -> {
            // Security check: verify user owns the transaction
            User transactionUser = getTransactionUser(t);
            if (!transactionUser.getId().equals(currentUser.getId())) {
                throw new SecurityException("Cannot delete transaction belonging to another user");
            }

            reverseBalanceEffect(t);
            transactionRepository.delete(t);
            auditService.log(currentUser, "DELETE", "Transaction", t.getId(), auditDetails(t));
        });
    }
}
