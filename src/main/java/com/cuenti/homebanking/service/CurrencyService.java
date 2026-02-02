package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.Currency;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CurrencyService {
    private final CurrencyRepository currencyRepository;
    private final UserService userService;

    public List<Currency> getAllCurrencies() {
        String username = SecurityUtil.getAuthenticatedUsername()
            .orElseThrow(() -> new RuntimeException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        return currencyRepository.findByUser(currentUser);
    }

    public Optional<Currency> getCurrencyByCode(String code) {
        String username = SecurityUtil.getAuthenticatedUsername()
            .orElseThrow(() -> new RuntimeException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        return currencyRepository.findByUserAndCode(currentUser, code);
    }

    @Transactional
    public Currency saveCurrency(Currency currency) {
        String username = SecurityUtil.getAuthenticatedUsername()
            .orElseThrow(() -> new RuntimeException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        // Build new currency with user if not already set
        if (currency.getUser() == null) {
            currency = Currency.builder()
                .id(currency.getId())
                .user(currentUser)
                .code(currency.getCode())
                .name(currency.getName())
                .symbol(currency.getSymbol())
                .decimalChar(currency.getDecimalChar())
                .fracDigits(currency.getFracDigits())
                .groupingChar(currency.getGroupingChar())
                .build();
        }
        return currencyRepository.save(currency);
    }

    @Transactional
    public void deleteCurrency(Currency currency) {
        String username = SecurityUtil.getAuthenticatedUsername()
            .orElseThrow(() -> new RuntimeException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        // Verify ownership
        Currency toDelete = currencyRepository.findByIdAndUser(currency.getId(), currentUser)
            .orElseThrow(() -> new RuntimeException("Currency not found or access denied"));
        currencyRepository.delete(toDelete);
    }
}
