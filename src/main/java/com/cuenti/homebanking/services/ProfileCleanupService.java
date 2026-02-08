package com.cuenti.homebanking.services;

import com.cuenti.homebanking.data.*;
import com.cuenti.homebanking.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for cleaning up all user profile data (danger zone functionality).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileCleanupService {

    private final TransactionRepository transactionRepository;
    private final ScheduledTransactionRepository scheduledTransactionRepository;
    private final AccountRepository accountRepository;
    private final PayeeRepository payeeRepository;
    private final TagRepository tagRepository;
    private final CategoryRepository categoryRepository;
    private final AssetRepository assetRepository;
    private final CurrencyRepository currencyRepository;
    private final UserService userService;
    private final SecurityUtils securityUtils;

    /**
     * Delete all data belonging to a user, leaving them with an empty profile.
     * This is the "danger zone" functionality.
     */
    @Transactional
    public void cleanupUserData(User user) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);

        // Security check: users can only cleanup their own data
        if (!currentUser.getId().equals(user.getId())) {
            throw new SecurityException("Cannot cleanup data for another user");
        }

        log.warn("⚠️ CLEANUP: Starting data wipe for user: {}", user.getUsername());

        // 1. Delete transactions first (they reference accounts and categories)
        log.info("Deleting transactions...");
        transactionRepository.findByUser(user).forEach(transactionRepository::delete);

        // 2. Delete scheduled transactions
        log.info("Deleting scheduled transactions...");
        scheduledTransactionRepository.findByUser(user).forEach(scheduledTransactionRepository::delete);

        // 3. Delete accounts
        log.info("Deleting accounts...");
        accountRepository.findByUser(user).forEach(accountRepository::delete);

        // 4. Delete assets
        log.info("Deleting assets...");
        assetRepository.findByUser(user).forEach(assetRepository::delete);

        // 5. Delete payees
        log.info("Deleting payees...");
        payeeRepository.findByUser(user).forEach(payeeRepository::delete);

        // 6. Delete tags
        log.info("Deleting tags...");
        tagRepository.findByUser(user).forEach(tagRepository::delete);

        // 7. Delete categories
        log.info("Deleting categories...");
        categoryRepository.findByUser(user).forEach(categoryRepository::delete);

        // 8. Delete currencies
        log.info("Deleting currencies...");
        currencyRepository.findByUser(user).forEach(currencyRepository::delete);

        log.warn("✅ CLEANUP COMPLETE: All data wiped for user: {}", user.getUsername());
    }
}
