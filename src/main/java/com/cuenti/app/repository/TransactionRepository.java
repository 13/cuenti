package com.cuenti.app.repository;

import com.cuenti.app.model.Account;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** Highest sort order among the account's transactions in [from, to). */
    @Query("SELECT MAX(t.sortOrder) FROM Transaction t " +
           "WHERE (t.fromAccount = :account OR t.toAccount = :account) " +
           "AND t.transactionDate >= :from AND t.transactionDate < :to")
    Integer maxSortOrder(@Param("account") Account account,
                         @Param("from") java.time.LocalDateTime from, @Param("to") java.time.LocalDateTime to);

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
     * Raw comma-separated tag strings used on the user's transactions. Tags
     * are stored as text on the transaction, so names can exist here without
     * a matching {@code Tag} row (e.g. after an import).
     */
    @Query("SELECT DISTINCT t.tags FROM Transaction t " +
           "LEFT JOIN t.fromAccount fa " +
           "LEFT JOIN t.toAccount ta " +
           "WHERE (fa.user = :user OR ta.user = :user) " +
           "AND t.tags IS NOT NULL AND t.tags <> ''")
    List<String> findDistinctTagStringsByUser(@Param("user") User user);

    /** Sum of amounts booked out of the account (expenses and complete transfers). */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.fromAccount = :account " +
           "AND (t.type = com.cuenti.app.model.Transaction.TransactionType.EXPENSE " +
           "OR (t.type = com.cuenti.app.model.Transaction.TransactionType.TRANSFER AND t.toAccount IS NOT NULL))")
    java.math.BigDecimal sumOutflow(@Param("account") com.cuenti.app.model.Account account);

    /** Sum of amounts booked into the account (income and complete transfers). */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.toAccount = :account " +
           "AND (t.type = com.cuenti.app.model.Transaction.TransactionType.INCOME " +
           "OR (t.type = com.cuenti.app.model.Transaction.TransactionType.TRANSFER AND t.fromAccount IS NOT NULL))")
    java.math.BigDecimal sumInflow(@Param("account") com.cuenti.app.model.Account account);

    /** The user's transactions whose tag string mentions {@code name} (candidates; callers match exactly). */
    @Query("SELECT t FROM Transaction t " +
           "LEFT JOIN t.fromAccount fa " +
           "LEFT JOIN t.toAccount ta " +
           "WHERE (fa.user = :user OR ta.user = :user) " +
           "AND LOWER(t.tags) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Transaction> findByUserAndTagsContaining(@Param("user") User user, @Param("name") String name);

    /** Tag strings of the user's transactions with this payee (one row per transaction, for counting). */
    @Query("SELECT t.tags FROM Transaction t " +
           "LEFT JOIN t.fromAccount fa " +
           "LEFT JOIN t.toAccount ta " +
           "WHERE (fa.user = :user OR ta.user = :user) " +
           "AND LOWER(t.payee) = LOWER(:payee) " +
           "AND t.tags IS NOT NULL AND t.tags <> ''")
    List<String> findTagStringsByUserAndPayee(@Param("user") User user, @Param("payee") String payee);

    /** Expense totals per category in a period (budget tracking). */
    @Query("SELECT t.category.id, SUM(t.amount) FROM Transaction t " +
           "WHERE (t.fromAccount.user = :user OR t.toAccount.user = :user) " +
           "AND t.type = :expenseType " +
           "AND t.category IS NOT NULL " +
           "AND t.transactionDate >= :from AND t.transactionDate <= :to " +
           "GROUP BY t.category.id")
    List<Object[]> sumExpensesByCategory(@Param("user") User user,
                                         @Param("from") java.time.LocalDateTime from,
                                         @Param("to") java.time.LocalDateTime to,
                                         @Param("expenseType") Transaction.TransactionType expenseType);

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

    /** Whether {@code user} already has a transaction with this number (import deduplication). */
    @Query("SELECT COUNT(t) > 0 FROM Transaction t LEFT JOIN t.fromAccount f LEFT JOIN t.toAccount a " +
           "WHERE t.number = :number AND (f.user = :user OR a.user = :user)")
    boolean existsByNumberForUser(@Param("number") String number, @Param("user") com.cuenti.app.model.User user);

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

    /**
     * Paged, filtered search for the REST API. LEFT JOINs keep transactions
     * with null category/accounts visible; countQuery avoids the fetch-join
     * count problem. Sorting comes from the Pageable.
     *
     * DO NOT remove the {@code CAST(:param AS ...)} wrappers. PostgreSQL cannot
     * infer the type of a bind parameter that appears only in {@code :p IS NULL}
     * or inside {@code CONCAT}, and fails the whole query with
     * "could not determine data type of parameter" (SQLState 42P18). H2 infers
     * these types, so the H2-based test suite passes even without the casts —
     * they were dropped once and broke every transactions request in production
     * on Postgres while all tests stayed green.
     */
    @Query(value = "SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN t.fromAccount fa " +
           "LEFT JOIN t.toAccount ta " +
           "LEFT JOIN t.category c " +
           "WHERE (fa.user = :user OR ta.user = :user) " +
           "AND (:accountId IS NULL OR fa.id = :accountId OR ta.id = :accountId) " +
           "AND (:type IS NULL OR t.type = :type) " +
           "AND (:categoryId IS NULL OR c.id = :categoryId) " +
           "AND (CAST(:from AS timestamp) IS NULL OR t.transactionDate >= :from) " +
           "AND (CAST(:to AS timestamp) IS NULL OR t.transactionDate <= :to) " +
           "AND (CAST(:payee AS string) IS NULL OR LOWER(t.payee) LIKE LOWER(CONCAT('%', CAST(:payee AS string), '%'))) " +
           "AND (CAST(:tag AS string) IS NULL OR LOWER(t.tags) LIKE LOWER(CONCAT('%', CAST(:tag AS string), '%'))) " +
           "AND (CAST(:search AS string) IS NULL " +
           "     OR LOWER(t.payee) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(t.memo) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(t.number) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))",
           countQuery = "SELECT COUNT(DISTINCT t) FROM Transaction t " +
           "LEFT JOIN t.fromAccount fa " +
           "LEFT JOIN t.toAccount ta " +
           "LEFT JOIN t.category c " +
           "WHERE (fa.user = :user OR ta.user = :user) " +
           "AND (:accountId IS NULL OR fa.id = :accountId OR ta.id = :accountId) " +
           "AND (:type IS NULL OR t.type = :type) " +
           "AND (:categoryId IS NULL OR c.id = :categoryId) " +
           "AND (CAST(:from AS timestamp) IS NULL OR t.transactionDate >= :from) " +
           "AND (CAST(:to AS timestamp) IS NULL OR t.transactionDate <= :to) " +
           "AND (CAST(:payee AS string) IS NULL OR LOWER(t.payee) LIKE LOWER(CONCAT('%', CAST(:payee AS string), '%'))) " +
           "AND (CAST(:tag AS string) IS NULL OR LOWER(t.tags) LIKE LOWER(CONCAT('%', CAST(:tag AS string), '%'))) " +
           "AND (CAST(:search AS string) IS NULL " +
           "     OR LOWER(t.payee) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(t.memo) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(t.number) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<Transaction> searchByUser(@Param("user") User user,
                                   @Param("accountId") Long accountId,
                                   @Param("type") Transaction.TransactionType type,
                                   @Param("categoryId") Long categoryId,
                                   @Param("from") java.time.LocalDateTime from,
                                   @Param("to") java.time.LocalDateTime to,
                                   @Param("payee") String payee,
                                   @Param("tag") String tag,
                                   @Param("search") String search,
                                   Pageable pageable);

    /** Booking history of one schedule, newest first. */
    List<Transaction> findByScheduledTransactionIdOrderByTransactionDateDesc(Long scheduledTransactionId);
}
