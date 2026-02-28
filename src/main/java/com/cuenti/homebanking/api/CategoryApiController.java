package com.cuenti.homebanking.api;

import com.cuenti.homebanking.api.dto.CategoryDTO;
import com.cuenti.homebanking.api.dto.DtoMapper;
import com.cuenti.homebanking.model.Category;
import com.cuenti.homebanking.service.CategoryService;
import com.cuenti.homebanking.service.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryApiController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryDTO>> getCategories(
            @RequestParam(required = false) Category.CategoryType type) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        List<Category> categories = type != null
                ? categoryService.getCategoriesByType(type)
                : categoryService.getAllCategories();

        return ResponseEntity.ok(categories.stream()
                .map(DtoMapper::toCategoryDTO)
                .collect(Collectors.toList()));
    }

    @PostMapping
    public ResponseEntity<CategoryDTO> createCategory(@RequestBody CategoryDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Category category = new Category();
        category.setName(dto.getName());
        category.setType(dto.getType());

        if (dto.getParentId() != null) {
            categoryService.getAllCategories().stream()
                    .filter(c -> c.getId().equals(dto.getParentId()))
                    .findFirst()
                    .ifPresent(category::setParent);
        }

        Category saved = categoryService.saveCategory(category);
        return ResponseEntity.ok(DtoMapper.toCategoryDTO(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryDTO> updateCategory(@PathVariable Long id, @RequestBody CategoryDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Category category = categoryService.getAllCategories().stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (category == null) return ResponseEntity.notFound().build();

        category.setName(dto.getName());
        category.setType(dto.getType());
        if (dto.getParentId() != null) {
            categoryService.getAllCategories().stream()
                    .filter(c -> c.getId().equals(dto.getParentId()))
                    .findFirst()
                    .ifPresent(category::setParent);
        } else {
            category.setParent(null);
        }

        Category saved = categoryService.saveCategory(category);
        return ResponseEntity.ok(DtoMapper.toCategoryDTO(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Category category = categoryService.getAllCategories().stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (category == null) return ResponseEntity.notFound().build();

        categoryService.deleteCategory(category);
        return ResponseEntity.ok().build();
    }
}
