package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.Category;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.repository.CategoryRepository;
import com.cuenti.homebanking.repository.TransactionRepository;
import com.cuenti.homebanking.repository.ScheduledTransactionRepository;
import com.cuenti.homebanking.repository.PayeeRepository;
import com.cuenti.homebanking.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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

    public Optional<Category> findById(Long id) {
        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        User currentUser = userService.findByUsername(username);
        return categoryRepository.findById(id)
                .filter(c -> c.getUser().getId().equals(currentUser.getId()));
    }

    @Transactional
    public Category saveCategory(Category category) {
        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        User currentUser = userService.findByUsername(username);

        // If it's a new category, set the user
        if (category.getId() == null) {
            category.setUser(currentUser);
        } else {
            // If updating, verify the user owns it
            Category existing = categoryRepository.findById(category.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"));
            if (!existing.getUser().getId().equals(currentUser.getId())) {
                throw new SecurityException("Cannot modify category belonging to another user");
            }
            category.setUser(currentUser);
        }

        // Uniqueness check: name must be unique within same parent (or root) for same user
        categoryRepository.findByUserAndParentAndName(currentUser, category.getParent(), category.getName())
                .ifPresent(existing -> {
                    if (!existing.getId().equals(category.getId())) {
                        throw new IllegalArgumentException("Category with same name already exists in this hierarchy");
                    }
                });

        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(Category category) {
        String username = securityUtils.getAuthenticatedUsername().orElseThrow();
        User currentUser = userService.findByUsername(username);

        // Security check: only allow deletion if category belongs to current user
        if (!category.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("Cannot delete category belonging to another user");
        }

        // Use bulk updates to set category to null in all related entities
        transactionRepository.clearCategoryReferences(category.getId());
        scheduledTransactionRepository.clearCategoryReferences(category.getId());
        payeeRepository.clearCategoryReferences(category.getId());

        // Now safe to delete the category
        categoryRepository.delete(category);
    }
}
