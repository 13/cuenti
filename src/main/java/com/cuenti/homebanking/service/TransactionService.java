package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.Account;
import com.cuenti.homebanking.model.Transaction;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.repository.TransactionRepository;
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

    /**
     * Create or update a transaction and update account balances.
     */
    @Transactional
    public Transaction saveTransaction(Transaction transaction) {
        if (transaction.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }

        // If updating, reverse the previous transaction effects
        if (transaction.getId() != null) {
            transactionRepository.findById(transaction.getId()).ifPresent(this::reverseBalanceEffect);
        }

        applyBalanceEffect(transaction);
        
        transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        return transactionRepository.save(transaction);
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
        transactionRepository.findById(transaction.getId()).ifPresent(t -> {
            reverseBalanceEffect(t);
            transactionRepository.delete(t);
        });
    }
}
