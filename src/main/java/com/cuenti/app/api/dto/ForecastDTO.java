package com.cuenti.app.api.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForecastDTO {
    private int year;
    private List<MonthForecast> months;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netForecast;
    private String currency;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthForecast {
        private String month; // "YYYY-MM"
        private BigDecimal income;
        private BigDecimal expense;
        private BigDecimal net;
    }
}
