package com.cuenti.app.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Remembers which transaction a client's {@code Idempotency-Key} created, so
 * a retried POST -- the response to the first one lost on a flaky connection
 * -- returns that transaction instead of creating a duplicate.
 */
@Entity
@Table(name = "idempotency_keys", uniqueConstraints = @UniqueConstraint(
        name = "uk_idempotency_user_key", columnNames = {"user_id", "idem_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "idem_key", nullable = false, length = 100)
    private String idemKey;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP")
    private LocalDateTime createdAt;
}
