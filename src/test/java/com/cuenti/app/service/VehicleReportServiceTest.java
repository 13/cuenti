package com.cuenti.app.service;

import com.cuenti.app.model.Transaction;
import com.cuenti.app.service.VehicleReportService.FuelEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleReportServiceTest {

    private Transaction fuelTx(String memo, String payee, String amount) {
        return Transaction.builder()
                .type(Transaction.TransactionType.EXPENSE)
                .amount(new BigDecimal(amount))
                .transactionDate(LocalDateTime.of(2026, 3, 1, 10, 0))
                .payee(payee)
                .memo(memo)
                .build();
    }

    @Test
    void parsesOdometerLitersAndFullTank() {
        FuelEntry entry = VehicleReportService.parseFuelEntry(
                fuelTx("d=45210 l=41.3 full", "Aral", "72.50"), "EUR");
        assertThat(entry.getOdometer()).isEqualByComparingTo("45210");
        assertThat(entry.getLiters()).isEqualByComparingTo("41.3");
        assertThat(entry.isFullTank()).isTrue();
        assertThat(entry.getStation()).isEqualTo("Aral");
    }

    @Test
    void parsesSecondaryKmNotation() {
        FuelEntry entry = VehicleReportService.parseFuelEntry(
                fuelTx("45210 km, 40 l", "Shell", "70.00"), "EUR");
        assertThat(entry.getOdometer()).isEqualByComparingTo("45210");
        assertThat(entry.getLiters()).isEqualByComparingTo("40");
    }

    @Test
    void consumptionComputedBetweenFullTanks() {
        FuelEntry first = VehicleReportService.parseFuelEntry(
                fuelTx("d=1000 l=40 full", "A", "60"), "EUR");
        first.setDate(LocalDate.of(2026, 1, 1));
        FuelEntry second = VehicleReportService.parseFuelEntry(
                fuelTx("d=1500 l=35 full", "A", "55"), "EUR");
        second.setDate(LocalDate.of(2026, 2, 1));

        BigDecimal[] attributed = VehicleReportService.computeDerivedValues(List.of(first, second));

        // 35 liters over 500 km => 7.00 l/100km
        assertThat(second.getConsumption()).isEqualByComparingTo("7.00");
        assertThat(attributed[0]).isEqualByComparingTo("35"); // liters
        assertThat(attributed[1]).isEqualByComparingTo("500"); // distance
    }

    @Test
    void fallbackTreatsEveryFillAsFullWhenNoneFlagged() {
        FuelEntry first = VehicleReportService.parseFuelEntry(fuelTx("d=1000 l=40", "A", "60"), "EUR");
        FuelEntry second = VehicleReportService.parseFuelEntry(fuelTx("d=1400 l=28", "A", "45"), "EUR");

        VehicleReportService.computeDerivedValues(List.of(first, second));
        assertThat(second.getConsumption()).isEqualByComparingTo("7.00");
    }
}
