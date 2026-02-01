package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.Account;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

/**
 * Service for account management operations.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final Random random = new Random();

    @Transactional
    public Account saveAccount(Account account) {
        if (account.getAccountNumber() == null || account.getAccountNumber().isEmpty()) {
            account.setAccountNumber(generateAccountNumber());
        }
        return accountRepository.save(account);
    }

    @Transactional
    public void deleteAccount(Account account) {
        accountRepository.delete(account);
    }

    @Transactional
    public void updateSortOrders(List<Account> accounts) {
        for (int i = 0; i < accounts.size(); i++) {
            Account account = accounts.get(i);
            account.setSortOrder(i);
            accountRepository.save(account);
        }
    }

    @Transactional
    public Account createAccount(User user, Account.AccountType accountType, BigDecimal initialBalance) {
        Account account = Account.builder()
                .accountName(accountType.name() + " Account")
                .accountNumber(generateAccountNumber())
                .accountType(accountType)
                .balance(initialBalance != null ? initialBalance : BigDecimal.ZERO)
                .startBalance(initialBalance != null ? initialBalance : BigDecimal.ZERO)
                .user(user)
                .build();

        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public List<Account> getAccountsByUser(User user) {
        return accountRepository.findByUserOrderBySortOrderAsc(user);
    }

    @Transactional(readOnly = true)
    public Account findByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));
    }

    @Transactional(readOnly = true)
    public Account findById(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
    }

    @Transactional
    public Account updateBalance(Account account, BigDecimal newBalance) {
        account.setBalance(newBalance);
        return accountRepository.save(account);
    }

    private String generateAccountNumber() {
        String accountNumber;
        do {
            accountNumber = "DE" + String.format("%018d", Math.abs(random.nextLong() % 1000000000000000000L));
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }
}
