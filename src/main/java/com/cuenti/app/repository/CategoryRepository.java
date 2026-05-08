package com.cuenti.app.repository;

import com.cuenti.app.model.Category;
import com.cuenti.app.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByUserAndType(User user, Category.CategoryType type);
    List<Category> findByUser(User user);
    Optional<Category> findByUserAndParentAndName(User user, Category parent, String name);
    List<Category> findByParent(Category parent);
}
