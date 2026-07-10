package com.cuenti.app.service;

import com.cuenti.app.model.Budget;
import com.cuenti.app.model.User;
import com.cuenti.app.repository.BudgetRepository;
import com.cuenti.app.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Monthly category budgets: CRUD plus current-month spend per category.
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<Budget> getBudgets(User user) {
        return budgetRepository.findByUserOrderByMonthlyLimitDesc(user);
    }

    @Transactional
    public Budget saveBudget(Budget budget) {
        boolean created = budget.getId() == null;
        Budget saved = budgetRepository.save(budget);
        auditService.log(saved.getUser(), created ? "CREATE" : "UPDATE", "Budget", saved.getId(),
                saved.getCategory().getFullName() + " " + saved.getMonthlyLimit());
        return saved;
    }

    @Transactional
    public void deleteBudget(Budget budget) {
        budgetRepository.delete(budget);
        auditService.log(budget.getUser(), "DELETE", "Budget", budget.getId(),
                budget.getCategory().getFullName() + " " + budget.getMonthlyLimit());
    }

    @Transactional(readOnly = true)
    public boolean existsForCategory(User user, Long categoryId, Long exceptBudgetId) {
        return budgetRepository.findByUserAndCategoryId(user, categoryId)
                .filter(b -> exceptBudgetId == null || !b.getId().equals(exceptBudgetId))
                .isPresent();
    }

    /**
     * Expense sum per category id for the current month.
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> getSpentThisMonth(User user) {
        LocalDate now = LocalDate.now();
        LocalDateTime from = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime to = now.withDayOfMonth(now.lengthOfMonth()).atTime(23, 59, 59);

        Map<Long, BigDecimal> result = new HashMap<>();
        for (Object[] row : transactionRepository.sumExpensesByCategory(user, from, to, com.cuenti.app.model.Transaction.TransactionType.EXPENSE)) {
            result.put(((Number) row[0]).longValue(), (BigDecimal) row[1]);
        }
        return result;
    }
}
