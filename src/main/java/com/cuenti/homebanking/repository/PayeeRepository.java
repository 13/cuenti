package com.cuenti.homebanking.repository;

import com.cuenti.homebanking.model.Payee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayeeRepository extends JpaRepository<Payee, Long> {

    List<Payee> findByNameContainingIgnoreCase(String name);

    @Query("SELECT DISTINCT p FROM Payee p LEFT JOIN FETCH p.defaultCategory c LEFT JOIN FETCH c.parent")
    List<Payee> findAllWithDetails();

    @Query("SELECT DISTINCT p FROM Payee p LEFT JOIN FETCH p.defaultCategory c LEFT JOIN FETCH c.parent WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Payee> findByNameContainingIgnoreCaseWithDetails(@Param("name") String name);
}
