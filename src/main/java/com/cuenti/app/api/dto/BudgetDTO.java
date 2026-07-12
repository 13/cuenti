package com.cuenti.app.api.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetDTO {
    private Long id;
    private Long categoryId;
    private String categoryName;
    private BigDecimal monthlyLimit;
    /** Wrapper type: Jackson 3 rejects absent JSON values for primitives on creator-based binding. */
    private Boolean active;
}
