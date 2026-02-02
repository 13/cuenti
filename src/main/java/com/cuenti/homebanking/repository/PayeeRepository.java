package com.cuenti.homebanking.repository;

import com.cuenti.homebanking.model.Payee;
import com.cuenti.homebanking.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayeeRepository extends JpaRepository<Payee, Long> {

    List<Payee> findByUser(User user);

    List<Payee> findByUserAndNameContainingIgnoreCase(User user, String name);

    @Query("SELECT DISTINCT p FROM Payee p LEFT JOIN FETCH p.defaultCategory c LEFT JOIN FETCH c.parent WHERE p.user = :user")
    List<Payee> findAllWithDetailsByUser(@Param("user") User user);

    @Query("SELECT DISTINCT p FROM Payee p LEFT JOIN FETCH p.defaultCategory c LEFT JOIN FETCH c.parent WHERE p.user = :user AND LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Payee> findByUserAndNameContainingIgnoreCaseWithDetails(@Param("user") User user, @Param("name") String name);
}
