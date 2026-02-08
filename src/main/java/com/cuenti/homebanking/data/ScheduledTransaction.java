package com.cuenti.homebanking.data;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity for scheduled recurring transactions.
 */
@Entity
@Table(name = "scheduled_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Transaction.TransactionType type;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "from_account_id")
    private Account fromAccount;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "to_account_id")
    private Account toAccount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    private String payee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    private String memo;
    private String tags;
    private String number;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Column(precision = 19, scale = 8)
    private BigDecimal units;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurrencePattern recurrencePattern;

    private Integer recurrenceValue; // e.g. every 2 months

    @Column(nullable = false)
    private LocalDateTime nextOccurrence;

    @Builder.Default
    private boolean enabled = true;

    public enum RecurrencePattern {
        DAILY,
        WEEKLY,
        MONTHLY,
        MONTHLY_LAST_DAY,
        YEARLY,
        EVERY_FRIDAY,
        EVERY_SATURDAY,
        EVERY_WEEKDAY,
        BI_WEEKLY
    }
}
