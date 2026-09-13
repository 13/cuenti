package com.cuenti.app.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction entity representing money movement.
 * Can be an expense, income, or transfer.
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"fromAccount", "toAccount", "asset", "category"})
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id")
    private Account fromAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id")
    private Account toAccount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, columnDefinition = "TIMESTAMP")
    @Builder.Default
    private LocalDateTime transactionDate = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.COMPLETED;

    private String payee;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;
    
    private String memo;
    private String tags;
    private String number;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.NONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Column(precision = 19, scale = 8)
    private BigDecimal units;

    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private java.util.List<TransactionSplit> splits = new java.util.ArrayList<>();

    /**
     * When this row was last written through the service, truncated to
     * milliseconds so it survives a database round trip unchanged. API
     * clients see it as an opaque {@link #version()} and echo it back in
     * {@code If-Match}, so a change made against an older copy is refused
     * instead of silently overwriting a newer one. Null for rows written
     * before the column existed.
     *
     * <p>Stamped explicitly by {@code TransactionService} rather than by
     * {@code @PreUpdate}: that callback fires at flush, which can come after
     * the response was built, handing clients a version the row no longer
     * has. Creates are covered by {@link #touch()} as {@code @PrePersist}.
     */
    @Column(name = "updated_at", columnDefinition = "TIMESTAMP")
    private LocalDateTime updatedAt;

    @PrePersist
    public void touch() {
        updatedAt = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    }

    /** The concurrency token clients send back in {@code If-Match}. */
    public String version() {
        return updatedAt == null
                ? "0"
                : Long.toString(updatedAt.toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
    }

    /**
     * Whether an {@code If-Match} value still describes this row. Absent or
     * {@code *} always matches, so clients that do not send the header keep
     * last-write-wins. Accepts the value quoted or weak-prefixed, the way
     * HTTP entity tags are usually echoed.
     */
    public boolean matchesVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) return true;
        String value = ifMatch.trim();
        if (value.equals("*")) return true;
        if (value.startsWith("W/")) value = value.substring(2);
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.equals(version());
    }

    public void addSplit(TransactionSplit split) {
        splits.add(split);
        split.setTransaction(this);
    }

    public void removeSplit(TransactionSplit split) {
        splits.remove(split);
        split.setTransaction(null);
    }

    public enum TransactionType {
        EXPENSE,
        INCOME,
        TRANSFER
    }

    public enum TransactionStatus {
        PENDING,
        COMPLETED,
        FAILED
    }

    public enum PaymentMethod {
        NONE(""),
        DEBIT_CARD("Debit Card"),
        CASH("Cash"),
        BANK_TRANSFER("Bank Transfer"),
        STANDING_ORDER("Standing Order"),
        ELECTRONIC_PAYMENT("Electronic Payment"),
        FI_FEE("FI Fee"),
        CARD_TRANSACTION("Transazione con carta"),
        TRADE("Commercio"),
        TRANSFER("Bonifico"),
        REWARD("Premio"),
        INTEREST("Interessi");

        private final String label;

        PaymentMethod(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
        
        public static PaymentMethod fromLabel(String label) {
            for (PaymentMethod pm : values()) {
                if (pm.label.equalsIgnoreCase(label) || pm.name().equalsIgnoreCase(label)) {
                    return pm;
                }
            }
            return NONE;
        }
    }
}
