package com.cuenti.app.repository;

import com.cuenti.app.model.Account;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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

    /**
     * Filtered window for the transaction grid: account/type/date pushed to
     * the database so the UI no longer loads the full history.
     */
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN FETCH t.fromAccount " +
           "LEFT JOIN FETCH t.toAccount " +
           "LEFT JOIN FETCH t.category c " +
           "LEFT JOIN FETCH c.parent " +
           "LEFT JOIN FETCH t.asset " +
           "WHERE (t.fromAccount.user = :user OR t.toAccount.user = :user) " +
           "AND (:account IS NULL OR t.fromAccount = :account OR t.toAccount = :account) " +
           "AND (:type IS NULL OR t.type = :type) " +
           "AND t.transactionDate >= :from AND t.transactionDate <= :to " +
           "ORDER BY t.transactionDate DESC, t.sortOrder DESC")
    List<Transaction> findFiltered(@Param("user") User user,
                                   @Param("account") Account account,
                                   @Param("type") Transaction.TransactionType type,
                                   @Param("from") java.time.LocalDateTime from,
                                   @Param("to") java.time.LocalDateTime to);

    /**
     * Running balance over the full user history computed by the database
     * (window function); only rows inside the filter window are returned.
     * Transfers are balance-neutral in the all-accounts view.
     */
    @Query(value = "SELECT w.id, w.bal FROM (" +
            "  SELECT t.id AS id, t.transaction_date AS td, t.type AS ttype, " +
            "         SUM(CASE t.type WHEN 'INCOME' THEN t.amount WHEN 'EXPENSE' THEN -t.amount ELSE 0 END) " +
            "           OVER (ORDER BY t.transaction_date, t.sort_order, t.id) AS bal " +
            "  FROM transactions t " +
            "  LEFT JOIN accounts fa ON fa.id = t.from_account_id " +
            "  LEFT JOIN accounts ta ON ta.id = t.to_account_id " +
            "  WHERE fa.user_id = :userId OR ta.user_id = :userId" +
            ") w " +
            "WHERE w.td >= :from AND w.td <= :to " +
            "AND (:type IS NULL OR w.ttype = :type)",
            nativeQuery = true)
    List<Object[]> runningBalancesForUser(@Param("userId") Long userId,
                                          @Param("from") java.time.LocalDateTime from,
                                          @Param("to") java.time.LocalDateTime to,
                                          @Param("type") String type);

    /**
     * Running balance for one account with per-account transfer semantics.
     */
    @Query(value = "SELECT w.id, w.bal FROM (" +
            "  SELECT t.id AS id, t.transaction_date AS td, t.type AS ttype, " +
            "         SUM(CASE " +
            "               WHEN t.type = 'INCOME'  AND t.to_account_id   = :accountId THEN t.amount " +
            "               WHEN t.type = 'EXPENSE' AND t.from_account_id = :accountId THEN -t.amount " +
            "               WHEN t.type = 'TRANSFER' THEN " +
            "                    (CASE WHEN t.to_account_id   = :accountId THEN t.amount ELSE 0 END) " +
            "                  + (CASE WHEN t.from_account_id = :accountId THEN -t.amount ELSE 0 END) " +
            "               ELSE 0 END) " +
            "           OVER (ORDER BY t.transaction_date, t.sort_order, t.id) AS bal " +
            "  FROM transactions t " +
            "  WHERE t.from_account_id = :accountId OR t.to_account_id = :accountId" +
            ") w " +
            "WHERE w.td >= :from AND w.td <= :to " +
            "AND (:type IS NULL OR w.ttype = :type)",
            nativeQuery = true)
    List<Object[]> runningBalancesForAccount(@Param("accountId") Long accountId,
                                             @Param("from") java.time.LocalDateTime from,
                                             @Param("to") java.time.LocalDateTime to,
                                             @Param("type") String type);

    List<Transaction> findByFromAccountOrderByTransactionDateDesc(Account fromAccount);
    
    List<Transaction> findByToAccountOrderByTransactionDateDesc(Account toAccount);

    Optional<Transaction> findByNumber(String number);

    /**
     * Count transactions that reference a specific asset.
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.asset = :asset")
    long countByAsset(@Param("asset") com.cuenti.app.model.Asset asset);

    /**
     * Update all transactions using a specific category to set category to null.
     */
    @Modifying
    @Query("UPDATE Transaction t SET t.category = null WHERE t.category.id = :categoryId")
    int clearCategoryReferences(@Param("categoryId") Long categoryId);
}
