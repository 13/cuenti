package com.cuenti.homebanking.repository;

import com.cuenti.homebanking.model.ScheduledTransaction;
import com.cuenti.homebanking.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduledTransactionRepository extends JpaRepository<ScheduledTransaction, Long> {
    List<ScheduledTransaction> findByUser(User user);
}
