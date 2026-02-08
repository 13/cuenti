package com.cuenti.homebanking.repository;

import com.cuenti.homebanking.model.Currency;
import com.cuenti.homebanking.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurrencyRepository extends JpaRepository<Currency, Long> {
    List<Currency> findByUser(User user);
    Optional<Currency> findByUserAndCode(User user, String code);
    Optional<Currency> findByIdAndUser(Long id, User user);
}
