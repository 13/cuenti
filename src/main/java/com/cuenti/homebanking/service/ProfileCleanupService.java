package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.*;
import com.cuenti.homebanking.repository.*;
import com.cuenti.homebanking.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileCleanupService {

    private final TransactionRepository transactionRepository;
    private final ScheduledTransactionRepository scheduledTransactionRepository;
    private final AccountRepository accountRepository;
    private final PayeeRepository payeeRepository;
    private final TagRepository tagRepository;
    private final CategoryRepository categoryRepository;
    private final UserService userService;
    private final SecurityUtils securityUtils;

    @Transactional
    public void cleanupUserData(User user) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);

        // Security check: users can only cleanup their own data
        if (!currentUser.getId().equals(user.getId())) {
            throw new SecurityException("Cannot cleanup data for another user");
        }

        // 1. Delete user-specific transaction data
        transactionRepository.findByUser(user).forEach(transactionRepository::delete);
        scheduledTransactionRepository.findByUser(user).forEach(scheduledTransactionRepository::delete);
        
        // 2. Delete user-specific accounts
        accountRepository.findByUser(user).forEach(accountRepository::delete);

        // 3. Delete user-specific payees, tags, and categories
        payeeRepository.findByUser(user).forEach(payeeRepository::delete);
        tagRepository.findByUser(user).forEach(tagRepository::delete);
        categoryRepository.findByUser(user).forEach(categoryRepository::delete);
    }
}
