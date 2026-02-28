package com.cuenti.homebanking.api;

import com.cuenti.homebanking.api.dto.AccountDTO;
import com.cuenti.homebanking.api.dto.DtoMapper;
import com.cuenti.homebanking.model.Account;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.service.AccountService;
import com.cuenti.homebanking.service.SecurityUtil;
import com.cuenti.homebanking.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountApiController {

    private final AccountService accountService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<AccountDTO>> getAccounts() {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();
        User user = userService.findByUsername(username);

        return ResponseEntity.ok(accountService.getAccountsByUser(user).stream()
                .map(DtoMapper::toAccountDTO)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountDTO> getAccount(@PathVariable Long id) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        try {
            Account account = accountService.findById(id);
            return ResponseEntity.ok(DtoMapper.toAccountDTO(account));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<AccountDTO> createAccount(@RequestBody AccountDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();
        User user = userService.findByUsername(username);

        Account account = Account.builder()
                .accountName(dto.getAccountName())
                .accountNumber(dto.getAccountNumber())
                .accountType(dto.getAccountType())
                .accountGroup(dto.getAccountGroup())
                .institution(dto.getInstitution())
                .currency(dto.getCurrency() != null ? dto.getCurrency() : "EUR")
                .startBalance(dto.getStartBalance())
                .balance(dto.getStartBalance())
                .sortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0)
                .excludeFromSummary(dto.isExcludeFromSummary())
                .excludeFromReports(dto.isExcludeFromReports())
                .build();

        Account saved = accountService.saveAccountForUser(account, user);
        return ResponseEntity.ok(DtoMapper.toAccountDTO(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccountDTO> updateAccount(@PathVariable Long id, @RequestBody AccountDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();
        User user = userService.findByUsername(username);

        Account existing = accountService.findById(id);
        existing.setAccountName(dto.getAccountName());
        existing.setAccountType(dto.getAccountType());
        existing.setAccountGroup(dto.getAccountGroup());
        existing.setInstitution(dto.getInstitution());
        existing.setCurrency(dto.getCurrency());
        existing.setExcludeFromSummary(dto.isExcludeFromSummary());
        existing.setExcludeFromReports(dto.isExcludeFromReports());
        if (dto.getStartBalance() != null) {
            accountService.adjustStartBalance(existing, dto.getStartBalance());
        }

        Account saved = accountService.saveAccountForUser(existing, user);
        return ResponseEntity.ok(DtoMapper.toAccountDTO(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long id) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Account account = accountService.findById(id);
        accountService.deleteAccount(account);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/sort-order")
    public ResponseEntity<Void> updateSortOrder(@RequestBody List<Long> accountIds) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        List<Account> accounts = accountIds.stream()
                .map(accountService::findById)
                .collect(Collectors.toList());
        accountService.updateSortOrders(accounts);
        return ResponseEntity.ok().build();
    }
}
