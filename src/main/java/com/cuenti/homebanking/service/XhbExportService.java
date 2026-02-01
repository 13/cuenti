package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.*;
import com.cuenti.homebanking.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class XhbExportService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final PayeeRepository payeeRepository;
    private final CurrencyRepository currencyRepository;
    private final ScheduledTransactionRepository scheduledRepository;

    public byte[] exportXhb(User user) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element root = doc.createElement("homebank");
        root.setAttribute("v", "1.6");
        doc.appendChild(root);

        // 1. Properties
        Element props = doc.createElement("properties");
        props.setAttribute("title", user.getUsername());
        props.setAttribute("curr", "1");
        root.appendChild(props);

        // 2. Currencies (Mapping to keys)
        Map<String, String> curToKey = new HashMap<>();
        List<Currency> currencies = currencyRepository.findAll();
        for (int i = 0; i < currencies.size(); i++) {
            Currency c = currencies.get(i);
            String key = String.valueOf(i + 1);
            curToKey.put(c.getCode(), key);
            
            Element cur = doc.createElement("cur");
            cur.setAttribute("key", key);
            cur.setAttribute("iso", c.getCode());
            cur.setAttribute("name", c.getName());
            cur.setAttribute("symb", c.getSymbol());
            cur.setAttribute("dchar", c.getDecimalChar());
            cur.setAttribute("gchar", c.getGroupingChar());
            cur.setAttribute("frac", String.valueOf(c.getFracDigits()));
            root.appendChild(cur);
        }

        // 3. Categories
        Map<Long, String> catToKey = new HashMap<>();
        List<Category> categories = categoryRepository.findAll();
        for (Category c : categories) {
            String key = String.valueOf(c.getId());
            catToKey.put(c.getId(), key);
            Element cat = doc.createElement("cat");
            cat.setAttribute("key", key);
            cat.setAttribute("name", c.getName());
            cat.setAttribute("flags", c.getType() == Category.CategoryType.INCOME ? "2" : "0");
            if (c.getParent() != null) {
                cat.setAttribute("parent", String.valueOf(c.getParent().getId()));
            }
            root.appendChild(cat);
        }

        // 4. Payees
        Map<String, String> payeeToKey = new HashMap<>();
        List<Payee> payees = payeeRepository.findAll();
        for (int i = 0; i < payees.size(); i++) {
            Payee p = payees.get(i);
            String key = String.valueOf(i + 1);
            payeeToKey.put(p.getName(), key);
            Element pay = doc.createElement("pay");
            pay.setAttribute("key", key);
            pay.setAttribute("name", p.getName());
            root.appendChild(pay);
        }

        // 5. Accounts
        Map<Long, String> accToKey = new HashMap<>();
        List<Account> accounts = accountRepository.findByUser(user);
        for (int i = 0; i < accounts.size(); i++) {
            Account acc = accounts.get(i);
            String key = String.valueOf(i + 1);
            accToKey.put(acc.getId(), key);
            Element account = doc.createElement("account");
            account.setAttribute("key", key);
            account.setAttribute("name", acc.getAccountName());
            account.setAttribute("bankname", acc.getInstitution() != null ? acc.getInstitution() : "");
            account.setAttribute("initial", acc.getStartBalance().toString());
            account.setAttribute("curr", curToKey.getOrDefault(acc.getCurrency(), "1"));
            
            String type = "1"; // Bank
            if (acc.getAccountType() == Account.AccountType.CASH) type = "2";
            else if (acc.getAccountType() == Account.AccountType.ASSET) type = "3";
            account.setAttribute("type", type);
            
            root.appendChild(account);
        }

        // 6. Transactions (<ope>)
        List<Transaction> transactions = transactionRepository.findByUser(user);
        LocalDate homebankEpoch = LocalDate.of(1, 1, 1);
        for (Transaction t : transactions) {
            Element ope = doc.createElement("ope");
            long days = ChronoUnit.DAYS.between(homebankEpoch, t.getTransactionDate().toLocalDate()) + 1;
            ope.setAttribute("date", String.valueOf(days));
            
            BigDecimal amount = t.getAmount();
            if (t.getType() == Transaction.TransactionType.EXPENSE) {
                amount = amount.negate();
            }
            
            Account mainAcc = (t.getType() == Transaction.TransactionType.INCOME) ? t.getToAccount() : t.getFromAccount();
            if (mainAcc == null) continue;

            ope.setAttribute("amount", amount.toString());
            ope.setAttribute("account", accToKey.get(mainAcc.getId()));
            
            if (t.getType() == Transaction.TransactionType.TRANSFER && t.getToAccount() != null) {
                ope.setAttribute("dst_account", accToKey.get(t.getToAccount().getId()));
            }
            
            if (t.getPayee() != null) {
                ope.setAttribute("payee", payeeToKey.get(t.getPayee()));
            }
            
            if (t.getCategory() != null) {
                ope.setAttribute("category", catToKey.get(t.getCategory().getId()));
            }
            
            ope.setAttribute("wording", t.getMemo() != null ? t.getMemo() : "");
            ope.setAttribute("tags", t.getTags() != null ? t.getTags() : "");
            
            root.appendChild(ope);
        }

        // 7. Schedulers (<fav>)
        List<ScheduledTransaction> scheduled = scheduledRepository.findByUser(user);
        for (ScheduledTransaction st : scheduled) {
            Element fav = doc.createElement("fav");
            
            BigDecimal amount = st.getAmount();
            if (st.getType() == Transaction.TransactionType.EXPENSE) {
                amount = amount.negate();
            }
            fav.setAttribute("amount", amount.toString());
            
            Account mainAcc = (st.getType() == Transaction.TransactionType.INCOME) ? st.getToAccount() : st.getFromAccount();
            if (mainAcc != null) {
                fav.setAttribute("account", accToKey.get(mainAcc.getId()));
            }
            if (st.getType() == Transaction.TransactionType.TRANSFER && st.getToAccount() != null) {
                fav.setAttribute("dst_account", accToKey.get(st.getToAccount().getId()));
            }

            long days = ChronoUnit.DAYS.between(homebankEpoch, st.getNextOccurrence().toLocalDate()) + 1;
            fav.setAttribute("nextdate", String.valueOf(days));
            
            String unit = "2"; // Monthly default
            switch (st.getRecurrencePattern()) {
                case DAILY: unit = "1"; break;
                case YEARLY: unit = "3"; break;
            }
            fav.setAttribute("unit", unit);
            fav.setAttribute("every", String.valueOf(st.getRecurrenceValue()));
            
            if (st.getPayee() != null) fav.setAttribute("payee", payeeToKey.get(st.getPayee()));
            if (st.getCategory() != null) fav.setAttribute("category", catToKey.get(st.getCategory().getId()));
            fav.setAttribute("wording", st.getMemo() != null ? st.getMemo() : "");
            fav.setAttribute("tags", st.getTags() != null ? st.getTags() : "");
            
            root.appendChild(fav);
        }

        // Transform to XML bytes
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(out));
        return out.toByteArray();
    }
}
