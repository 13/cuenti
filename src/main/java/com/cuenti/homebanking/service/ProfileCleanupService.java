package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.*;
import com.cuenti.homebanking.repository.*;
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

    @Transactional
    public void cleanupUserData(User user) {
        // 1. Delete user-specific transaction data
        transactionRepository.findByUser(user).forEach(transactionRepository::delete);
        scheduledTransactionRepository.findByUser(user).forEach(scheduledTransactionRepository::delete);
        
        // 2. Delete user-specific accounts
        accountRepository.findByUser(user).forEach(accountRepository::delete);

        // 3. Optional: Global entities (Payees, Tags, Categories) 
        // Note: These are currently shared across users in our model. 
        // If we want to start with a truly clean profile including custom categories:
        // (Only delete if they aren't protected system defaults)
        
        // Since the requirement is a "clean profile", we remove all history linked to this user.
    }
}
