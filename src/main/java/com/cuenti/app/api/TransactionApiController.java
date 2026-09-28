package com.cuenti.app.api;

import com.cuenti.app.api.dto.DtoMapper;
import com.cuenti.app.api.dto.PagedResponse;
import com.cuenti.app.api.dto.TransactionDTO;
import com.cuenti.app.api.dto.TransactionSplitDTO;
import com.cuenti.app.model.*;
import com.cuenti.app.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final IdempotencyService idempotencyService;

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
        // The id tiebreaker makes the order total: without it, rows sharing
        // (transactionDate, sortOrder) shuffle between pages and paginated
        // clients see duplicates/gaps.
        Sort sortSpec = Sort.by(sortDirection, sortField)
                .and(Sort.by(Sort.Direction.DESC, "sortOrder"))
                .and(Sort.by(Sort.Direction.DESC, "id"));

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

    /**
     * Creates a transaction. With an {@code Idempotency-Key} header, repeating
     * the request returns the transaction the first one created (marked
     * {@code Idempotent-Replayed: true}) instead of creating another -- what
     * lets an offline client safely resend a write whose response it lost.
     */
    @PostMapping
    public ResponseEntity<?> createTransaction(
            @RequestBody TransactionDTO dto,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();
        return create(username, dto, idempotencyKey);
    }

    /**
     * Updates a transaction. {@code If-Match} refuses the write with 409 when the
     * row has changed since the client's copy. An {@code Idempotency-Key} makes a
     * resend of an update that was already applied answer with the row as it
     * stands, rather than with a 409 against the change it made itself.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateTransaction(
            @PathVariable Long id,
            @RequestBody TransactionDTO dto,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();
        return update(username, id, dto, ifMatch, idempotencyKey);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTransaction(
            @PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();
        return delete(username, id, ifMatch);
    }

    /** Most operations one {@link #batch} request may carry. */
    static final int MAX_BATCH = 100;

    /** One write in a {@link #batch}: what the single-item endpoint's path, headers and body would carry. */
    public record BatchOperation(String clientId, String op, Long id, String version,
                                 String idempotencyKey, TransactionDTO transaction) {}

    public record BatchRequest(List<BatchOperation> operations) {}

    /**
     * What the single-item endpoint would have answered: its status, and the
     * transaction or the error message it would have sent.
     */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record BatchResult(String clientId, int status, TransactionDTO transaction,
                              String error, Boolean replayed) {}

    /**
     * Applies several writes in one round trip -- what an offline client
     * sends when it reconnects with a queue.
     *
     * <p>Each operation is exactly the single-item request it stands for, with
     * the same idempotency and {@code If-Match} rules, and in its own database
     * transaction: one refused operation neither rolls back nor stops the others.
     * They run in order, so an update queued after the create it depends on
     * sees it. The answer is always 200 with one result per operation, in
     * request order; each result's {@code status} is what the single-item
     * endpoint would have answered.
     */
    @PostMapping("/batch")
    public ResponseEntity<?> batch(@RequestBody BatchRequest request) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();
        List<BatchOperation> operations = request == null || request.operations() == null
                ? List.of() : request.operations();
        if (operations.size() > MAX_BATCH) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "A batch may carry at most " + MAX_BATCH + " operations"));
        }
        List<BatchResult> results = new ArrayList<>(operations.size());
        for (BatchOperation op : operations) {
            results.add(apply(username, op));
        }
        return ResponseEntity.ok(Map.of("results", results));
    }

    private BatchResult apply(String username, BatchOperation op) {
        String clientId = op == null ? null : op.clientId();
        try {
            if (op == null || op.op() == null) {
                return new BatchResult(clientId, 400, null, "op is required", null);
            }
            ResponseEntity<?> answer = switch (op.op().toUpperCase(java.util.Locale.ROOT)) {
                case "CREATE" -> op.transaction() == null
                        ? badRequest("transaction is required")
                        : create(username, op.transaction(), op.idempotencyKey());
                case "UPDATE" -> op.id() == null || op.transaction() == null
                        ? badRequest("id and transaction are required")
                        : update(username, op.id(), op.transaction(), op.version(), op.idempotencyKey());
                case "DELETE" -> op.id() == null
                        ? badRequest("id is required")
                        : delete(username, op.id(), op.version());
                default -> badRequest("op must be CREATE, UPDATE or DELETE");
            };
            return toResult(clientId, answer);
        } catch (IllegalArgumentException e) {
            // What the single-item endpoints answer through the controller advice.
            return new BatchResult(clientId, 400, null, String.valueOf(e.getMessage()), null);
        } catch (SecurityException e) {
            return new BatchResult(clientId, 404, null, null, null);
        } catch (RuntimeException e) {
            // Contained to this operation, like a 500 on its own request would be.
            return new BatchResult(clientId, 500, null, "Internal error", null);
        }
    }

    private static ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    private static BatchResult toResult(String clientId, ResponseEntity<?> answer) {
        int status = answer.getStatusCode().value();
        Object body = answer.getBody();
        TransactionDTO transaction = body instanceof TransactionDTO dto ? dto : null;
        String error = body instanceof Map<?, ?> map && map.get("error") != null
                ? String.valueOf(map.get("error")) : null;
        Boolean replayed = "true".equals(answer.getHeaders().getFirst("Idempotent-Replayed")) ? true : null;
        return new BatchResult(clientId, status, transaction, error, replayed);
    }

    /** Validates an Idempotency-Key, answering 400 for a bad one; null when it is fine. */
    private static ResponseEntity<?> invalidKey(String key) {
        if (key != null && (key.isEmpty() || key.length() > IdempotencyService.MAX_KEY_LENGTH)) {
            return badRequest("Idempotency-Key must be 1-" + IdempotencyService.MAX_KEY_LENGTH + " characters");
        }
        return null;
    }

    private ResponseEntity<?> create(String username, TransactionDTO dto, String idempotencyKey) {
        String key = idempotencyKey == null ? null : idempotencyKey.trim();
        ResponseEntity<?> keyError = invalidKey(key);
        if (keyError != null) return keyError;
        Long userId = key == null ? null : userService.findByUsername(username).getId();
        if (key != null) {
            ResponseEntity<?> replayed = replay(userId, key);
            if (replayed != null) return replayed;
        }

        String splitError = validateSplits(dto);
        if (splitError != null) return badRequest(splitError);
        Transaction transaction = mapFromDTO(dto);
        applySplitsMutation(transaction, dto);
        if (key == null) {
            Transaction saved = transactionService.saveTransaction(transaction);
            return ResponseEntity.ok(DtoMapper.toTransactionDTO(saved));
        }
        try {
            Transaction saved = idempotencyService.createOnce(userId, key, transaction);
            return ResponseEntity.ok(DtoMapper.toTransactionDTO(saved));
        } catch (DataIntegrityViolationException e) {
            // A concurrent request with the same key committed first, and its
            // result is this request's result. Anything else is a real failure.
            ResponseEntity<?> raced = replay(userId, key);
            if (raced != null) return raced;
            throw e;
        }
    }

    private ResponseEntity<?> update(String username, Long id, TransactionDTO dto,
                                     String ifMatch, String idempotencyKey) {
        String key = idempotencyKey == null ? null : idempotencyKey.trim();
        ResponseEntity<?> keyError = invalidKey(key);
        if (keyError != null) return keyError;
        User user = userService.findByUsername(username);
        if (key != null) {
            ResponseEntity<?> replayed = replayUpdate(user.getId(), key);
            if (replayed != null) return replayed;
        }

        // Read-only existence/ownership check plus validation of the incoming DTO.
        // Nothing below reads or writes any scalar/association field for the purpose
        // of persisting it - the actual mutation happens inside
        // TransactionService.updateTransaction, on a fresh entity loaded within that
        // single write transaction, so balance reversal always sees the true old
        // amount/type/accounts (see the Javadoc on that method for why this matters).
        Transaction existing = transactionService.findOwned(id, user).orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();

        String splitError = validateSplits(dto);
        if (splitError != null) return badRequest(splitError);
        String sumError = validateSplitSumInvariant(existing, dto);
        if (sumError != null) return badRequest(sumError);

        java.util.function.Consumer<Transaction> mutator = fresh -> {
            applySplitsMutation(fresh, dto);
            applyDtoFields(fresh, dto);
        };
        Transaction saved;
        try {
            saved = key == null
                    ? transactionService.updateTransaction(id, ifMatch, mutator)
                    : idempotencyService.updateOnce(user.getId(), key, id, ifMatch, mutator);
        } catch (StaleTransactionException e) {
            return staleConflict();
        } catch (DataIntegrityViolationException e) {
            if (key == null) throw e;
            // The same update, sent twice at once; the other one was applied.
            ResponseEntity<?> raced = replayUpdate(user.getId(), key);
            if (raced != null) return raced;
            throw e;
        }
        return ResponseEntity.ok(DtoMapper.toTransactionDTO(saved));
    }

    /** What an earlier request with this key already got, or null if there was none. */
    private ResponseEntity<?> replay(Long userId, String key) {
        return idempotencyService.find(userId, key)
                .<ResponseEntity<?>>map(found -> found.transaction()
                        .<ResponseEntity<?>>map(t -> ResponseEntity.ok()
                                .header("Idempotent-Replayed", "true")
                                .body(DtoMapper.toTransactionDTO(t)))
                        .orElseGet(() -> ResponseEntity.status(409).body(Map.of("error",
                                "This transaction was already created and has since been deleted."))))
                .orElse(null);
    }

    /** 409 for a write made against an outdated copy. */
    private static ResponseEntity<?> staleConflict() {
        return ResponseEntity.status(409).body(Map.of("error",
                "This transaction was changed after this edit was made. Reload it and apply the change again."));
    }

    /** The row an update with this key already produced, as it stands now; null if there was none. */
    private ResponseEntity<?> replayUpdate(Long userId, String key) {
        return idempotencyService.find(userId, key)
                .<ResponseEntity<?>>map(found -> found.transaction()
                        .<ResponseEntity<?>>map(t -> ResponseEntity.ok()
                                .header("Idempotent-Replayed", "true")
                                .body(DtoMapper.toTransactionDTO(t)))
                        .orElseGet(() -> ResponseEntity.notFound().build()))
                .orElse(null);
    }

    private ResponseEntity<?> delete(String username, Long id, String ifMatch) {
        // The full row is needed for the balance reversal.
        Transaction existing = transactionService
                .findOwned(id, userService.findByUsername(username))
                .orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();

        try {
            transactionService.deleteTransaction(existing, ifMatch);
        } catch (StaleTransactionException e) {
            return staleConflict();
        }
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
