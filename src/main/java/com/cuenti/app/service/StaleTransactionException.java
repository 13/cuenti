package com.cuenti.app.service;

/**
 * A change was made against an older copy of a transaction than the one now
 * stored. The API answers it with 409 so the client can reload and decide,
 * instead of the later write silently discarding the earlier one.
 */
public class StaleTransactionException extends RuntimeException {
    public StaleTransactionException(Long id) {
        super("Transaction " + id + " was changed since this edit was made");
    }
}
