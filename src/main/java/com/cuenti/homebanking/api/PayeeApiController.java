package com.cuenti.homebanking.api;

import com.cuenti.homebanking.api.dto.DtoMapper;
import com.cuenti.homebanking.api.dto.PayeeDTO;
import com.cuenti.homebanking.model.Payee;
import com.cuenti.homebanking.service.CategoryService;
import com.cuenti.homebanking.service.PayeeService;
import com.cuenti.homebanking.service.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/payees")
@RequiredArgsConstructor
public class PayeeApiController {

    private final PayeeService payeeService;
    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<PayeeDTO>> getPayees(@RequestParam(required = false) String search) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        List<Payee> payees = search != null
                ? payeeService.searchPayees(search)
                : payeeService.getAllPayees();

        return ResponseEntity.ok(payees.stream()
                .map(DtoMapper::toPayeeDTO)
                .collect(Collectors.toList()));
    }

    @PostMapping
    public ResponseEntity<PayeeDTO> createPayee(@RequestBody PayeeDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Payee payee = new Payee();
        payee.setName(dto.getName());
        payee.setNotes(dto.getNotes());
        payee.setDefaultPaymentMethod(dto.getDefaultPaymentMethod());
        if (dto.getDefaultCategoryId() != null) {
            categoryService.getAllCategories().stream()
                    .filter(c -> c.getId().equals(dto.getDefaultCategoryId()))
                    .findFirst()
                    .ifPresent(payee::setDefaultCategory);
        }

        Payee saved = payeeService.savePayee(payee);
        return ResponseEntity.ok(DtoMapper.toPayeeDTO(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PayeeDTO> updatePayee(@PathVariable Long id, @RequestBody PayeeDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Payee payee = payeeService.getAllPayees().stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (payee == null) return ResponseEntity.notFound().build();

        payee.setName(dto.getName());
        payee.setNotes(dto.getNotes());
        payee.setDefaultPaymentMethod(dto.getDefaultPaymentMethod());
        if (dto.getDefaultCategoryId() != null) {
            categoryService.getAllCategories().stream()
                    .filter(c -> c.getId().equals(dto.getDefaultCategoryId()))
                    .findFirst()
                    .ifPresent(payee::setDefaultCategory);
        } else {
            payee.setDefaultCategory(null);
        }

        Payee saved = payeeService.savePayee(payee);
        return ResponseEntity.ok(DtoMapper.toPayeeDTO(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayee(@PathVariable Long id) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Payee payee = payeeService.getAllPayees().stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (payee == null) return ResponseEntity.notFound().build();

        payeeService.deletePayee(payee);
        return ResponseEntity.ok().build();
    }
}
