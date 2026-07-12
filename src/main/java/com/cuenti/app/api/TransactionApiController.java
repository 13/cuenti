package com.cuenti.app.api;

import com.cuenti.app.api.dto.DtoMapper;
import com.cuenti.app.api.dto.PagedResponse;
import com.cuenti.app.api.dto.TransactionDTO;
import com.cuenti.app.api.dto.TransactionSplitDTO;
import com.cuenti.app.model.*;
import com.cuenti.app.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final Set<String> SORT_WHITELIST = Set.of("transactionDate", "amount", "payee");

    @GetMapping
    public ResponseEntity<?> getTransactions(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Transaction.TransactionType type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) String payee,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();
        User user = userService.findByUsername(username);

        String sortField = "transactionDate";
        Sort.Direction sortDirection = Sort.Direction.DESC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            if (!SORT_WHITELIST.contains(parts[0])) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "sort field must be one of " + SORT_WHITELIST));
            }
            sortField = parts[0];
            if (parts.length > 1 && "asc".equalsIgnoreCase(parts[1])) sortDirection = Sort.Direction.ASC;
        }
        Sort sortSpec = Sort.by(sortDirection, sortField).and(Sort.by(Sort.Direction.DESC, "sortOrder"));

        boolean paged = page != null || size != null;
        int effectivePage = page != null ? Math.max(page, 0) : 0;
        int effectiveSize = size != null ? Math.min(Math.max(size, 1), 200) : 50;
        Pageable pageable = paged
                ? PageRequest.of(effectivePage, effectiveSize, sortSpec)
                : Pageable.unpaged(sortSpec);

        Page<Transaction> result = transactionService.search(user, accountId, type, categoryId,
                start != null ? start.atStartOfDay() : null,
                end != null ? end.atTime(java.time.LocalTime.MAX) : null,
                emptyToNull(payee), emptyToNull(tag), emptyToNull(search), pageable);

        List<TransactionDTO> dtos = result.getContent().stream()
                .map(DtoMapper::toTransactionDTO)
                .collect(Collectors.toList());

        if (!paged) return ResponseEntity.ok(dtos); // back-compat: plain array

        return ResponseEntity.ok(PagedResponse.<TransactionDTO>builder()
                .content(dtos)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build());
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    @PostMapping
    public ResponseEntity<?> createTransaction(@RequestBody TransactionDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        String splitError = validateSplits(dto);
        if (splitError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", splitError));
        }
        Transaction transaction = mapFromDTO(dto);
        applySplitsMutation(transaction, dto);
        Transaction saved = transactionService.saveTransaction(transaction);
        return ResponseEntity.ok(DtoMapper.toTransactionDTO(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTransaction(@PathVariable Long id, @RequestBody TransactionDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        // Read-only existence/ownership check plus validation of the incoming DTO.
        // Nothing below reads or writes any scalar/association field for the purpose
        // of persisting it - the actual mutation happens inside
        // TransactionService.updateTransaction, on a fresh entity loaded within that
        // single write transaction, so balance reversal always sees the true old
        // amount/type/accounts (see the Javadoc on that method for why this matters).
        Transaction existing = transactionService.getTransactionsByUser(
                        userService.findByUsername(username)).stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();

        String splitError = validateSplits(dto);
        if (splitError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", splitError));
        }
        String sumError = validateSplitSumInvariant(existing, dto);
        if (sumError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", sumError));
        }

        Transaction saved = transactionService.updateTransaction(id, fresh -> {
            applySplitsMutation(fresh, dto);
            applyDtoFields(fresh, dto);
        });
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
     * Validates the DTO's splits structurally (amount present, category known, sum
     * matches the DTO's amount) without touching any managed entity. Returns null
     * when the splits field is absent (nothing to validate for the "leave existing
     * splits untouched" case).
     *
     * @return error message, or null when valid
     */
    private String validateSplits(TransactionDTO dto) {
        if (dto.getSplits() == null) return null;

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
        }
        if (!dto.getSplits().isEmpty() && sum.compareTo(dto.getAmount()) != 0) {
            return "Split amounts must sum to the transaction amount";
        }
        return null;
    }

    /**
     * When the splits field is present, replaces the transaction's split set. An
     * absent (null) splits field leaves the transaction's existing splits untouched;
     * an empty list is a deliberate remove-all. Assumes {@link #validateSplits}
     * already returned null for this DTO.
     */
    private void applySplitsMutation(Transaction transaction, TransactionDTO dto) {
        if (dto.getSplits() == null) return;

        List<TransactionSplit> newSplits = new ArrayList<>();
        for (TransactionSplitDTO s : dto.getSplits()) {
            Category category = categoryService.findById(s.getCategoryId()).orElse(null);
            newSplits.add(TransactionSplit.builder()
                    .amount(s.getAmount())
                    .memo(s.getMemo())
                    .category(category)
                    .build());
        }

        transaction.getSplits().clear();
        newSplits.forEach(transaction::addSplit);
    }

    /**
     * Closes the split-sum invariant hole for updates that change the amount without
     * resending the splits field: if the DTO omits splits but the existing
     * transaction has splits recorded, the (possibly new) DTO amount must still match
     * the existing splits' sum, otherwise the persisted splits would silently no
     * longer add up to the transaction amount.
     *
     * @return error message, or null when there is nothing to enforce or it matches
     */
    private String validateSplitSumInvariant(Transaction existing, TransactionDTO dto) {
        if (dto.getSplits() != null) return null;
        if (existing.getSplits() == null || existing.getSplits().isEmpty()) return null;
        if (dto.getAmount() == null) return null;

        BigDecimal existingSum = existing.getSplits().stream()
                .map(TransactionSplit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (existingSum.compareTo(dto.getAmount()) != 0) {
            return "Amount must match existing split total (" + existingSum
                    + ") when splits are not provided";
        }
        return null;
    }
}
