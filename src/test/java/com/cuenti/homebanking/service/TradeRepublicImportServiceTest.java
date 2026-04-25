package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.Account;
import com.cuenti.homebanking.model.Asset;
import com.cuenti.homebanking.model.Transaction;
import com.cuenti.homebanking.repository.PayeeRepository;
import com.cuenti.homebanking.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeRepublicImportServiceTest {

    @Mock
    private TransactionService transactionService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PayeeRepository payeeRepository;

    @Mock
    private AssetService assetService;

    private TradeRepublicImportService service;

    @BeforeEach
    void setUp() {
        service = new TradeRepublicImportService(transactionService, transactionRepository, payeeRepository, assetService);
    }

    @Test
    void importsTransactionExportAndSkipsDuplicateTransactionId() throws Exception {
        Set<String> seenTxIds = new HashSet<>();
        when(transactionRepository.findByNumber(anyString())).thenAnswer(invocation -> {
            String txId = invocation.getArgument(0, String.class);
            return seenTxIds.contains(txId) ? Optional.of(new Transaction()) : Optional.empty();
        });
        doAnswer(invocation -> {
            Transaction saved = invocation.getArgument(0, Transaction.class);
            if (saved.getNumber() != null) {
                seenTxIds.add(saved.getNumber());
            }
            return null;
        }).when(transactionService).saveTransaction(any(Transaction.class));

        String csv = "\"datetime\",\"date\",\"account_type\",\"category\",\"type\",\"asset_class\",\"name\",\"symbol\",\"shares\",\"price\",\"amount\",\"fee\",\"tax\",\"currency\",\"original_amount\",\"original_currency\",\"fx_rate\",\"description\",\"transaction_id\",\"counterparty_name\",\"counterparty_iban\",\"payment_reference\",\"mcc_code\"\n"
                + "\"2026-01-13T12:39:48.855274Z\",\"2026-01-13\",\"DEFAULT\",\"CASH\",\"CARD_TRANSACTION\",\"\",\"PUR SUEDTIROL MERAN\",\"\",\"\",\"\",\"-1.920000\",\"\",\"\",\"EUR\",\"\",\"\",\"\",\"PUR SUEDTIROL MERANnull\",\"duplicate-id\",\"\",\"\",\"\",\"5499\"\n"
                + "\"2026-01-13T12:39:48.855274Z\",\"2026-01-13\",\"DEFAULT\",\"CASH\",\"CARD_TRANSACTION\",\"\",\"PUR SUEDTIROL MERAN\",\"\",\"\",\"\",\"-1.920000\",\"\",\"\",\"EUR\",\"\",\"\",\"\",\"PUR SUEDTIROL MERANnull\",\"duplicate-id\",\"\",\"\",\"\",\"5499\"\n";

        service.importCsv(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), new Account(), new Account());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionService, times(1)).saveTransaction(captor.capture());

        Transaction saved = captor.getValue();
        assertEquals("duplicate-id", saved.getNumber());
        assertEquals(Transaction.TransactionType.EXPENSE, saved.getType());
        assertEquals(0, saved.getAmount().compareTo(new BigDecimal("1.920000")));
        assertEquals("PUR SUEDTIROL MERAN", saved.getPayee());
        assertEquals(Transaction.PaymentMethod.CARD_TRANSACTION, saved.getPaymentMethod());
    }

    @Test
    void importsBuyAsTransferAndAppliesFeeToNetAmount() throws Exception {
        when(transactionRepository.findByNumber(anyString())).thenReturn(Optional.empty());

        Asset asset = new Asset();
        asset.setSymbol("VWCE.DE");
        asset.setName("Vanguard FTSE All-World UCITS ETF");
        when(assetService.getAllAssets()).thenReturn(List.of(asset));

        String csv = "\"datetime\",\"date\",\"account_type\",\"category\",\"type\",\"asset_class\",\"name\",\"symbol\",\"shares\",\"price\",\"amount\",\"fee\",\"tax\",\"currency\",\"original_amount\",\"original_currency\",\"fx_rate\",\"description\",\"transaction_id\",\"counterparty_name\",\"counterparty_iban\",\"payment_reference\",\"mcc_code\"\n"
                + "\"2026-02-02T17:21:41.931Z\",\"2026-02-02\",\"DEFAULT\",\"TRADING\",\"BUY\",\"FUND\",\"FTSE All-World USD (Acc)\",\"IE00BK5BQT80\",\"1.6828210000\",\"148.5600000000\",\"-250.00\",\"-1.00\",\"\",\"EUR\",\"\",\"\",\"\",\"Savings plan execution IE00BK5BQT80 Vanguard Funds PLC - Vanguard FTSE All-World UCITS ETF (USD) Accumulating, quantity: 1.682821\",\"buy-id\",\"\",\"\",\"\",\"\"\n";

        Account cash = new Account();
        Account assets = new Account();
        service.importCsv(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), cash, assets);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionService, times(1)).saveTransaction(captor.capture());

        Transaction saved = captor.getValue();
        assertEquals("buy-id", saved.getNumber());
        assertEquals(Transaction.TransactionType.TRANSFER, saved.getType());
        assertEquals(0, saved.getAmount().compareTo(new BigDecimal("251.00")));
        assertEquals(cash, saved.getFromAccount());
        assertEquals(assets, saved.getToAccount());
        assertEquals(Transaction.PaymentMethod.TRADE, saved.getPaymentMethod());
        assertNotNull(saved.getAsset());
        assertEquals("Vanguard FTSE All-World UCITS ETF", saved.getPayee());
        assertEquals(0, saved.getUnits().compareTo(new BigDecimal("1.6828210000")));
    }
}

