package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.Currency;
import com.cuenti.homebanking.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CurrencyService {
    private final CurrencyRepository currencyRepository;

    public List<Currency> getAllCurrencies() {
        return currencyRepository.findAll();
    }

    @Transactional
    public Currency saveCurrency(Currency currency) {
        return currencyRepository.save(currency);
    }

    @Transactional
    public void deleteCurrency(Currency currency) {
        currencyRepository.delete(currency);
    }
}
