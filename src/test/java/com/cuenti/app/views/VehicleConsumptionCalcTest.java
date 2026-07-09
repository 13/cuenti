package com.cuenti.app.views;

import com.cuenti.app.usecase.UseCase;
import com.cuenti.app.views.VehiclesView.FuelEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fuel consumption math: full-tank to full-tank attribution, partial fills
 * accumulated in between, fill-to-fill fallback when nothing is flagged.
 */
class VehicleConsumptionCalcTest {

    private static FuelEntry entry(int day, int odometer, double liters, double amount, boolean fullTank) {
        FuelEntry e = new FuelEntry(LocalDate.of(2026, 1, day),
                BigDecimal.valueOf(odometer), BigDecimal.valueOf(liters),
                BigDecimal.valueOf(amount), "EUR", "Station", null);
        e.fullTank = fullTank;
        return e;
    }

    @Test
    @UseCase(id = "UC-105", scenario = "Full-tank to full-tank consumption")
    void partialFillAccumulatesUntilNextFullTank() {
        FuelEntry first = entry(1, 10000, 40, 60, true);
        FuelEntry partial = entry(5, 10400, 20, 30, false);   // partial: no consumption of its own
        FuelEntry second = entry(9, 11000, 35, 55, true);     // full: closes the window

        List<FuelEntry> entries = List.of(first, partial, second);
        BigDecimal[] attributed = VehiclesView.computeDerivedValues(entries);

        assertThat(partial.consumption).isNull();
        // 20 + 35 liters over 1000 km = 5.50 L/100km
        assertThat(second.consumption).isEqualByComparingTo("5.50");
        assertThat(attributed[0]).isEqualByComparingTo("55");   // liters
        assertThat(attributed[1]).isEqualByComparingTo("1000"); // distance
    }

    @Test
    @UseCase(id = "UC-105", scenario = "Fallback when no full-tank flags")
    void withoutFullTankFlags_everyFillMeasures() {
        FuelEntry a = entry(1, 20000, 40, 60, false);
        FuelEntry b = entry(8, 20500, 30, 45, false);

        VehiclesView.computeDerivedValues(List.of(a, b));

        // 30 liters over 500 km = 6.00 L/100km (classic fill-to-fill)
        assertThat(b.consumption).isEqualByComparingTo("6.00");
        assertThat(a.consumption).isNull(); // first fill has no baseline
    }

    @Test
    @UseCase(id = "UC-105", scenario = "Price per liter per entry")
    void pricePerLiterComputedPerEntry() {
        FuelEntry a = entry(1, 30000, 40, 62, true);
        VehiclesView.computeDerivedValues(List.of(a));
        assertThat(a.pricePerLiter).isEqualByComparingTo("1.550");
    }
}
