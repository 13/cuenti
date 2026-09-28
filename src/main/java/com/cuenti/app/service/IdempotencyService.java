package com.cuenti.app.service;

import com.cuenti.app.model.IdempotencyRecord;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.repository.IdempotencyRecordRepository;
import com.cuenti.app.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Creates a transaction at most once per client-supplied key.
 *
 * <p>The transaction and its key record are written in one database
 * transaction. Two requests racing with the same key both try to insert the
 * record; the unique constraint lets exactly one commit, the other rolls back
 * its transaction row along with its record, and the caller answers it with
 * the winner's result.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    /** Longest key accepted. Mobile clients send their local queue id. */
    public static final int MAX_KEY_LENGTH = 100;

    private final IdempotencyRecordRepository records;
    private final TransactionRepository transactions;
    private final TransactionService transactionService;

    /** A key seen before, and the transaction it created if that still exists. */
    public record Replay(Optional<Transaction> transaction) {}

    @Transactional(readOnly = true)
    public Optional<Replay> find(Long userId, String key) {
        return records.findByUserIdAndIdemKey(userId, key)
                .map(record -> new Replay(transactions.findById(record.getTransactionId())));
    }

    @Transactional
    public Transaction createOnce(Long userId, String key, Transaction transaction) {
        Transaction saved = transactionService.saveTransaction(transaction);
        records.saveAndFlush(IdempotencyRecord.builder()
                .userId(userId)
                .idemKey(key)
                .transactionId(saved.getId())
                .createdAt(LocalDateTime.now())
                .build());
        return saved;
    }

    /**
     * {@link TransactionService#updateTransaction(Long, String, java.util.function.Consumer)}
     * at most once per key, for the same reason as {@link #createOnce}: an update
     * whose answer was lost is resent, and a second application would be refused
     * as stale -- the first one moved the version on -- and reported as a conflict
     * with an edit that was in fact applied.
     */
    @Transactional
    public Transaction updateOnce(Long userId, String key, Long id, String ifMatch,
                                  java.util.function.Consumer<Transaction> mutator) {
        Transaction saved = transactionService.updateTransaction(id, ifMatch, mutator);
        records.saveAndFlush(IdempotencyRecord.builder()
                .userId(userId)
                .idemKey(key)
                .transactionId(saved.getId())
                .createdAt(LocalDateTime.now())
                .build());
        return saved;
    }
}
