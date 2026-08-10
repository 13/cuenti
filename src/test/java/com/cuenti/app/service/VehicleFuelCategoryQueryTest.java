package com.cuenti.app.service;

import com.cuenti.app.model.Category;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Category fuel detection and last-known odometer lookup used by the
 * transaction form's fuel section.
 */
@ExtendWith(MockitoExtension.class)
class VehicleFuelCategoryQueryTest {

    @Mock
    private TransactionService transactionService;

    @Mock
    private ExchangeRateService exchangeRateService;

    @Mock
    private User user;

    @InjectMocks
    private VehicleReportService service;

    private Transaction tx(Long categoryId, String memo, LocalDateTime date) {
        Category cat = mock(Category.class);
        when(cat.getId()).thenReturn(categoryId);
        return Transaction.builder()
                .type(Transaction.TransactionType.EXPENSE)
                .amount(new BigDecimal("50"))
                .transactionDate(date)
                .category(cat)
                .memo(memo)
                .build();
    }

    @Test
    void categoryWithFuelMemosIsFuelCategory() {
        var tx1 = tx(5L, "d=45210 l=41.3 full", LocalDateTime.of(2026, 7, 1, 10, 0));
        var tx2 = tx(7L, "groceries", LocalDateTime.of(2026, 7, 2, 10, 0));
        when(transactionService.getTransactionsByUser(user)).thenReturn(List.of(tx1, tx2));

        assertThat(service.isFuelCategory(user, 5L)).isTrue();
        assertThat(service.isFuelCategory(user, 7L)).isFalse();
    }

    @Test
    void lastOdometerPicksLatestReadingBeforeDate() {
        var tx1 = tx(5L, "d=44000 l=40", LocalDateTime.of(2026, 6, 1, 10, 0));
        var tx2 = tx(5L, "d=44870 l=38", LocalDateTime.of(2026, 7, 1, 10, 0));
        var tx3 = tx(5L, "d=45500 l=41", LocalDateTime.of(2026, 8, 1, 10, 0));
        var tx4 = tx(5L, "no odometer here l=20", LocalDateTime.of(2026, 7, 15, 10, 0));
        when(transactionService.getTransactionsByUser(user)).thenReturn(List.of(tx1, tx2, tx3, tx4));

        assertThat(service.lastOdometer(user, 5L, LocalDate.of(2026, 7, 20)))
                .isEqualByComparingTo("44870");
    }

    @Test
    void lastOdometerNullWhenNoPriorReading() {
        var tx1 = tx(5L, "d=44000 l=40", LocalDateTime.of(2026, 6, 1, 10, 0));
        when(transactionService.getTransactionsByUser(user)).thenReturn(List.of(tx1));

        assertThat(service.lastOdometer(user, 5L, LocalDate.of(2026, 6, 1))).isNull();
        assertThat(service.lastOdometer(user, 9L, LocalDate.of(2026, 12, 1))).isNull();
    }
}
