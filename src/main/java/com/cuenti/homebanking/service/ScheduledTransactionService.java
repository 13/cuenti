package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.Account;
import com.cuenti.homebanking.model.ScheduledTransaction;
import com.cuenti.homebanking.model.Transaction;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.repository.ScheduledTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduledTransactionService {

    private final ScheduledTransactionRepository repository;
    private final TransactionService transactionService;
    private final AccountService accountService;

    public List<ScheduledTransaction> getByUser(User user) {
        return repository.findByUser(user);
    }

    @Transactional
    public ScheduledTransaction save(ScheduledTransaction scheduledTransaction) {
        return repository.save(scheduledTransaction);
    }

    @Transactional
    public void delete(ScheduledTransaction scheduledTransaction) {
        repository.delete(scheduledTransaction);
    }

    @Transactional
    public void post(Long scheduledId) {
        ScheduledTransaction scheduled = repository.findById(scheduledId)
                .orElseThrow(() -> new IllegalArgumentException("Scheduled transaction not found"));

        Account from = scheduled.getFromAccount() != null ? 
                accountService.findById(scheduled.getFromAccount().getId()) : null;
        Account to = scheduled.getToAccount() != null ? 
                accountService.findById(scheduled.getToAccount().getId()) : null;

        Transaction transaction = Transaction.builder()
                .type(scheduled.getType())
                .fromAccount(from)
                .toAccount(to)
                .amount(scheduled.getAmount())
                .payee(scheduled.getPayee())
                .category(scheduled.getCategory())
                .memo(scheduled.getMemo())
                .tags(scheduled.getTags())
                .number(scheduled.getNumber())
                .asset(scheduled.getAsset())
                .units(scheduled.getUnits())
                .transactionDate(scheduled.getNextOccurrence())
                .status(Transaction.TransactionStatus.COMPLETED)
                .build();

        transactionService.saveTransaction(transaction);
        updateToNextOccurrence(scheduled);
    }

    @Transactional
    public void skip(Long scheduledId) {
        ScheduledTransaction scheduled = repository.findById(scheduledId)
                .orElseThrow(() -> new IllegalArgumentException("Scheduled transaction not found"));
        updateToNextOccurrence(scheduled);
    }

    private void updateToNextOccurrence(ScheduledTransaction scheduled) {
        LocalDateTime next = scheduled.getNextOccurrence();
        int value = (scheduled.getRecurrenceValue() != null && scheduled.getRecurrenceValue() > 0) 
                    ? scheduled.getRecurrenceValue() : 1;

        switch (scheduled.getRecurrencePattern()) {
            case DAILY -> next = next.plusDays(value);
            case WEEKLY -> next = next.plusWeeks(value);
            case BI_WEEKLY -> next = next.plusWeeks(2);
            case MONTHLY -> next = next.plusMonths(value);
            case MONTHLY_LAST_DAY -> {
                next = next.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
            }
            case YEARLY -> next = next.plusYears(value);
            case EVERY_FRIDAY -> next = next.with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
            case EVERY_SATURDAY -> next = next.with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
            case EVERY_WEEKDAY -> {
                next = next.plusDays(1);
                while (next.getDayOfWeek() == DayOfWeek.SATURDAY || next.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    next = next.plusDays(1);
                }
            }
        }
        scheduled.setNextOccurrence(next);
        repository.save(scheduled);
    }
}
