package com.cuenti.homebanking.repository;

import com.cuenti.homebanking.model.Account;
import com.cuenti.homebanking.model.Transaction;
import com.cuenti.homebanking.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Transaction entity.
 * Provides database access methods for transaction management.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    /**
     * Find all transactions for a specific account (both incoming and outgoing).
     * Uses JOIN FETCH to avoid LazyInitializationException in the UI.
     */
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN FETCH t.fromAccount " +
           "LEFT JOIN FETCH t.toAccount " +
           "LEFT JOIN FETCH t.category c " +
           "LEFT JOIN FETCH c.parent " +
           "LEFT JOIN FETCH t.asset " +
           "WHERE t.fromAccount = :account OR t.toAccount = :account " +
           "ORDER BY t.transactionDate DESC, t.sortOrder DESC")
    List<Transaction> findByAccount(@Param("account") Account account);

    /**
     * Find all transactions for a specific user.
     * Uses JOIN FETCH to avoid LazyInitializationException in the UI.
     */
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN FETCH t.fromAccount " +
           "LEFT JOIN FETCH t.toAccount " +
           "LEFT JOIN FETCH t.category c " +
           "LEFT JOIN FETCH c.parent " +
           "LEFT JOIN FETCH t.asset " +
           "WHERE (t.fromAccount.user = :user OR t.toAccount.user = :user) " +
           "ORDER BY t.transactionDate DESC, t.sortOrder DESC")
    List<Transaction> findByUser(@Param("user") User user);

    List<Transaction> findByFromAccountOrderByTransactionDateDesc(Account fromAccount);
    
    List<Transaction> findByToAccountOrderByTransactionDateDesc(Account toAccount);

    /**
     * Count transactions that reference a specific asset.
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.asset = :asset")
    long countByAsset(@Param("asset") com.cuenti.homebanking.model.Asset asset);
}
