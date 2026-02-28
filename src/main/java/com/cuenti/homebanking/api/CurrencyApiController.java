package com.cuenti.homebanking.api;

import com.cuenti.homebanking.api.dto.CurrencyDTO;
import com.cuenti.homebanking.api.dto.DtoMapper;
import com.cuenti.homebanking.model.Currency;
import com.cuenti.homebanking.service.CurrencyService;
import com.cuenti.homebanking.service.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/currencies")
@RequiredArgsConstructor
public class CurrencyApiController {

    private final CurrencyService currencyService;

    @GetMapping
    public ResponseEntity<List<CurrencyDTO>> getCurrencies() {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(currencyService.getAllCurrencies().stream()
                .map(DtoMapper::toCurrencyDTO)
                .collect(Collectors.toList()));
    }

    @PostMapping
    public ResponseEntity<CurrencyDTO> createCurrency(@RequestBody CurrencyDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Currency currency = Currency.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .symbol(dto.getSymbol())
                .decimalChar(dto.getDecimalChar())
                .fracDigits(dto.getFracDigits())
                .groupingChar(dto.getGroupingChar())
                .build();

        Currency saved = currencyService.saveCurrency(currency);
        return ResponseEntity.ok(DtoMapper.toCurrencyDTO(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CurrencyDTO> updateCurrency(@PathVariable Long id, @RequestBody CurrencyDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Currency currency = currencyService.getAllCurrencies().stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (currency == null) return ResponseEntity.notFound().build();

        currency.setCode(dto.getCode());
        currency.setName(dto.getName());
        currency.setSymbol(dto.getSymbol());
        currency.setDecimalChar(dto.getDecimalChar());
        currency.setFracDigits(dto.getFracDigits());
        currency.setGroupingChar(dto.getGroupingChar());

        Currency saved = currencyService.saveCurrency(currency);
        return ResponseEntity.ok(DtoMapper.toCurrencyDTO(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCurrency(@PathVariable Long id) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Currency currency = currencyService.getAllCurrencies().stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (currency == null) return ResponseEntity.notFound().build();

        currencyService.deleteCurrency(currency);
        return ResponseEntity.ok().build();
    }
}
