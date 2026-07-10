package com.cuenti.app.service;

import com.cuenti.app.model.Account;
import com.cuenti.app.model.ScheduledTransaction;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import com.cuenti.app.repository.ScheduledTransactionRepository;
import com.cuenti.app.security.SecurityUtils;
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
    private final UserService userService;

    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    public List<ScheduledTransaction> getByUser(User user) {
        return repository.findByUser(user);
    }

    @Transactional
    public ScheduledTransaction save(ScheduledTransaction scheduledTransaction) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        // If it's a new scheduled transaction, set the user
        if (scheduledTransaction.getId() == null) {
            scheduledTransaction.setUser(currentUser);
        } else {
            // If updating, verify the user owns it
            ScheduledTransaction existing = repository.findById(scheduledTransaction.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Scheduled transaction not found"));
            if (!existing.getUser().getId().equals(currentUser.getId())) {
                throw new SecurityException("Cannot modify scheduled transaction belonging to another user");
            }
        }
        boolean created = scheduledTransaction.getId() == null;
        ScheduledTransaction saved = repository.save(scheduledTransaction);
        auditService.log(currentUser, created ? "CREATE" : "UPDATE", "ScheduledTransaction",
                saved.getId(), saved.getPayee());
        return saved;
    }

    @Transactional
    public void delete(ScheduledTransaction scheduledTransaction) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        // Security check: only allow deletion if scheduled transaction belongs to current user
        if (scheduledTransaction.getUser().getId().equals(currentUser.getId())) {
            repository.delete(scheduledTransaction);
            auditService.log(currentUser, "DELETE", "ScheduledTransaction",
                    scheduledTransaction.getId(), scheduledTransaction.getPayee());
        } else {
            throw new SecurityException("Cannot delete scheduled transaction belonging to another user");
        }
    }

    @Transactional
    public void post(Long scheduledId) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        ScheduledTransaction scheduled = repository.findById(scheduledId)
                .orElseThrow(() -> new IllegalArgumentException("Scheduled transaction not found"));

        // Security check: verify the user owns this scheduled transaction
        if (!scheduled.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Cannot post scheduled transaction belonging to another user");
        }

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
                .paymentMethod(scheduled.getPaymentMethod() != null ? scheduled.getPaymentMethod() : Transaction.PaymentMethod.NONE)
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
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        ScheduledTransaction scheduled = repository.findById(scheduledId)
                .orElseThrow(() -> new IllegalArgumentException("Scheduled transaction not found"));

        // Security check: verify the user owns this scheduled transaction
        if (!scheduled.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Cannot skip scheduled transaction belonging to another user");
        }

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

    /** Enabled schedules due within the next 7 days (nav badge). */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public long countDueSoon(com.cuenti.app.model.User user) {
        return getByUser(user).stream()
                .filter(com.cuenti.app.model.ScheduledTransaction::isEnabled)
                .filter(st -> st.getNextOccurrence() != null
                        && st.getNextOccurrence().isBefore(java.time.LocalDateTime.now().plusDays(7)))
                .count();
    }
}
