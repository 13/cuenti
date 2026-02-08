package com.cuenti.homebanking.services;

import com.cuenti.homebanking.data.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for exporting data to Homebank XHB format.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class XhbExportService {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final PayeeService payeeService;
    private final CurrencyService currencyService;

    @Transactional(readOnly = true)
    public byte[] exportXhb(User user) throws Exception {
        log.info("Starting XHB export for user: {}", user.getUsername());

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        // Root element
        Element root = doc.createElement("homebank");
        root.setAttribute("v", "1.4");
        doc.appendChild(root);

        // Maps for key assignments
        Map<String, Integer> currencyKeys = new HashMap<>();
        Map<Long, Integer> categoryKeys = new HashMap<>();
        Map<Long, Integer> payeeKeys = new HashMap<>();
        Map<Long, Integer> accountKeys = new HashMap<>();
        AtomicInteger keyCounter = new AtomicInteger(1);

        // Export currencies
        List<Currency> currencies = currencyService.getAllCurrencies();
        for (Currency c : currencies) {
            int key = keyCounter.getAndIncrement();
            currencyKeys.put(c.getCode(), key);

            Element curEl = doc.createElement("cur");
            curEl.setAttribute("key", String.valueOf(key));
            curEl.setAttribute("iso", c.getCode());
            curEl.setAttribute("name", c.getName());
            curEl.setAttribute("symb", c.getSymbol() != null ? c.getSymbol() : "");
            curEl.setAttribute("dchar", c.getDecimalChar() != null ? c.getDecimalChar() : ",");
            curEl.setAttribute("gchar", c.getGroupingChar() != null ? c.getGroupingChar() : ".");
            curEl.setAttribute("frac", String.valueOf(c.getFracDigits()));
            root.appendChild(curEl);
        }

        // Export categories
        List<Category> categories = categoryService.getAllCategories();
        for (Category cat : categories) {
            int key = keyCounter.getAndIncrement();
            categoryKeys.put(cat.getId(), key);
        }
        for (Category cat : categories) {
            Element catEl = doc.createElement("cat");
            catEl.setAttribute("key", String.valueOf(categoryKeys.get(cat.getId())));
            catEl.setAttribute("name", cat.getName());
            catEl.setAttribute("flags", cat.getType() == Category.CategoryType.INCOME ? "2" : "0");
            if (cat.getParent() != null) {
                catEl.setAttribute("parent", String.valueOf(categoryKeys.get(cat.getParent().getId())));
            }
            root.appendChild(catEl);
        }

        // Export payees
        List<Payee> payees = payeeService.getAllPayees();
        for (Payee p : payees) {
            int key = keyCounter.getAndIncrement();
            payeeKeys.put(p.getId(), key);

            Element payEl = doc.createElement("pay");
            payEl.setAttribute("key", String.valueOf(key));
            payEl.setAttribute("name", p.getName());
            root.appendChild(payEl);
        }

        // Export accounts
        List<Account> accounts = accountService.getAccountsByUser(user);
        for (Account acc : accounts) {
            int key = keyCounter.getAndIncrement();
            accountKeys.put(acc.getId(), key);

            Element accEl = doc.createElement("account");
            accEl.setAttribute("key", String.valueOf(key));
            accEl.setAttribute("name", acc.getAccountName());
            accEl.setAttribute("bankname", acc.getInstitution() != null ? acc.getInstitution() : "");
            accEl.setAttribute("type", getAccountTypeCode(acc.getAccountType()));
            if (currencyKeys.containsKey(acc.getCurrency())) {
                accEl.setAttribute("curr", String.valueOf(currencyKeys.get(acc.getCurrency())));
            }
            accEl.setAttribute("initial", acc.getStartBalance() != null ? acc.getStartBalance().toPlainString() : "0");
            root.appendChild(accEl);
        }

        // Export transactions
        List<Transaction> transactions = transactionService.getTransactionsByUser(user);
        for (Transaction t : transactions) {
            Element opeEl = doc.createElement("ope");

            // Date in Homebank format (days since 1/1/0001)
            long days = ChronoUnit.DAYS.between(LocalDate.of(1, 1, 1), t.getTransactionDate().toLocalDate()) + 1;
            opeEl.setAttribute("date", String.valueOf(days));

            // Amount (negative for expenses, positive for income)
            if (t.getType() == Transaction.TransactionType.EXPENSE) {
                opeEl.setAttribute("amount", "-" + t.getAmount().toPlainString());
            } else {
                opeEl.setAttribute("amount", t.getAmount().toPlainString());
            }

            // Account
            if (t.getFromAccount() != null && accountKeys.containsKey(t.getFromAccount().getId())) {
                opeEl.setAttribute("account", String.valueOf(accountKeys.get(t.getFromAccount().getId())));
            } else if (t.getToAccount() != null && accountKeys.containsKey(t.getToAccount().getId())) {
                opeEl.setAttribute("account", String.valueOf(accountKeys.get(t.getToAccount().getId())));
            }

            // Destination account for transfers
            if (t.getType() == Transaction.TransactionType.TRANSFER && t.getToAccount() != null) {
                opeEl.setAttribute("dst_account", String.valueOf(accountKeys.get(t.getToAccount().getId())));
            }

            // Category
            if (t.getCategory() != null && categoryKeys.containsKey(t.getCategory().getId())) {
                opeEl.setAttribute("category", String.valueOf(categoryKeys.get(t.getCategory().getId())));
            }

            // Memo
            if (t.getMemo() != null) {
                opeEl.setAttribute("wording", t.getMemo());
            }

            root.appendChild(opeEl);
        }

        // Write to byte array
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(outputStream));

        log.info("XHB export complete for user: {}", user.getUsername());
        return outputStream.toByteArray();
    }

    private String getAccountTypeCode(Account.AccountType type) {
        return switch (type) {
            case CASH -> "2";
            case ASSET -> "3";
            default -> "1"; // Bank
        };
    }
}
