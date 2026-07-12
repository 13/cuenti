package com.cuenti.app.api;

import com.cuenti.app.api.dto.BudgetDTO;
import com.cuenti.app.api.dto.BudgetProgressDTO;
import com.cuenti.app.api.dto.DtoMapper;
import com.cuenti.app.model.Budget;
import com.cuenti.app.model.Category;
import com.cuenti.app.model.User;
import com.cuenti.app.service.BudgetService;
import com.cuenti.app.service.CategoryService;
import com.cuenti.app.service.SecurityUtil;
import com.cuenti.app.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetApiController {

    private final BudgetService budgetService;
    private final CategoryService categoryService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<BudgetDTO>> getBudgets() {
        User user = currentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(budgetService.getBudgets(user).stream()
                .map(DtoMapper::toBudgetDTO)
                .collect(Collectors.toList()));
    }

    @PostMapping
    public ResponseEntity<?> createBudget(@RequestBody BudgetDTO dto) {
        User user = currentUser();
        if (user == null) return ResponseEntity.status(401).build();

        Category category = categoryService.findById(dto.getCategoryId()).orElse(null);
        if (category == null) return ResponseEntity.notFound().build();
        if (budgetService.existsForCategory(user, dto.getCategoryId(), null)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Budget for this category already exists"));
        }

        Budget saved = budgetService.saveBudget(Budget.builder()
                .user(user)
                .category(category)
                .monthlyLimit(dto.getMonthlyLimit())
                .active(true)
                .build());
        return ResponseEntity.ok(DtoMapper.toBudgetDTO(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBudget(@PathVariable Long id, @RequestBody BudgetDTO dto) {
        User user = currentUser();
        if (user == null) return ResponseEntity.status(401).build();

        Budget budget = findOwn(user, id);
        if (budget == null) return ResponseEntity.notFound().build();

        if (dto.getCategoryId() != null && !dto.getCategoryId().equals(budget.getCategory().getId())) {
            Category category = categoryService.findById(dto.getCategoryId()).orElse(null);
            if (category == null) return ResponseEntity.notFound().build();
            if (budgetService.existsForCategory(user, dto.getCategoryId(), id)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Budget for this category already exists"));
            }
            budget.setCategory(category);
        }
        if (dto.getMonthlyLimit() != null) budget.setMonthlyLimit(dto.getMonthlyLimit());
        if (dto.getActive() != null) budget.setActive(dto.getActive());

        return ResponseEntity.ok(DtoMapper.toBudgetDTO(budgetService.saveBudget(budget)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBudget(@PathVariable Long id) {
        User user = currentUser();
        if (user == null) return ResponseEntity.status(401).build();

        Budget budget = findOwn(user, id);
        if (budget == null) return ResponseEntity.notFound().build();

        budgetService.deleteBudget(budget);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/progress")
    public ResponseEntity<List<BudgetProgressDTO>> getProgress() {
        User user = currentUser();
        if (user == null) return ResponseEntity.status(401).build();

        Map<Long, BigDecimal> spentByCategory = budgetService.getSpentThisMonth(user);
        List<BudgetProgressDTO> progress = budgetService.getBudgets(user).stream()
                .map(b -> {
                    BigDecimal spent = spentByCategory.getOrDefault(b.getCategory().getId(), BigDecimal.ZERO);
                    return BudgetProgressDTO.builder()
                            .budgetId(b.getId())
                            .categoryId(b.getCategory().getId())
                            .categoryName(b.getCategory().getFullName())
                            .monthlyLimit(b.getMonthlyLimit())
                            .spent(spent)
                            .remaining(b.getMonthlyLimit().subtract(spent))
                            .active(b.isActive())
                            .build();
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(progress);
    }

    private User currentUser() {
        return SecurityUtil.getAuthenticatedUsername()
                .map(userService::findByUsername)
                .orElse(null);
    }

    private Budget findOwn(User user, Long id) {
        return budgetService.getBudgets(user).stream()
                .filter(b -> b.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}
