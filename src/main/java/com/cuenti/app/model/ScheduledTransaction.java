package com.cuenti.app.model;

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
    @Column(length = 2000)
    private String tags;
    private String number;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Transaction.PaymentMethod paymentMethod = Transaction.PaymentMethod.NONE;

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

    /** Last date an occurrence may fall on; the schedule switches off after it (null = open-ended). */
    private java.time.LocalDate endDate;

    /** Occurrences still to post, counted down on post/skip; the schedule switches off at 0 (null = unlimited). */
    private Integer remainingOccurrences;

    /** Schedules are date-based; drop any time-of-day so due/overdue checks agree within a day. */
    @PrePersist
    @PreUpdate
    void truncateNextOccurrence() {
        if (nextOccurrence != null) {
            nextOccurrence = nextOccurrence.toLocalDate().atStartOfDay();
        }
    }

    public enum RecurrencePattern {
        DAILY,
        WEEKLY,
        MONTHLY,
        MONTHLY_LAST_DAY,
        YEARLY,
        /** @deprecated legacy; migrated to WEEKLY (value 1) on a Friday. Kept so old imports parse. */
        @Deprecated EVERY_FRIDAY,
        /** @deprecated legacy; migrated to WEEKLY (value 1) on a Saturday. Kept so old imports parse. */
        @Deprecated EVERY_SATURDAY,
        EVERY_WEEKDAY,
        /** @deprecated legacy; migrated to WEEKLY with value 2. Kept so old imports parse. */
        @Deprecated BI_WEEKLY;

        /** Patterns offered when creating or editing a schedule. */
        public static final java.util.List<RecurrencePattern> SELECTABLE =
                java.util.List.of(DAILY, WEEKLY, MONTHLY, MONTHLY_LAST_DAY, YEARLY, EVERY_WEEKDAY);

        /** Whether "every N" applies to this pattern. */
        public boolean hasInterval() {
            return this != EVERY_WEEKDAY && this != EVERY_FRIDAY && this != EVERY_SATURDAY && this != BI_WEEKLY;
        }
    }
}
