package com.cuenti.homebanking.api.dto;

import com.cuenti.homebanking.model.ScheduledTransaction;
import com.cuenti.homebanking.model.Transaction;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledTransactionDTO {
    private Long id;
    private Transaction.TransactionType type;
    private Long fromAccountId;
    private String fromAccountName;
    private Long toAccountId;
    private String toAccountName;
    private BigDecimal amount;
    private String payee;
    private Long categoryId;
    private String categoryName;
    private String memo;
    private String tags;
    private String number;
    private Transaction.PaymentMethod paymentMethod;
    private Long assetId;
    private String assetName;
    private BigDecimal units;
    private ScheduledTransaction.RecurrencePattern recurrencePattern;
    private Integer recurrenceValue;
    private LocalDateTime nextOccurrence;
    private boolean enabled;
}
