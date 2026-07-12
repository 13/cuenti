package com.cuenti.app.service;

import com.cuenti.app.api.dto.ForecastDTO;
import com.cuenti.app.model.Account;
import com.cuenti.app.model.ScheduledTransaction;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ForecastServiceTest {

    private ScheduledTransactionService scheduledService;
    private AccountService accountService;
    private ExchangeRateService exchangeRateService;
    private ForecastService forecastService;

    private final User user = new User();
    private final Account account = new Account();

    @BeforeEach
    void setUp() {
        scheduledService = mock(ScheduledTransactionService.class);
        accountService = mock(AccountService.class);
        exchangeRateService = mock(ExchangeRateService.class);
        forecastService = new ForecastService(scheduledService, accountService, exchangeRateService);

        user.setId(1L);
        user.setDefaultCurrency("EUR");
        account.setId(10L);
        account.setCurrency("EUR");
        account.setExcludeFromReports(false);
        // identity conversion
        when(exchangeRateService.convert(any(BigDecimal.class), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(accountService.getAccountsByUser(user)).thenReturn(List.of(account));
    }

    private ScheduledTransaction monthlyExpense(BigDecimal amount, LocalDateTime next) {
        ScheduledTransaction st = new ScheduledTransaction();
        st.setEnabled(true);
        st.setType(Transaction.TransactionType.EXPENSE);
        st.setFromAccount(account);
        st.setAmount(amount);
        st.setRecurrencePattern(ScheduledTransaction.RecurrencePattern.MONTHLY);
        st.setRecurrenceValue(1);
        st.setNextOccurrence(next);
        return st;
    }

    @Test
    void monthlyExpenseAppearsInEveryMonthFromItsStart() {
        when(scheduledService.getByUser(user)).thenReturn(List.of(
                monthlyExpense(new BigDecimal("100"), LocalDateTime.of(2026, 3, 5, 0, 0))));

        ForecastDTO forecast = forecastService.getForecast(user, 2026);

        assertThat(forecast.getYear()).isEqualTo(2026);
        assertThat(forecast.getMonths()).hasSize(12);
        assertThat(forecast.getMonths().get(0).getExpense()).isEqualByComparingTo("0"); // January
        assertThat(forecast.getMonths().get(2).getExpense()).isEqualByComparingTo("100"); // March
        assertThat(forecast.getMonths().get(11).getExpense()).isEqualByComparingTo("100"); // December
        assertThat(forecast.getTotalExpense()).isEqualByComparingTo("1000"); // Mar..Dec = 10 months
        assertThat(forecast.getNetForecast()).isEqualByComparingTo("-1000");
        assertThat(forecast.getCurrency()).isEqualTo("EUR");
    }

    @Test
    void disabledAndTransferSchedulesIgnored() {
        ScheduledTransaction disabled = monthlyExpense(new BigDecimal("50"), LocalDateTime.of(2026, 1, 1, 0, 0));
        disabled.setEnabled(false);
        ScheduledTransaction transfer = monthlyExpense(new BigDecimal("50"), LocalDateTime.of(2026, 1, 1, 0, 0));
        transfer.setType(Transaction.TransactionType.TRANSFER);
        when(scheduledService.getByUser(user)).thenReturn(List.of(disabled, transfer));

        ForecastDTO forecast = forecastService.getForecast(user, 2026);
        assertThat(forecast.getTotalExpense()).isEqualByComparingTo("0");
        assertThat(forecast.getTotalIncome()).isEqualByComparingTo("0");
    }

    @Test
    void excludedAccountIgnored() {
        account.setExcludeFromReports(true);
        when(scheduledService.getByUser(user)).thenReturn(List.of(
                monthlyExpense(new BigDecimal("100"), LocalDateTime.of(2026, 1, 5, 0, 0))));

        ForecastDTO forecast = forecastService.getForecast(user, 2026);
        assertThat(forecast.getTotalExpense()).isEqualByComparingTo("0");
    }
}
