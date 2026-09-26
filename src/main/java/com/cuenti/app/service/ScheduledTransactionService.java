package com.cuenti.app.service;

import com.cuenti.app.model.Account;
import com.cuenti.app.model.ScheduledTransaction;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import com.cuenti.app.repository.ScheduledTransactionRepository;
import com.cuenti.app.repository.TransactionRepository;
import com.cuenti.app.security.SecurityUtils;
import com.cuenti.app.util.AccountSides;
import com.cuenti.app.util.TagNames;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
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
    private final TransactionRepository transactionRepository;

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
        scheduledTransaction.setTags(TagNames.normalize(scheduledTransaction.getTags()));
        Account[] accounts = accountsForType(scheduledTransaction.getType(),
                scheduledTransaction.getFromAccount(), scheduledTransaction.getToAccount());
        scheduledTransaction.setFromAccount(accounts[0]);
        scheduledTransaction.setToAccount(accounts[1]);
        ScheduledTransaction saved = repository.save(scheduledTransaction);
        auditService.log(currentUser, created ? "CREATE" : "UPDATE", "ScheduledTransaction",
                saved.getId(), saved.getPayee());
        com.cuenti.app.util.ScheduledChangeBroadcaster.broadcast(currentUser.getId());
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
            com.cuenti.app.util.ScheduledChangeBroadcaster.broadcast(currentUser.getId());
        } else {
            throw new SecurityException("Cannot delete scheduled transaction belonging to another user");
        }
    }

    @Transactional
    public void post(Long scheduledId) {
        post(scheduledId, null, null);
    }

    /**
     * Post the current occurrence, optionally overriding amount and booking date
     * for this one transaction (variable bills); the schedule itself is unchanged
     * apart from advancing to its next occurrence.
     */
    @Transactional
    public void post(Long scheduledId, BigDecimal amountOverride, LocalDate dateOverride) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        ScheduledTransaction scheduled = repository.findById(scheduledId)
                .orElseThrow(() -> new IllegalArgumentException("Scheduled transaction not found"));

        // Security check: verify the user owns this scheduled transaction
        if (!scheduled.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Cannot post scheduled transaction belonging to another user");
        }

        Transaction transaction = buildOccurrence(scheduled, amountOverride, dateOverride);
        transactionService.saveTransaction(transaction);
        updateToNextOccurrence(scheduled);
        auditService.log(currentUser, "POST", "ScheduledTransaction",
                scheduled.getId(), scheduled.getPayee());
        com.cuenti.app.util.ScheduledChangeBroadcaster.broadcast(currentUser.getId());
    }

    /** See {@link AccountSides#forType}. */
    public static Account[] accountsForType(Transaction.TransactionType type, Account from, Account to) {
        return AccountSides.forType(type, from, to);
    }

    /** The account a non-transfer schedule books on (for transfers: the source). */
    public static Account primaryAccount(ScheduledTransaction st) {
        if (st.getType() == Transaction.TransactionType.INCOME) {
            return st.getToAccount() != null ? st.getToAccount() : st.getFromAccount();
        }
        return st.getFromAccount() != null ? st.getFromAccount() : st.getToAccount();
    }

    /**
     * The transaction the next occurrence would book, not saved; the caller may edit
     * it (category, tags, account, amount...) and hand it to {@link #postEdited}.
     */
    @Transactional(readOnly = true)
    public Transaction draftOccurrence(Long scheduledId) {
        return buildOccurrence(ownedSchedule(scheduledId), null, null);
    }

    /**
     * Books an edited occurrence (see {@link #draftOccurrence}) and advances the schedule,
     * in one transaction. The schedule itself keeps its own values.
     */
    @Transactional
    public Transaction postEdited(Long scheduledId, Transaction edited) {
        ScheduledTransaction scheduled = ownedSchedule(scheduledId);
        edited.setScheduledTransactionId(scheduled.getId());
        Transaction saved = transactionService.saveTransaction(edited);
        updateToNextOccurrence(scheduled);
        auditService.log(scheduled.getUser(), "POST", "ScheduledTransaction", scheduled.getId(), scheduled.getPayee());
        com.cuenti.app.util.ScheduledChangeBroadcaster.broadcast(scheduled.getUser().getId());
        return saved;
    }

    /**
     * A new, unsaved schedule copying an existing transaction, starting one month after
     * it ("Als Planung speichern").
     */
    public static ScheduledTransaction draftFrom(Transaction t) {
        return ScheduledTransaction.builder()
                .type(t.getType())
                .fromAccount(t.getFromAccount())
                .toAccount(t.getToAccount())
                .amount(t.getAmount())
                .payee(t.getPayee())
                .category(t.getCategory())
                .memo(t.getMemo())
                .tags(t.getTags())
                .number(t.getNumber())
                .paymentMethod(t.getPaymentMethod() != null ? t.getPaymentMethod() : Transaction.PaymentMethod.NONE)
                .recurrencePattern(ScheduledTransaction.RecurrencePattern.MONTHLY)
                .recurrenceValue(1)
                .nextOccurrence((t.getTransactionDate() != null ? t.getTransactionDate() : LocalDateTime.now())
                        .toLocalDate().plusMonths(1).atStartOfDay())
                .enabled(true)
                .build();
    }

    private Transaction buildOccurrence(ScheduledTransaction scheduled, BigDecimal amountOverride, LocalDate dateOverride) {
        // Older schedules (and API/import clients) may keep an income's account in "from";
        // the ledger credits income to "to", so place it on the side the type expects.
        Account[] accounts = accountsForType(scheduled.getType(),
                scheduled.getFromAccount() != null ? accountService.findById(scheduled.getFromAccount().getId()) : null,
                scheduled.getToAccount() != null ? accountService.findById(scheduled.getToAccount().getId()) : null);
        return Transaction.builder()
                .type(scheduled.getType())
                .fromAccount(accounts[0])
                .toAccount(accounts[1])
                .amount(amountOverride != null ? amountOverride : scheduled.getAmount())
                .payee(scheduled.getPayee())
                .category(scheduled.getCategory())
                .memo(scheduled.getMemo())
                .tags(TagNames.normalize(scheduled.getTags()))
                .number(scheduled.getNumber())
                .paymentMethod(scheduled.getPaymentMethod() != null ? scheduled.getPaymentMethod() : Transaction.PaymentMethod.NONE)
                .asset(scheduled.getAsset())
                .units(scheduled.getUnits())
                .transactionDate(dateOverride != null ? dateOverride.atStartOfDay() : scheduled.getNextOccurrence())
                .status(Transaction.TransactionStatus.COMPLETED)
                .scheduledTransactionId(scheduled.getId())
                .build();
    }

    private ScheduledTransaction ownedSchedule(Long scheduledId) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        ScheduledTransaction scheduled = repository.findById(scheduledId)
                .orElseThrow(() -> new IllegalArgumentException("Scheduled transaction not found"));
        if (!scheduled.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Cannot access scheduled transaction belonging to another user");
        }
        return scheduled;
    }

    /** Post every enabled schedule that is currently due, catching up missed occurrences. */
    @Transactional
    public int postAllDue() {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        LocalDateTime cutoff = dueCutoff();
        int posted = 0;
        for (ScheduledTransaction st : repository.findByUserAndEnabledTrueAndNextOccurrenceBefore(currentUser, cutoff)) {
            // post() mutates this same managed instance, so the loop sees each advance
            while (st.isEnabled() && st.getNextOccurrence() != null
                    && st.getNextOccurrence().isBefore(cutoff) && posted < 1000) {
                post(st.getId());
                posted++;
            }
        }
        return posted;
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
        auditService.log(currentUser, "SKIP", "ScheduledTransaction",
                scheduled.getId(), scheduled.getPayee());
        com.cuenti.app.util.ScheduledChangeBroadcaster.broadcast(currentUser.getId());
    }

    /** Advance one occurrence according to the schedule's recurrence pattern. */
    public static LocalDateTime advanceOccurrence(LocalDateTime current, ScheduledTransaction scheduled) {
        LocalDateTime next = current;
        int value = (scheduled.getRecurrenceValue() != null && scheduled.getRecurrenceValue() > 0)
                    ? scheduled.getRecurrenceValue() : 1;

        switch (scheduled.getRecurrencePattern()) {
            case DAILY -> next = next.plusDays(value);
            case WEEKLY -> next = next.plusWeeks(value);
            case BI_WEEKLY -> next = next.plusWeeks(2);
            case MONTHLY -> next = next.plusMonths(value);
            case MONTHLY_LAST_DAY -> next = next.plusMonths(value).with(TemporalAdjusters.lastDayOfMonth());
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
        return next;
    }

    /**
     * Moves to the next occurrence. A schedule with an end switches itself off once
     * its last occurrence is posted or skipped (count reaches 0, or the next date
     * falls after the end date).
     */
    private void updateToNextOccurrence(ScheduledTransaction scheduled) {
        LocalDateTime next = advanceOccurrence(scheduled.getNextOccurrence(), scheduled);
        scheduled.setNextOccurrence(next);
        if (scheduled.getRemainingOccurrences() != null) {
            scheduled.setRemainingOccurrences(Math.max(0, scheduled.getRemainingOccurrences() - 1));
        }
        if (isFinished(scheduled)) {
            scheduled.setEnabled(false);
        }
        repository.save(scheduled);
    }

    /** No occurrence left: count used up or the next date lies past the end date. */
    public static boolean isFinished(ScheduledTransaction st) {
        return (st.getRemainingOccurrences() != null && st.getRemainingOccurrences() <= 0)
                || (st.getEndDate() != null && st.getNextOccurrence() != null
                    && st.getNextOccurrence().toLocalDate().isAfter(st.getEndDate()));
    }

    /** Start of tomorrow: anything before it is due (today) or overdue. Shared by badge, list and post-all. */
    public static LocalDateTime dueCutoff() {
        return LocalDate.now().plusDays(1).atStartOfDay();
    }

    /** Due on an earlier day than today. */
    public static boolean isOverdue(ScheduledTransaction st) {
        return st.getNextOccurrence().isBefore(LocalDate.now().atStartOfDay());
    }

    /** Enabled schedules due now (today or overdue); both badge and toast use this. */
    public static boolean isDue(ScheduledTransaction st) {
        return st.isEnabled() && st.getNextOccurrence().isBefore(dueCutoff());
    }

    /**
     * Enabled schedules for the nav badge and reminder toast: due today or overdue,
     * extended by the user's badge look-ahead ({@link User#getScheduledBadgeDays()}).
     */
    @Transactional(readOnly = true)
    public List<ScheduledTransaction> findDue(User user) {
        int lookAhead = user.getScheduledBadgeDays() != null ? Math.max(0, user.getScheduledBadgeDays()) : 0;
        return repository.findByUserAndEnabledTrueAndNextOccurrenceBefore(user, dueCutoff().plusDays(lookAhead));
    }

    /** Transactions posted from a schedule, newest first. */
    @Transactional(readOnly = true)
    public List<Transaction> getHistory(Long scheduledId) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        ScheduledTransaction scheduled = repository.findById(scheduledId)
                .orElseThrow(() -> new IllegalArgumentException("Scheduled transaction not found"));
        if (!scheduled.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Cannot read history of scheduled transaction belonging to another user");
        }
        return transactionRepository.findByScheduledTransactionIdOrderByTransactionDateDesc(scheduledId);
    }

    /** Day rollover turns schedules due without any user action; repaint every open badge. */
    @Scheduled(cron = "5 0 0 * * *")
    public void refreshBadgesAtMidnight() {
        com.cuenti.app.util.ScheduledChangeBroadcaster.broadcastAll();
    }
}
