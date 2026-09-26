package com.cuenti.app.util;

import com.cuenti.app.model.Account;
import com.cuenti.app.model.Transaction;

/**
 * Which account column a transaction type books on: an expense leaves its account
 * ({@code from}), an income enters it ({@code to}), a transfer uses both. Balance
 * logic, dialogs and the transaction list all read accounts by this rule.
 */
public final class AccountSides {

    private AccountSides() {
    }

    /**
     * {@code [from, to]} for {@code type}; a single account given on the wrong side
     * (older schedules, API clients, imports) is moved over.
     */
    public static Account[] forType(Transaction.TransactionType type, Account from, Account to) {
        if (type == Transaction.TransactionType.INCOME) {
            return new Account[]{null, to != null ? to : from};
        }
        if (type == Transaction.TransactionType.EXPENSE) {
            return new Account[]{from != null ? from : to, null};
        }
        return new Account[]{from, to};
    }

    /** Applies {@link #forType} to a transaction in place. */
    public static void normalize(Transaction transaction) {
        Account[] sides = forType(transaction.getType(), transaction.getFromAccount(), transaction.getToAccount());
        transaction.setFromAccount(sides[0]);
        transaction.setToAccount(sides[1]);
    }
}
