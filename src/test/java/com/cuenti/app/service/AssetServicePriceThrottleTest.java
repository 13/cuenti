package com.cuenti.app.service;

import com.cuenti.app.usecase.UseCase;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the per-user asset price update throttle that backs the
 * navigation-triggered async refresh.
 */
class AssetServicePriceThrottleTest {

    private final AssetService service = new AssetService(null, null, null, null);

    @Test
    @UseCase(id = "UC-103", scenario = "Price refresh throttled per user")
    void firstCallIsDue_secondCallWithinWindowIsThrottled() {
        Instant now = Instant.now();

        assertTrue(service.markPriceUpdateDue(1L, now), "first call must run");
        assertFalse(service.markPriceUpdateDue(1L, now.plusSeconds(60)), "call within window must be throttled");
        assertFalse(service.markPriceUpdateDue(1L, now.plus(Duration.ofMinutes(14))), "still inside window");
        assertTrue(service.markPriceUpdateDue(1L, now.plus(Duration.ofMinutes(16))), "after window must run again");
    }

    @Test
    @UseCase(id = "UC-103", scenario = "Throttle is independent per user")
    void throttleIsPerUser() {
        Instant now = Instant.now();

        assertTrue(service.markPriceUpdateDue(10L, now));
        assertTrue(service.markPriceUpdateDue(11L, now), "different user must not be throttled");
        assertFalse(service.markPriceUpdateDue(10L, now.plusSeconds(1)));
    }
}
