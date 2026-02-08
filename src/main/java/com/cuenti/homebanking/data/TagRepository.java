package com.cuenti.homebanking.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {
    List<Tag> findByUserAndNameContainingIgnoreCase(User user, String name);
    List<Tag> findByUser(User user);
    Optional<Tag> findByUserAndName(User user, String name);
}
