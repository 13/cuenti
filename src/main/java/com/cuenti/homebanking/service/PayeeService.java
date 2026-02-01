package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.Payee;
import com.cuenti.homebanking.repository.PayeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PayeeService {
    private final PayeeRepository payeeRepository;

    public List<Payee> getAllPayees() {
        return payeeRepository.findAllWithDetails();
    }

    public List<Payee> searchPayees(String searchTerm) {
        if (searchTerm == null || searchTerm.isEmpty()) {
            return getAllPayees();
        }
        return payeeRepository.findByNameContainingIgnoreCaseWithDetails(searchTerm);
    }

    @Transactional
    public Payee savePayee(Payee payee) {
        return payeeRepository.save(payee);
    }

    @Transactional
    public void deletePayee(Payee payee) {
        payeeRepository.delete(payee);
    }
}
