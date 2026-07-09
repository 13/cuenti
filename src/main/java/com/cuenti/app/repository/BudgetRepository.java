package com.cuenti.app.repository;

import com.cuenti.app.model.Budget;
import com.cuenti.app.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByUserOrderByMonthlyLimitDesc(User user);

    Optional<Budget> findByUserAndCategoryId(User user, Long categoryId);
}
