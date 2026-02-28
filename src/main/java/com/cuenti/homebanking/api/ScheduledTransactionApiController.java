package com.cuenti.homebanking.api;

import com.cuenti.homebanking.api.dto.DtoMapper;
import com.cuenti.homebanking.api.dto.ScheduledTransactionDTO;
import com.cuenti.homebanking.model.*;
import com.cuenti.homebanking.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/scheduled-transactions")
@RequiredArgsConstructor
public class ScheduledTransactionApiController {

    private final ScheduledTransactionService scheduledTransactionService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final AssetService assetService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<ScheduledTransactionDTO>> getScheduledTransactions() {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();
        User user = userService.findByUsername(username);

        return ResponseEntity.ok(scheduledTransactionService.getByUser(user).stream()
                .map(DtoMapper::toScheduledTransactionDTO)
                .collect(Collectors.toList()));
    }

    @PostMapping
    public ResponseEntity<ScheduledTransactionDTO> create(@RequestBody ScheduledTransactionDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        ScheduledTransaction st = mapFromDTO(dto);
        ScheduledTransaction saved = scheduledTransactionService.save(st);
        return ResponseEntity.ok(DtoMapper.toScheduledTransactionDTO(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ScheduledTransactionDTO> update(@PathVariable Long id, @RequestBody ScheduledTransactionDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        ScheduledTransaction st = mapFromDTO(dto);
        st.setId(id);
        // Preserve user from existing
        User user = userService.findByUsername(username);
        st.setUser(user);
        ScheduledTransaction saved = scheduledTransactionService.save(st);
        return ResponseEntity.ok(DtoMapper.toScheduledTransactionDTO(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();
        User user = userService.findByUsername(username);

        ScheduledTransaction st = scheduledTransactionService.getByUser(user).stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (st == null) return ResponseEntity.notFound().build();

        scheduledTransactionService.delete(st);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/post")
    public ResponseEntity<Void> post(@PathVariable Long id) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        scheduledTransactionService.post(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/skip")
    public ResponseEntity<Void> skip(@PathVariable Long id) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        scheduledTransactionService.skip(id);
        return ResponseEntity.ok().build();
    }

    private ScheduledTransaction mapFromDTO(ScheduledTransactionDTO dto) {
        ScheduledTransaction.ScheduledTransactionBuilder builder = ScheduledTransaction.builder()
                .type(dto.getType())
                .amount(dto.getAmount())
                .payee(dto.getPayee())
                .memo(dto.getMemo())
                .tags(dto.getTags())
                .number(dto.getNumber())
                .units(dto.getUnits())
                .recurrencePattern(dto.getRecurrencePattern())
                .recurrenceValue(dto.getRecurrenceValue())
                .nextOccurrence(dto.getNextOccurrence())
                .enabled(dto.isEnabled());

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
