package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.Category;
import com.cuenti.homebanking.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public List<Category> getCategoriesByType(Category.CategoryType type) {
        return categoryRepository.findByType(type);
    }

    @Transactional
    public Category saveCategory(Category category) {
        return categoryRepository.save(category);
    }
}
