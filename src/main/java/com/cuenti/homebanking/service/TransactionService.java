package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.Account;
import com.cuenti.homebanking.model.Transaction;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.repository.TransactionRepository;
import com.cuenti.homebanking.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
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

    /**
     * Create or update a transaction and update account balances.
     */
    @Transactional
    public Transaction saveTransaction(Transaction transaction) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);

        if (transaction.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }

        // Security check: verify all accounts in the transaction belong to current user
        if (transaction.getFromAccount() != null &&
            !transaction.getFromAccount().getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Cannot use account belonging to another user");
        }
        if (transaction.getToAccount() != null &&
            !transaction.getToAccount().getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Cannot use account belonging to another user");
        }

        // If updating, verify user owns the existing transaction
        if (transaction.getId() != null) {
            Transaction existing = transactionRepository.findById(transaction.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
            User existingUser = getTransactionUser(existing);
            if (!existingUser.getId().equals(currentUser.getId())) {
                throw new SecurityException("Cannot modify transaction belonging to another user");
            }
            reverseBalanceEffect(existing);
        }

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
        return transactionRepository.save(transaction);
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

    /**
     * Get all transactions for a specific user.
     */
    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsByUser(User user) {
        return transactionRepository.findByUser(user);
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
        });
    }
}
