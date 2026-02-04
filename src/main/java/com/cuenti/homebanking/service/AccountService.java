package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.Account;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.repository.AccountRepository;
import com.cuenti.homebanking.security.SecurityUtils;
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
    private final UserService userService;
    private final SecurityUtils securityUtils;
    private final Random random = new Random();

    @Transactional
    public Account saveAccount(Account account) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);

        return saveAccountForUser(account, currentUser);
    }

    /**
     * Save account for a specific user (used during initialization or admin operations)
     */
    @Transactional
    public Account saveAccountForUser(Account account, User user) {
        if (account.getAccountNumber() == null || account.getAccountNumber().isEmpty()) {
            account.setAccountNumber(generateAccountNumber());
        }

        // If it's a new account, set the user
        if (account.getId() == null) {
            account.setUser(user);
        } else {
            // If updating, verify the user owns it
            Account existing = accountRepository.findById(account.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Account not found"));
            if (!existing.getUser().getId().equals(user.getId())) {
                throw new SecurityException("Cannot modify account belonging to another user");
            }
            account.setUser(user);
        }

        return accountRepository.save(account);
    }

    @Transactional
    public void deleteAccount(Account account) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);

        // Security check: only allow deletion if account belongs to current user
        if (!account.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Cannot delete account belonging to another user");
        }
        accountRepository.delete(account);
    }

    @Transactional
    public void updateSortOrders(List<Account> accounts) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);

        for (int i = 0; i < accounts.size(); i++) {
            Account account = accounts.get(i);

            // Security check: verify all accounts belong to current user
            if (!account.getUser().getId().equals(currentUser.getId())) {
                throw new SecurityException("Cannot modify account belonging to another user");
            }

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
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        // Security check: verify account belongs to current user
        if (!account.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Cannot access account belonging to another user");
        }

        return account;
    }

    /**
     * Get account by ID without security checks (for internal service use only).
     * Caller is responsible for security validation.
     */
    @Transactional(readOnly = true)
    public Account getAccountById(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
    }

    @Transactional
    public Account updateBalance(Account account, BigDecimal newBalance) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);

        // Security check: verify account belongs to current user
        if (!account.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Cannot modify account belonging to another user");
        }

        account.setBalance(newBalance);
        return accountRepository.save(account);
    }

    @Transactional
    public void adjustStartBalance(Account account, BigDecimal newStartBalance) {
        if (account.getId() == null) {
            // New account: just set startBalance and balance
            account.setStartBalance(newStartBalance != null ? newStartBalance : BigDecimal.ZERO);
            account.setBalance(account.getBalance().add(account.getStartBalance()));
            return;
        }

        // Existing account: load persisted account to compute delta
        Account persisted = accountRepository.findById(account.getId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + account.getId()));

        BigDecimal oldStart = persisted.getStartBalance() != null ? persisted.getStartBalance() : BigDecimal.ZERO;
        BigDecimal delta = newStartBalance.subtract(oldStart);

        account.setStartBalance(newStartBalance);
        account.setBalance(account.getBalance().add(delta));
    }


    private String generateAccountNumber() {
        String accountNumber;
        do {
            accountNumber = "DE" + String.format("%018d", Math.abs(random.nextLong() % 1000000000000000000L));
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }
}
