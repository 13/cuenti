package com.cuenti.homebanking.model;

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
