package com.cuenti.app.api.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleReportDTO {
    private List<FuelEntryDTO> entries;
    private BigDecimal totalCost;
    private BigDecimal totalLiters;
    /**
     * Distance covered between consumption measure points (full-tank to full-tank);
     * partial-fill legs outside measured cycles are not included.
     */
    private BigDecimal totalDistance;
    private BigDecimal avgConsumption;
    private BigDecimal avgPricePerLiter;
    private String currency;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FuelEntryDTO {
        private LocalDate date;
        private BigDecimal odometer;
        private BigDecimal liters;
        private BigDecimal amount;
        private String currency;
        private String station;
        private String memo;
        private boolean fullTank;
        private BigDecimal distance;
        private BigDecimal pricePerLiter;
        private BigDecimal consumption;
    }
}
