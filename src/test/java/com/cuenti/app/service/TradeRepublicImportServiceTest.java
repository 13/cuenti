package com.cuenti.app.service;

import com.cuenti.app.model.Account;
import com.cuenti.app.model.Asset;
import com.cuenti.app.model.Payee;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import com.cuenti.app.repository.PayeeRepository;
import com.cuenti.app.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
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

    private final User user = new User();

    private static final String HEADER = "\"datetime\",\"date\",\"account_type\",\"category\",\"type\",\"asset_class\",\"name\",\"symbol\",\"shares\",\"price\",\"amount\",\"fee\",\"tax\",\"currency\",\"original_amount\",\"original_currency\",\"fx_rate\",\"description\",\"transaction_id\",\"counterparty_name\",\"counterparty_iban\",\"payment_reference\",\"mcc_code\"\n";

    @BeforeEach
    void setUp() {
        service = new TradeRepublicImportService(transactionService, transactionRepository, payeeRepository, assetService);
    }

    private static Account account(long id) {
        Account account = new Account();
        account.setId(id);
        return account;
    }

    private List<Transaction> importRows(String rows) throws Exception {
        lenient().when(transactionRepository.existsByNumberForUser(anyString(), any())).thenReturn(false);
        service.importCsv(new ByteArrayInputStream((HEADER + rows).getBytes(StandardCharsets.UTF_8)), account(1L), account(2L), user);
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionService, atLeast(0)).saveTransaction(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void interestBooksGrossIncomeAndWithheldTaxSeparately() throws Exception {
        List<Transaction> saved = importRows("\"2026-09-01T06:13:09.240748Z\",\"2026-09-01\",\"DEFAULT\",\"CASH\",\"INTEREST_PAYMENT\",\"\",\"\",\"\",\"\",\"\",\"8.850000\",\"\",\"-2.30\",\"EUR\",\"\",\"\",\"\",\"Interest payment for payout collection x\",\"int-id\",\"\",\"\",\"\",\"\"\n");

        assertEquals(2, saved.size());
        Transaction interest = saved.get(0);
        assertEquals(Transaction.TransactionType.INCOME, interest.getType());
        assertEquals(0, interest.getAmount().compareTo(new BigDecimal("8.85")));
        assertEquals("Trade Republic", interest.getPayee());
        assertEquals(Transaction.PaymentMethod.INTEREST, interest.getPaymentMethod());
        assertEquals("int-id", interest.getNumber());

        Transaction tax = saved.get(1);
        assertEquals(Transaction.TransactionType.EXPENSE, tax.getType());
        assertEquals(0, tax.getAmount().compareTo(new BigDecimal("2.30")));
        assertEquals("int-id-tax", tax.getNumber());
        assertEquals("Trade Republic", tax.getPayee());
    }

    @Test
    void internationalCardPaymentIsACardTransaction() throws Exception {
        List<Transaction> saved = importRows("\"2026-08-12T15:23:40.242370Z\",\"2026-08-12\",\"DEFAULT\",\"CASH\",\"CARD_TRANSACTION_INTERNATIONAL\",\"\",\"ANTHROPIC* CLAUDE SUB\",\"\",\"\",\"\",\"-21.960000\",\"\",\"\",\"EUR\",\"\",\"\",\"\",\"ANTHROPIC* CLAUDE SUB\",\"card-id\",\"\",\"\",\"\",\"5734\"\n");

        assertEquals(1, saved.size());
        assertEquals(Transaction.PaymentMethod.CARD_TRANSACTION, saved.get(0).getPaymentMethod());
        assertEquals(Transaction.TransactionType.EXPENSE, saved.get(0).getType());
    }

    @Test
    void savebackIsARewardFromTradeRepublic() throws Exception {
        List<Transaction> saved = importRows("\"2026-09-01T01:45:40.444659Z\",\"2026-09-01\",\"DEFAULT\",\"CASH\",\"BENEFITS_SAVEBACK\",\"FUND\",\"FTSE All-World USD (Acc)\",\"IE00BK5BQT80\",\"\",\"\",\"3.370000\",\"\",\"\",\"EUR\",\"\",\"\",\"\",\" Cash reward allocation x\",\"sb-id\",\"\",\"\",\"\",\"\"\n");

        assertEquals(1, saved.size());
        assertEquals(Transaction.TransactionType.INCOME, saved.get(0).getType());
        assertEquals("Trade Republic", saved.get(0).getPayee());
        assertEquals(Transaction.PaymentMethod.REWARD, saved.get(0).getPaymentMethod());
    }

    @Test
    void bookingDayFollowsTheDateColumnInGermanTime() throws Exception {
        // 22:30 UTC is already the next day in Germany; the export's date column says so too.
        List<Transaction> saved = importRows("\"2026-08-10T22:30:00Z\",\"2026-08-11\",\"DEFAULT\",\"CASH\",\"CARD_TRANSACTION\",\"\",\"BAR\",\"\",\"\",\"\",\"-5.00\",\"\",\"\",\"EUR\",\"\",\"\",\"\",\"BAR\",\"late-id\",\"\",\"\",\"\",\"\"\n");

        assertEquals(LocalDateTime.of(2026, 8, 11, 0, 30), saved.get(0).getTransactionDate());
    }

    @Test
    void buyOfUnknownSecurityCreatesTheAsset() throws Exception {
        when(assetService.getAllAssets()).thenReturn(List.of());
        when(assetService.saveAsset(any(Asset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Transaction> saved = importRows("\"2026-09-02T17:08:04.233Z\",\"2026-09-02\",\"DEFAULT\",\"TRADING\",\"BUY\",\"FUND\",\"FTSE All-World USD (Acc)\",\"IE00BK5BQT80\",\"1.4992810000\",\"166.7465000000\",\"-250.00\",\"\",\"\",\"EUR\",\"\",\"\",\"\",\"Savings plan execution IE00BK5BQT80 Vanguard, quantity: 1.499281\",\"buy-id\",\"\",\"\",\"\",\"\"\n");

        Asset asset = saved.get(0).getAsset();
        assertNotNull(asset);
        assertEquals("IE00BK5BQT80", asset.getSymbol());
        assertEquals("FTSE All-World USD (Acc)", asset.getName());
        assertEquals(Asset.AssetType.ETF, asset.getType());
        assertEquals(0, saved.get(0).getUnits().compareTo(new BigDecimal("1.499281")));
    }

    @Test
    void failingRowIsCountedAndDoesNotStopTheImport() throws Exception {
        when(transactionRepository.existsByNumberForUser(anyString(), any())).thenReturn(false);
        doAnswer(invocation -> {
            if ("bad".equals(invocation.getArgument(0, Transaction.class).getNumber())) {
                throw new IllegalArgumentException("boom");
            }
            return null;
        }).when(transactionService).saveTransaction(any(Transaction.class));

        String rows = "\"2026-08-11T11:20:53Z\",\"2026-08-11\",\"DEFAULT\",\"CASH\",\"CARD_TRANSACTION\",\"\",\"A\",\"\",\"\",\"\",\"-1.00\",\"\",\"\",\"EUR\",\"\",\"\",\"\",\"A\",\"bad\",\"\",\"\",\"\",\"\"\n"
                + "\"2026-08-11T11:20:53Z\",\"2026-08-11\",\"DEFAULT\",\"CASH\",\"CARD_TRANSACTION\",\"\",\"B\",\"\",\"\",\"\",\"-2.00\",\"\",\"\",\"EUR\",\"\",\"\",\"\",\"B\",\"good\",\"\",\"\",\"\",\"\"\n";
        TradeRepublicImportService.ImportResult result = service.importCsv(
                new ByteArrayInputStream((HEADER + rows).getBytes(StandardCharsets.UTF_8)), account(1L), account(2L), user);

        assertEquals(new TradeRepublicImportService.ImportResult(1, 0, 1), result);
    }

    @Test
    void importedPayeesAreCreated() throws Exception {
        when(payeeRepository.existsByUserAndNameIgnoreCase(user, "EVIVA SPORT")).thenReturn(false);

        importRows("\"2026-08-09T14:54:38Z\",\"2026-08-09\",\"DEFAULT\",\"CASH\",\"CARD_TRANSACTION\",\"\",\"EVIVA SPORT\",\"\",\"\",\"\",\"-28.00\",\"\",\"\",\"EUR\",\"\",\"\",\"\",\"EVIVA SPORT\",\"p-id\",\"\",\"\",\"\",\"\"\n");

        ArgumentCaptor<Payee> payee = ArgumentCaptor.forClass(Payee.class);
        verify(payeeRepository).save(payee.capture());
        assertEquals("EVIVA SPORT", payee.getValue().getName());
        assertEquals(user, payee.getValue().getUser());
    }

    @Test
    void sameCashAndAssetAccountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.importCsv(
                new ByteArrayInputStream(HEADER.getBytes(StandardCharsets.UTF_8)), account(1L), account(1L), user));
    }

    @Test
    void importsTransactionExportAndSkipsDuplicateTransactionId() throws Exception {
        Set<String> seenTxIds = new HashSet<>();
        when(transactionRepository.existsByNumberForUser(anyString(), any())).thenAnswer(invocation ->
                seenTxIds.contains(invocation.getArgument(0, String.class)));
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

        TradeRepublicImportService.ImportResult result = service.importCsv(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), account(1L), account(2L), user);
        assertEquals(new TradeRepublicImportService.ImportResult(1, 1, 0), result);

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
        when(transactionRepository.existsByNumberForUser(anyString(), any())).thenReturn(false);

        Asset asset = new Asset();
        asset.setSymbol("VWCE.DE");
        asset.setName("Vanguard FTSE All-World UCITS ETF");
        when(assetService.getAllAssets()).thenReturn(List.of(asset));

        String csv = "\"datetime\",\"date\",\"account_type\",\"category\",\"type\",\"asset_class\",\"name\",\"symbol\",\"shares\",\"price\",\"amount\",\"fee\",\"tax\",\"currency\",\"original_amount\",\"original_currency\",\"fx_rate\",\"description\",\"transaction_id\",\"counterparty_name\",\"counterparty_iban\",\"payment_reference\",\"mcc_code\"\n"
                + "\"2026-02-02T17:21:41.931Z\",\"2026-02-02\",\"DEFAULT\",\"TRADING\",\"BUY\",\"FUND\",\"FTSE All-World USD (Acc)\",\"IE00BK5BQT80\",\"1.6828210000\",\"148.5600000000\",\"-250.00\",\"-1.00\",\"\",\"EUR\",\"\",\"\",\"\",\"Savings plan execution IE00BK5BQT80 Vanguard Funds PLC - Vanguard FTSE All-World UCITS ETF (USD) Accumulating, quantity: 1.682821\",\"buy-id\",\"\",\"\",\"\",\"\"\n";

        Account cash = account(1L);
        Account assets = account(2L);
        service.importCsv(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), cash, assets, user);

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

