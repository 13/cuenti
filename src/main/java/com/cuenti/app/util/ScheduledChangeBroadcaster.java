package com.cuenti.app.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-crossing notifier for scheduled-transaction changes. Service-layer
 * mutations broadcast the owning user's id; every attached MainLayout listens
 * and repaints its nav badge (covers other browser tabs via @Push).
 */
public final class ScheduledChangeBroadcaster {

    private static final Map<Long, Set<Runnable>> LISTENERS = new ConcurrentHashMap<>();

    private ScheduledChangeBroadcaster() {
    }

    /** Register a listener for a user's schedule changes; returns an unregister handle. */
    public static Runnable register(Long userId, Runnable listener) {
        LISTENERS.compute(userId, (id, set) -> {
            if (set == null) {
                set = ConcurrentHashMap.newKeySet();
            }
            set.add(listener);
            return set;
        });
        return () -> LISTENERS.computeIfPresent(userId, (id, set) -> {
            set.remove(listener);
            return set.isEmpty() ? null : set;
        });
    }

    /**
     * Notify all listeners of the given user, deferred until after the current
     * transaction commits so listeners never read uncommitted state.
     */
    public static void broadcast(Long userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fire(userId);
                }
            });
        } else {
            fire(userId);
        }
    }

    private static void fire(Long userId) {
        Set<Runnable> set = LISTENERS.get(userId);
        if (set != null) {
            set.forEach(Runnable::run);
        }
    }
}
