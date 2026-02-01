package com.cuenti.homebanking.repository;

import com.cuenti.homebanking.model.Account;
import com.cuenti.homebanking.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Account entity.
 * Provides database access methods for account management.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    
    /**
     * Find all accounts belonging to a user sorted by sortOrder.
     *
     * @param user the user whose accounts to retrieve
     * @return a list of accounts
     */
    List<Account> findByUserOrderBySortOrderAsc(User user);

    List<Account> findByUser(User user);
    
    /**
     * Find an account by account number.
     *
     * @param accountNumber the account number to search for
     * @return an Optional containing the account if found
     */
    Optional<Account> findByAccountNumber(String accountNumber);
    
    /**
     * Check if an account number exists.
     *
     * @param accountNumber the account number to check
     * @return true if the account number exists, false otherwise
     */
    boolean existsByAccountNumber(String accountNumber);
}
