package com.cuenti.homebanking.api.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrencyDTO {
    private Long id;
    private String code;
    private String name;
    private String symbol;
    private String decimalChar;
    private int fracDigits;
    private String groupingChar;
}
