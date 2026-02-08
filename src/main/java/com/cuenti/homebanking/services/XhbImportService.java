package com.cuenti.homebanking.services;

import com.cuenti.homebanking.data.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for importing Homebank XHB files.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class XhbImportService {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final PayeeService payeeService;
    private final TagService tagService;
    private final CurrencyRepository currencyRepository;
    private final UserService userService;

    @Transactional
    public void importXhb(InputStream inputStream, User user) throws Exception {
        log.info("Starting XHB import for user: {}", user.getUsername());

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(inputStream);

        Map<String, String> currencyMap = new HashMap<>();
        Map<String, String> groupMap = new HashMap<>();
        Map<String, Category> categoryMap = new HashMap<>();
        Map<String, Payee> payeeMap = new HashMap<>();
        Map<String, Account> accountMap = new HashMap<>();

        // 1. Import Currencies
        NodeList curList = doc.getElementsByTagName("cur");
        for (int i = 0; i < curList.getLength(); i++) {
            Element el = (Element) curList.item(i);
            String key = el.getAttribute("key");
            String iso = el.getAttribute("iso");
            currencyMap.put(key, iso);

            if (iso != null && !iso.isEmpty() && !iso.equals("BTC")) {
                if (!currencyRepository.findByUserAndCode(user, iso).isPresent()) {
                    Currency c = Currency.builder()
                            .user(user)
                            .code(iso)
                            .name(el.getAttribute("name"))
                            .symbol(el.getAttribute("symb"))
                            .decimalChar(el.getAttribute("dchar").isEmpty() ? "," : el.getAttribute("dchar"))
                            .groupingChar(el.getAttribute("gchar").isEmpty() ? "." : el.getAttribute("gchar"))
                            .fracDigits(parseFracDigits(el.getAttribute("frac")))
                            .build();
                    currencyRepository.save(c);
                }
            }
        }

        // 2. Import Groups
        NodeList grpList = doc.getElementsByTagName("grp");
        for (int i = 0; i < grpList.getLength(); i++) {
            Element el = (Element) grpList.item(i);
            groupMap.put(el.getAttribute("key"), el.getAttribute("name"));
        }

        // 3. Import Categories
        NodeList catList = doc.getElementsByTagName("cat");
        for (int i = 0; i < catList.getLength(); i++) {
            Element el = (Element) catList.item(i);
            String key = el.getAttribute("key");
            int flags = parseFlags(el.getAttribute("flags"));

            Category cat = Category.builder()
                    .user(user)
                    .name(el.getAttribute("name"))
                    .type((flags & 2) != 0 ? Category.CategoryType.INCOME : Category.CategoryType.EXPENSE)
                    .build();
            categoryMap.put(key, cat);
        }
        // Set parent relationships and save
        for (int i = 0; i < catList.getLength(); i++) {
            Element el = (Element) catList.item(i);
            String key = el.getAttribute("key");
            String parentKey = el.getAttribute("parent");
            Category cat = categoryMap.get(key);
            if (parentKey != null && !parentKey.isEmpty() && categoryMap.containsKey(parentKey)) {
                cat.setParent(categoryMap.get(parentKey));
            }
            categoryMap.put(key, categoryService.saveCategory(cat));
        }

        // 4. Import Payees
        NodeList payList = doc.getElementsByTagName("pay");
        for (int i = 0; i < payList.getLength(); i++) {
            Element el = (Element) payList.item(i);
            String key = el.getAttribute("key");
            Payee p = Payee.builder()
                    .user(user)
                    .name(el.getAttribute("name"))
                    .build();
            payeeMap.put(key, payeeService.savePayee(p));
        }

        // 5. Import Accounts
        NodeList accList = doc.getElementsByTagName("account");
        for (int i = 0; i < accList.getLength(); i++) {
            Element el = (Element) accList.item(i);
            String key = el.getAttribute("key");
            String typeStr = el.getAttribute("type");

            Account.AccountType type = Account.AccountType.BANK;
            if ("2".equals(typeStr)) type = Account.AccountType.CASH;
            else if ("3".equals(typeStr)) type = Account.AccountType.ASSET;

            BigDecimal initial = parseAmount(el.getAttribute("initial"));

            Account acc = Account.builder()
                    .user(user)
                    .accountName(el.getAttribute("name"))
                    .accountNumber("HB-" + key)
                    .institution(el.getAttribute("bankname"))
                    .accountType(type)
                    .accountGroup(groupMap.get(el.getAttribute("grp")))
                    .currency(currencyMap.getOrDefault(el.getAttribute("curr"), "EUR"))
                    .startBalance(initial)
                    .balance(initial)
                    .build();

            accountMap.put(key, accountService.saveAccount(acc));
        }

        // 6. Import Transactions
        NodeList opeList = doc.getElementsByTagName("ope");
        int imported = 0;
        for (int i = 0; i < opeList.getLength(); i++) {
            Element el = (Element) opeList.item(i);

            BigDecimal rawAmount = parseAmount(el.getAttribute("amount"));
            Account account = accountMap.get(el.getAttribute("account"));
            Account dstAccount = accountMap.get(el.getAttribute("dst_account"));

            // Skip positive side of transfers to avoid duplicates
            if (dstAccount != null && rawAmount.compareTo(BigDecimal.ZERO) > 0) {
                continue;
            }

            BigDecimal amount = rawAmount.abs();
            long days = Long.parseLong(el.getAttribute("date"));
            LocalDateTime date = LocalDate.of(1, 1, 1).plusDays(days - 1).atStartOfDay();

            Transaction.TransactionType type;
            Account fromAcc = null;
            Account toAcc = null;

            if (dstAccount != null) {
                type = Transaction.TransactionType.TRANSFER;
                fromAcc = account;
                toAcc = dstAccount;
            } else if (rawAmount.compareTo(BigDecimal.ZERO) < 0) {
                type = Transaction.TransactionType.EXPENSE;
                fromAcc = account;
            } else {
                type = Transaction.TransactionType.INCOME;
                toAcc = account;
            }

Transaction t = Transaction.builder()
                    .type(type)
                    .amount(amount)
                    .transactionDate(date)
                    .fromAccount(fromAcc)
                    .toAccount(toAcc)
                    .category(categoryMap.get(el.getAttribute("category")))
                    .payee(payeeMap.get(el.getAttribute("payee")) != null ?
                           payeeMap.get(el.getAttribute("payee")).getName() : null)
                    .memo(el.getAttribute("wording"))
                    .build();

            transactionService.saveTransaction(t);
            imported++;
        }

        log.info("XHB import complete: {} transactions imported for user {}", imported, user.getUsername());
    }

    private int parseFracDigits(String value) {
        try {
            return value.isEmpty() ? 2 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    private int parseFlags(String value) {
        try {
            return value.isEmpty() ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private BigDecimal parseAmount(String value) {
        try {
            return value.isEmpty() ? BigDecimal.ZERO : new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
