package com.cuenti.homebanking.api;

import com.cuenti.homebanking.data.Transaction;
import com.cuenti.homebanking.data.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.services.TransactionService;
import com.cuenti.homebanking.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionApiController {

    private final TransactionService transactionService;
    private final UserService userService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<List<Transaction>> getTransactions() {
        return securityUtils.getAuthenticatedUsername()
                .map(userService::findByUsername)
                .map(user -> ResponseEntity.ok(transactionService.getTransactionsByUser(user)))
                .orElse(ResponseEntity.status(401).build());
    }

    @PostMapping
    public ResponseEntity<Transaction> createTransaction(@RequestBody Transaction transaction) {
        return securityUtils.getAuthenticatedUsername()
                .map(userService::findByUsername)
                .map(user -> ResponseEntity.ok(transactionService.saveTransaction(transaction)))
                .orElse(ResponseEntity.status(401).build());
    }
}
