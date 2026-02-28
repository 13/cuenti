package com.cuenti.homebanking.api.dto;

import com.cuenti.homebanking.model.Transaction;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayeeDTO {
    private Long id;
    private String name;
    private String notes;
    private Long defaultCategoryId;
    private String defaultCategoryName;
    private Transaction.PaymentMethod defaultPaymentMethod;
}
