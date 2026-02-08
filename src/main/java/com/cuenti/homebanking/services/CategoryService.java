package com.cuenti.homebanking.services;

import com.cuenti.homebanking.data.*;
import com.cuenti.homebanking.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final ScheduledTransactionRepository scheduledTransactionRepository;
    private final PayeeRepository payeeRepository;
    private final UserService userService;
    private final SecurityUtils securityUtils;

    public List<Category> getAllCategories() {
        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        User currentUser = userService.findByUsername(username);
        return categoryRepository.findByUser(currentUser);
    }

    public List<Category> getCategoriesByType(Category.CategoryType type) {
        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        User currentUser = userService.findByUsername(username);
        return categoryRepository.findByUserAndType(currentUser, type);
    }

    @Transactional
    public Category saveCategory(Category category) {
        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        User currentUser = userService.findByUsername(username);

        if (category.getId() == null) {
            category.setUser(currentUser);
        } else {
            Category existing = categoryRepository.findById(category.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"));
            if (!existing.getUser().getId().equals(currentUser.getId())) {
                throw new SecurityException("Cannot modify category belonging to another user");
            }
            category.setUser(currentUser);
        }
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(Category category) {
        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        User currentUser = userService.findByUsername(username);

        if (!category.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Cannot delete category belonging to another user");
        }

        // Use bulk updates to set category to null in all related entities
        transactionRepository.clearCategoryReferences(category.getId());
        scheduledTransactionRepository.clearCategoryReferences(category.getId());
        payeeRepository.clearCategoryReferences(category.getId());

        categoryRepository.delete(category);
    }
}
