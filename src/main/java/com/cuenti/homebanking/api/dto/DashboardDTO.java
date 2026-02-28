package com.cuenti.homebanking.api.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardDTO {
    private BigDecimal availableCash;
    private BigDecimal portfolioValue;
    private BigDecimal netWorth;
    private String defaultCurrency;
    private List<AccountDTO> accounts;
    private List<AssetPerformanceDTO> assetPerformance;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssetPerformanceDTO {
        private String assetName;
        private String assetSymbol;
        private BigDecimal totalUnits;
        private BigDecimal totalCost;
        private BigDecimal currentValue;
        private BigDecimal currentPrice;
        private BigDecimal gainLoss;
        private BigDecimal gainLossPercent;
    }
}
