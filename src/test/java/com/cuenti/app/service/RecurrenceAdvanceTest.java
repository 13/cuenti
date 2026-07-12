package com.cuenti.app.service;

import com.cuenti.app.model.ScheduledTransaction;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RecurrenceAdvanceTest {

    private ScheduledTransaction scheduled(ScheduledTransaction.RecurrencePattern pattern, Integer value) {
        ScheduledTransaction st = new ScheduledTransaction();
        st.setRecurrencePattern(pattern);
        st.setRecurrenceValue(value);
        return st;
    }

    @Test
    void monthlyAdvancesByValue() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 15, 12, 0);
        LocalDateTime next = ScheduledTransactionService.advanceOccurrence(
                start, scheduled(ScheduledTransaction.RecurrencePattern.MONTHLY, 2));
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 3, 15, 12, 0));
    }

    @Test
    void nullRecurrenceValueDefaultsToOne() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime next = ScheduledTransactionService.advanceOccurrence(
                start, scheduled(ScheduledTransaction.RecurrencePattern.DAILY, null));
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 1, 2, 0, 0));
    }

    @Test
    void monthlyLastDaySnapsToEndOfMonth() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 31, 8, 0);
        LocalDateTime next = ScheduledTransactionService.advanceOccurrence(
                start, scheduled(ScheduledTransaction.RecurrencePattern.MONTHLY_LAST_DAY, 1));
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 2, 28, 8, 0));
    }

    @Test
    void everyWeekdaySkipsWeekend() {
        LocalDateTime friday = LocalDateTime.of(2026, 7, 10, 9, 0); // a Friday
        LocalDateTime next = ScheduledTransactionService.advanceOccurrence(
                friday, scheduled(ScheduledTransaction.RecurrencePattern.EVERY_WEEKDAY, 1));
        assertThat(next.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }
}
