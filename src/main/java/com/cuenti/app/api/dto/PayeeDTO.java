package com.cuenti.app.api.dto;

import com.cuenti.app.model.Transaction;
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
