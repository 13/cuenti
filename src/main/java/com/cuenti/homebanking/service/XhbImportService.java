package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.*;
import com.cuenti.homebanking.repository.*;
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
import java.util.HashSet;
import java.util.Set;

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
    private final ScheduledTransactionService scheduledService;

    @Transactional
    public void importXhb(InputStream inputStream, User user) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(inputStream);

        // Map Homebank keys to our entities
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
                if (currencyRepository.findByCode(iso) == null) {
                    Currency c = new Currency();
                    c.setCode(iso);
                    c.setName(el.getAttribute("name"));
                    c.setSymbol(el.getAttribute("symb"));
                    c.setDecimalChar(el.getAttribute("dchar").isEmpty() ? "," : el.getAttribute("dchar"));
                    c.setGroupingChar(el.getAttribute("gchar").isEmpty() ? "." : el.getAttribute("gchar"));
                    try {
                        c.setFracDigits(Integer.parseInt(el.getAttribute("frac").isEmpty() ? "2" : el.getAttribute("frac")));
                    } catch (NumberFormatException e) {
                        c.setFracDigits(2);
                    }
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

        // 3. Import Categories (Hierarchy)
        NodeList catList = doc.getElementsByTagName("cat");
        for (int i = 0; i < catList.getLength(); i++) {
            Element el = (Element) catList.item(i);
            String key = el.getAttribute("key");
            int flags = 0;
            try {
                String flagsStr = el.getAttribute("flags");
                flags = flagsStr.isEmpty() ? 0 : Integer.parseInt(flagsStr);
            } catch (NumberFormatException e) {}
            
            Category cat = Category.builder()
                    .name(el.getAttribute("name"))
                    .type((flags & 2) != 0 ? Category.CategoryType.INCOME : Category.CategoryType.EXPENSE)
                    .build();
            categoryMap.put(key, cat);
        }
        for (int i = 0; i < catList.getLength(); i++) {
            Element el = (Element) catList.item(i);
            String key = el.getAttribute("key");
            String parentKey = el.getAttribute("parent");
            Category cat = categoryMap.get(key);
            if (parentKey != null && !parentKey.isEmpty()) {
                cat.setParent(categoryMap.get(parentKey));
            }
            categoryMap.put(key, categoryService.saveCategory(cat));
        }

        // 4. Import Tags from Tag Definitions
        NodeList tagDefList = doc.getElementsByTagName("tag");
        for (int i = 0; i < tagDefList.getLength(); i++) {
            Element el = (Element) tagDefList.item(i);
            ensureTagExists(el.getAttribute("name"));
        }

        // 5. Import Payees
        NodeList payList = doc.getElementsByTagName("pay");
        for (int i = 0; i < payList.getLength(); i++) {
            Element el = (Element) payList.item(i);
            String key = el.getAttribute("key");
            Payee p = Payee.builder()
                    .name(el.getAttribute("name"))
                    .build();
            payeeMap.put(key, payeeService.savePayee(p));
        }

        // 6. Import Accounts
        NodeList accList = doc.getElementsByTagName("account");
        for (int i = 0; i < accList.getLength(); i++) {
            Element el = (Element) accList.item(i);
            String key = el.getAttribute("key");
            String typeStr = el.getAttribute("type");
            
            Account.AccountType type = Account.AccountType.BANK;
            if ("2".equals(typeStr)) type = Account.AccountType.CASH;
            else if ("3".equals(typeStr)) type = Account.AccountType.ASSET;

            Account acc = Account.builder()
                    .user(user)
                    .accountName(el.getAttribute("name"))
                    .accountNumber("HB-" + key)
                    .institution(el.getAttribute("bankname"))
                    .accountType(type)
                    .accountGroup(groupMap.get(el.getAttribute("grp")))
                    .currency(currencyMap.getOrDefault(el.getAttribute("curr"), "EUR"))
                    .startBalance(new BigDecimal(el.getAttribute("initial").isEmpty() ? "0" : el.getAttribute("initial")))
                    .balance(new BigDecimal(el.getAttribute("initial").isEmpty() ? "0" : el.getAttribute("initial")))
                    .build();
            
            accountMap.put(key, accountService.saveAccount(acc));
        }

        // 7. Import Operations (Transactions)
        NodeList opeList = doc.getElementsByTagName("ope");
        int transactionSortOrder = 0;
        for (int i = 0; i < opeList.getLength(); i++) {
            Element el = (Element) opeList.item(i);
            
            BigDecimal rawAmount = new BigDecimal(el.getAttribute("amount"));
            Account account = accountMap.get(el.getAttribute("account"));
            Account dstAccount = accountMap.get(el.getAttribute("dst_account"));
            
            // FIX: Skip duplicate transfers. Homebank pairs transfers. 
            // We ONLY process the negative side (the source) to avoid doubling history and balances.
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

            String rawTags = el.getAttribute("tags");
            String tags = convertSpaceTagsToComma(rawTags);
            importTagsFromString(tags);

            Transaction t = Transaction.builder()
                    .type(type)
                    .amount(amount)
                    .transactionDate(date)
                    .fromAccount(fromAcc)
                    .toAccount(toAcc)
                    .payee(el.hasAttribute("payee") ? (payeeMap.get(el.getAttribute("payee")) != null ? payeeMap.get(el.getAttribute("payee")).getName() : null) : null)
                    .category(categoryMap.get(el.getAttribute("category")))
                    .memo(el.getAttribute("wording"))
                    .tags(tags)
                    .sortOrder(transactionSortOrder++)
                    .status(Transaction.TransactionStatus.COMPLETED)
                    .build();

            transactionService.saveTransaction(t);
        }

        // 8. Import Schedulers (<fav> tags)
        NodeList favList = doc.getElementsByTagName("fav");
        for (int i = 0; i < favList.getLength(); i++) {
            Element el = (Element) favList.item(i);
            
            BigDecimal rawAmount = new BigDecimal(el.getAttribute("amount"));
            long days = Long.parseLong(el.getAttribute("nextdate"));
            LocalDateTime nextDate = LocalDate.of(1, 1, 1).plusDays(days - 1).atStartOfDay();
            
            Account account = accountMap.get(el.getAttribute("account"));
            Account dstAccount = accountMap.get(el.getAttribute("dst_account"));
            
            Transaction.TransactionType type;
            Account fromAcc = null;
            Account toAcc = null;

            if (dstAccount != null) {
                type = Transaction.TransactionType.TRANSFER;
                if (rawAmount.compareTo(BigDecimal.ZERO) < 0) {
                    fromAcc = account;
                    toAcc = dstAccount;
                } else {
                    fromAcc = dstAccount;
                    toAcc = account;
                }
            } else if (rawAmount.compareTo(BigDecimal.ZERO) < 0) {
                type = Transaction.TransactionType.EXPENSE;
                fromAcc = account;
            } else {
                type = Transaction.TransactionType.INCOME;
                toAcc = account;
            }

            // Homebank unit mapping: 1=Day, 2=Month (observed), 3=Year (observed)
            ScheduledTransaction.RecurrencePattern pattern = ScheduledTransaction.RecurrencePattern.MONTHLY;
            String unit = el.getAttribute("unit");
            switch (unit) {
                case "1": pattern = ScheduledTransaction.RecurrencePattern.DAILY; break;
                case "2": pattern = ScheduledTransaction.RecurrencePattern.MONTHLY; break;
                case "3": pattern = ScheduledTransaction.RecurrencePattern.YEARLY; break;
            }

            String rawTags = el.getAttribute("tags");
            String tags = convertSpaceTagsToComma(rawTags);
            importTagsFromString(tags);

            ScheduledTransaction st = ScheduledTransaction.builder()
                    .user(user)
                    .type(type)
                    .amount(rawAmount.abs())
                    .nextOccurrence(nextDate)
                    .fromAccount(fromAcc)
                    .toAccount(toAcc)
                    .payee(el.hasAttribute("payee") ? (payeeMap.get(el.getAttribute("payee")) != null ? payeeMap.get(el.getAttribute("payee")).getName() : null) : null)
                    .category(categoryMap.get(el.getAttribute("category")))
                    .memo(el.getAttribute("wording"))
                    .tags(tags)
                    .recurrencePattern(pattern)
                    .recurrenceValue(Integer.parseInt(el.getAttribute("every").isEmpty() ? "1" : el.getAttribute("every")))
                    .enabled(true)
                    .build();

            scheduledService.save(st);
        }
    }

    private String convertSpaceTagsToComma(String tags) {
        if (tags == null || tags.isEmpty()) return "";
        return tags.trim().replace(" ", ",");
    }

    private void importTagsFromString(String commaSeparatedTags) {
        if (commaSeparatedTags == null || commaSeparatedTags.isEmpty()) return;
        String[] tagNames = commaSeparatedTags.split(",");
        for (String name : tagNames) {
            ensureTagExists(name.trim());
        }
    }

    private void ensureTagExists(String tagName) {
        if (tagName == null || tagName.isEmpty()) return;
        if (tagService.searchTags(tagName).stream().noneMatch(t -> t.getName().equalsIgnoreCase(tagName))) {
            tagService.saveTag(Tag.builder().name(tagName).build());
        }
    }
}
