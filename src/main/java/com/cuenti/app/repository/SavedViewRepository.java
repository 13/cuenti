package com.cuenti.app.repository;

import com.cuenti.app.model.SavedView;
import com.cuenti.app.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedViewRepository extends JpaRepository<SavedView, Long> {

    List<SavedView> findByUserOrderByNameAsc(User user);

    Optional<SavedView> findByUserAndName(User user, String name);
}
