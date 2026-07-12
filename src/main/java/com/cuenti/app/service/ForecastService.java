package com.cuenti.app.service;

import com.cuenti.app.api.dto.ForecastDTO;
import com.cuenti.app.model.Account;
import com.cuenti.app.model.ScheduledTransaction;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Projects scheduled transactions across a calendar year: per-month income
 * and expense in the user's default currency. Transfers are ignored, as are
 * schedules on accounts excluded from reports.
 */
@Service
@RequiredArgsConstructor
public class ForecastService {

    private final ScheduledTransactionService scheduledService;
    private final AccountService accountService;
    private final ExchangeRateService exchangeRateService;

    @Transactional(readOnly = true)
    public ForecastDTO getForecast(User user, int year) {
        Set<Long> reportableAccountIds = accountService.getAccountsByUser(user).stream()
                .filter(a -> !a.isExcludeFromReports())
                .map(Account::getId)
                .collect(Collectors.toSet());

        Map<String, BigDecimal> monthlyIncomes = new TreeMap<>();
        Map<String, BigDecimal> monthlyExpenses = new TreeMap<>();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;

        for (ScheduledTransaction st : scheduledService.getByUser(user)) {
            if (!st.isEnabled()) continue;

            Account fromAccount = st.getFromAccount();
            Account toAccount = st.getToAccount();

            if (st.getType() == Transaction.TransactionType.INCOME) {
                if (toAccount == null || !reportableAccountIds.contains(toAccount.getId())) continue;
            } else if (st.getType() == Transaction.TransactionType.EXPENSE) {
                if (fromAccount == null || !reportableAccountIds.contains(fromAccount.getId())) continue;
            } else {
                continue; // transfers ignored, matching the web view
            }

            LocalDateTime occurrence = st.getNextOccurrence();
            LocalDate occurrenceDate = occurrence.toLocalDate();

            while (occurrenceDate.getYear() < year) {
                occurrence = ScheduledTransactionService.advanceOccurrence(occurrence, st);
                occurrenceDate = occurrence.toLocalDate();
            }

            while (occurrenceDate.getYear() == year) {
                String monthKey = String.format("%d-%02d", occurrenceDate.getYear(), occurrenceDate.getMonthValue());

                if (st.getType() == Transaction.TransactionType.INCOME) {
                    String currency = toAccount != null ? toAccount.getCurrency() : user.getDefaultCurrency();
                    BigDecimal converted = exchangeRateService.convert(st.getAmount(), currency, user.getDefaultCurrency());
                    monthlyIncomes.merge(monthKey, converted, BigDecimal::add);
                    totalIncome = totalIncome.add(converted);
                } else {
                    String currency = fromAccount != null ? fromAccount.getCurrency() : user.getDefaultCurrency();
                    BigDecimal converted = exchangeRateService.convert(st.getAmount(), currency, user.getDefaultCurrency());
                    monthlyExpenses.merge(monthKey, converted, BigDecimal::add);
                    totalExpense = totalExpense.add(converted);
                }

                occurrence = ScheduledTransactionService.advanceOccurrence(occurrence, st);
                occurrenceDate = occurrence.toLocalDate();
            }
        }

        List<ForecastDTO.MonthForecast> months = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            String key = String.format("%d-%02d", year, m);
            BigDecimal income = monthlyIncomes.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal expense = monthlyExpenses.getOrDefault(key, BigDecimal.ZERO);
            months.add(ForecastDTO.MonthForecast.builder()
                    .month(key)
                    .income(income)
                    .expense(expense)
                    .net(income.subtract(expense))
                    .build());
        }

        return ForecastDTO.builder()
                .year(year)
                .months(months)
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netForecast(totalIncome.subtract(totalExpense))
                .currency(user.getDefaultCurrency())
                .build();
    }
}
