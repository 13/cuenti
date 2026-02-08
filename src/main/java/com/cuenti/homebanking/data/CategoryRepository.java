package com.cuenti.homebanking.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByUserAndType(User user, Category.CategoryType type);
    List<Category> findByUser(User user);
}
