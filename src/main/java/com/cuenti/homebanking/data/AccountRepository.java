package com.cuenti.homebanking.data;

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
     */
    List<Account> findByUserOrderBySortOrderAsc(User user);

    List<Account> findByUser(User user);

    /**
     * Find an account by account number.
     */
    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Check if an account number exists.
     */
    boolean existsByAccountNumber(String accountNumber);
}
