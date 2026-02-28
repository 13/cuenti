package com.cuenti.homebanking.api;

import com.cuenti.homebanking.api.dto.DtoMapper;
import com.cuenti.homebanking.api.dto.TransactionDTO;
import com.cuenti.homebanking.model.*;
import com.cuenti.homebanking.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionApiController {

    private final TransactionService transactionService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final AssetService assetService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<TransactionDTO>> getTransactions(
            @RequestParam(required = false) Long accountId) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();
        User user = userService.findByUsername(username);

        List<Transaction> transactions;
        if (accountId != null) {
            Account account = accountService.findById(accountId);
            transactions = transactionService.getTransactionsByAccount(account);
        } else {
            transactions = transactionService.getTransactionsByUser(user);
        }

        return ResponseEntity.ok(transactions.stream()
                .map(DtoMapper::toTransactionDTO)
                .collect(Collectors.toList()));
    }

    @PostMapping
    public ResponseEntity<TransactionDTO> createTransaction(@RequestBody TransactionDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Transaction transaction = mapFromDTO(dto);
        Transaction saved = transactionService.saveTransaction(transaction);
        return ResponseEntity.ok(DtoMapper.toTransactionDTO(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionDTO> updateTransaction(@PathVariable Long id, @RequestBody TransactionDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Transaction transaction = mapFromDTO(dto);
        transaction.setId(id);
        Transaction saved = transactionService.saveTransaction(transaction);
        return ResponseEntity.ok(DtoMapper.toTransactionDTO(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Transaction transaction = Transaction.builder().id(id).build();
        // Need to load the full transaction for balance reversal
        List<Transaction> userTransactions = transactionService.getTransactionsByUser(
                userService.findByUsername(username));
        Transaction existing = userTransactions.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();

        transactionService.deleteTransaction(existing);
        return ResponseEntity.ok().build();
    }

    private Transaction mapFromDTO(TransactionDTO dto) {
        Transaction.TransactionBuilder builder = Transaction.builder()
                .type(dto.getType())
                .amount(dto.getAmount())
                .transactionDate(dto.getTransactionDate())
                .payee(dto.getPayee())
                .memo(dto.getMemo())
                .tags(dto.getTags())
                .number(dto.getNumber())
                .paymentMethod(dto.getPaymentMethod() != null ? dto.getPaymentMethod() : Transaction.PaymentMethod.NONE)
                .units(dto.getUnits())
                .sortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);

        if (dto.getFromAccountId() != null) {
            builder.fromAccount(accountService.findById(dto.getFromAccountId()));
        }
        if (dto.getToAccountId() != null) {
            builder.toAccount(accountService.findById(dto.getToAccountId()));
        }
        if (dto.getCategoryId() != null) {
            categoryService.getAllCategories().stream()
                    .filter(c -> c.getId().equals(dto.getCategoryId()))
                    .findFirst()
                    .ifPresent(builder::category);
        }
        if (dto.getAssetId() != null) {
            assetService.getAllAssets().stream()
                    .filter(a -> a.getId().equals(dto.getAssetId()))
                    .findFirst()
                    .ifPresent(builder::asset);
        }

        return builder.build();
    }
}
