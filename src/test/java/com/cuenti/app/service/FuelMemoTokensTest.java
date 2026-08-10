package com.cuenti.app.service;

import com.cuenti.app.service.VehicleReportService.FuelTokens;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip parsing/building of the fuel memo format used by the
 * structured fuel entry form: "d=<km> l=<liters> [full] <free text>".
 */
class FuelMemoTokensTest {

    @Test
    void parsesTokensAndPreservesRemainderText() {
        FuelTokens t = VehicleReportService.parseFuelTokens("d=45210 l=41.3 full Aral Autobahn");
        assertThat(t.odometer()).isEqualByComparingTo("45210");
        assertThat(t.liters()).isEqualByComparingTo("41.3");
        assertThat(t.fullTank()).isTrue();
        assertThat(t.remainderText()).isEqualTo("Aral Autobahn");
        assertThat(t.hasFuelData()).isTrue();
    }

    @Test
    void nullAndEmptyMemoYieldEmptyTokens() {
        FuelTokens t = VehicleReportService.parseFuelTokens(null);
        assertThat(t.odometer()).isNull();
        assertThat(t.liters()).isNull();
        assertThat(t.fullTank()).isFalse();
        assertThat(t.remainderText()).isEmpty();
        assertThat(t.hasFuelData()).isFalse();
    }

    @Test
    void parsesLegacySecondaryNotation() {
        FuelTokens t = VehicleReportService.parseFuelTokens("45210 km 40 l");
        assertThat(t.odometer()).isEqualByComparingTo("45210");
        assertThat(t.liters()).isEqualByComparingTo("40");
        assertThat(t.fullTank()).isFalse();
    }

    @Test
    void buildsCanonicalMemo() {
        String memo = VehicleReportService.buildFuelMemo(
                new BigDecimal("45210"), new BigDecimal("41.3"), true, "Aral");
        assertThat(memo).isEqualTo("d=45210 l=41.3 full Aral");
    }

    @Test
    void buildSkipsMissingParts() {
        assertThat(VehicleReportService.buildFuelMemo(null, new BigDecimal("40"), false, null))
                .isEqualTo("l=40");
        assertThat(VehicleReportService.buildFuelMemo(null, null, false, "just a note"))
                .isEqualTo("just a note");
        assertThat(VehicleReportService.buildFuelMemo(null, null, false, ""))
                .isEmpty();
    }

    @Test
    void roundTripIsStable() {
        String built = VehicleReportService.buildFuelMemo(
                new BigDecimal("100500"), new BigDecimal("38.5"), true, "Shell");
        FuelTokens t = VehicleReportService.parseFuelTokens(built);
        assertThat(t.odometer()).isEqualByComparingTo("100500");
        assertThat(t.liters()).isEqualByComparingTo("38.5");
        assertThat(t.fullTank()).isTrue();
        assertThat(t.remainderText()).isEqualTo("Shell");
        assertThat(VehicleReportService.buildFuelMemo(
                t.odometer(), t.liters(), t.fullTank(), t.remainderText())).isEqualTo(built);
    }
}
