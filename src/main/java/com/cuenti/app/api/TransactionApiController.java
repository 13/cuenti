package com.cuenti.app.api;

import com.cuenti.app.api.dto.DtoMapper;
import com.cuenti.app.api.dto.TransactionDTO;
import com.cuenti.app.api.dto.TransactionSplitDTO;
import com.cuenti.app.model.*;
import com.cuenti.app.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    public ResponseEntity<?> createTransaction(@RequestBody TransactionDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Transaction transaction = mapFromDTO(dto);
        String splitError = applySplits(transaction, dto);
        if (splitError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", splitError));
        }
        Transaction saved = transactionService.saveTransaction(transaction);
        return ResponseEntity.ok(DtoMapper.toTransactionDTO(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTransaction(@PathVariable Long id, @RequestBody TransactionDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        // Update the user's existing transaction instead of merging a fresh
        // instance: a fresh instance carries an empty splits list, which
        // orphanRemoval would interpret as "delete all splits" even when the
        // request omitted the splits field entirely.
        Transaction existing = transactionService.getTransactionsByUser(
                        userService.findByUsername(username)).stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();

        String splitError = applySplits(existing, dto);
        if (splitError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", splitError));
        }
        applyDtoFields(existing, dto);
        Transaction saved = transactionService.saveTransaction(existing);
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
        Transaction transaction = Transaction.builder().build();
        applyDtoFields(transaction, dto);
        return transaction;
    }

    /** Copies the DTO's scalar and association fields onto the given transaction. */
    private void applyDtoFields(Transaction target, TransactionDTO dto) {
        target.setType(dto.getType());
        target.setAmount(dto.getAmount());
        target.setTransactionDate(dto.getTransactionDate());
        target.setPayee(dto.getPayee());
        target.setMemo(dto.getMemo());
        target.setTags(dto.getTags());
        target.setNumber(dto.getNumber());
        target.setPaymentMethod(dto.getPaymentMethod() != null ? dto.getPaymentMethod() : Transaction.PaymentMethod.NONE);
        target.setUnits(dto.getUnits());
        target.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        target.setFromAccount(dto.getFromAccountId() != null
                ? accountService.findById(dto.getFromAccountId()) : null);
        target.setToAccount(dto.getToAccountId() != null
                ? accountService.findById(dto.getToAccountId()) : null);
        target.setCategory(dto.getCategoryId() != null
                ? categoryService.findById(dto.getCategoryId()).orElse(null) : null);
        target.setAsset(dto.getAssetId() != null
                ? assetService.getAllAssets().stream()
                        .filter(a -> a.getId().equals(dto.getAssetId()))
                        .findFirst().orElse(null)
                : null);
    }

    /**
     * Validates the DTO's splits and, when the splits field is present,
     * replaces the transaction's split set. An absent (null) splits field
     * leaves the transaction's existing splits untouched; an empty list is a
     * deliberate remove-all.
     *
     * @return error message, or null when valid
     */
    private String applySplits(Transaction transaction, TransactionDTO dto) {
        if (dto.getSplits() == null) return null;

        List<TransactionSplit> newSplits = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (TransactionSplitDTO s : dto.getSplits()) {
            if (s.getAmount() == null) {
                return "Each split must have an amount";
            }
            Category category = s.getCategoryId() != null
                    ? categoryService.findById(s.getCategoryId()).orElse(null) : null;
            if (category == null) {
                return "Unknown split categoryId: " + s.getCategoryId();
            }
            sum = sum.add(s.getAmount());
            newSplits.add(TransactionSplit.builder()
                    .amount(s.getAmount())
                    .memo(s.getMemo())
                    .category(category)
                    .build());
        }
        if (!newSplits.isEmpty() && sum.compareTo(dto.getAmount()) != 0) {
            return "Split amounts must sum to the transaction amount";
        }

        transaction.getSplits().clear();
        newSplits.forEach(transaction::addSplit);
        return null;
    }
}
