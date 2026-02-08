package com.cuenti.homebanking.services;

import com.cuenti.homebanking.data.Payee;
import com.cuenti.homebanking.data.PayeeRepository;
import com.cuenti.homebanking.data.User;
import com.cuenti.homebanking.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PayeeService {
    private final PayeeRepository payeeRepository;
    private final UserService userService;
    private final SecurityUtils securityUtils;

    public List<Payee> getAllPayees() {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        return payeeRepository.findAllWithDetailsByUser(currentUser);
    }

    public List<Payee> searchPayees(String searchTerm) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        if (searchTerm == null || searchTerm.isEmpty()) {
            return payeeRepository.findAllWithDetailsByUser(currentUser);
        }
        return payeeRepository.findByUserAndNameContainingIgnoreCaseWithDetails(currentUser, searchTerm);
    }

    @Transactional
    public Payee savePayee(Payee payee) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);

        if (payee.getId() == null) {
            payee.setUser(currentUser);
        } else {
            Payee existing = payeeRepository.findById(payee.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Payee not found"));
            if (!existing.getUser().getId().equals(currentUser.getId())) {
                throw new SecurityException("Cannot modify payee belonging to another user");
            }
            payee.setUser(currentUser);
        }
        return payeeRepository.save(payee);
    }

    @Transactional
    public void deletePayee(Payee payee) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        if (payee.getUser().getId().equals(currentUser.getId())) {
            payeeRepository.delete(payee);
        } else {
            throw new SecurityException("Cannot delete payee belonging to another user");
        }
    }
}
