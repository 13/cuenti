package com.cuenti.app.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledChangeBroadcasterTest {

    @AfterEach
    void clearTxSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void broadcast_reachesOnlyListenersOfThatUser() {
        AtomicInteger userA = new AtomicInteger();
        AtomicInteger userB = new AtomicInteger();
        Runnable unregisterA = ScheduledChangeBroadcaster.register(1L, userA::incrementAndGet);
        Runnable unregisterB = ScheduledChangeBroadcaster.register(2L, userB::incrementAndGet);

        ScheduledChangeBroadcaster.broadcast(1L);

        assertThat(userA.get()).isEqualTo(1);
        assertThat(userB.get()).isZero();

        unregisterA.run();
        unregisterB.run();
    }

    @Test
    void unregisteredListener_isNotCalledAgain() {
        AtomicInteger calls = new AtomicInteger();
        Runnable unregister = ScheduledChangeBroadcaster.register(3L, calls::incrementAndGet);

        ScheduledChangeBroadcaster.broadcast(3L);
        unregister.run();
        ScheduledChangeBroadcaster.broadcast(3L);

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void broadcastInsideTransaction_isDeferredUntilCommit() {
        AtomicInteger calls = new AtomicInteger();
        Runnable unregister = ScheduledChangeBroadcaster.register(4L, calls::incrementAndGet);

        TransactionSynchronizationManager.initSynchronization();
        ScheduledChangeBroadcaster.broadcast(4L);
        assertThat(calls.get()).isZero();

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        assertThat(calls.get()).isEqualTo(1);

        unregister.run();
    }

    @Test
    void repeatedBroadcastsInOneTransaction_coalesceToSingleNotification() {
        AtomicInteger calls = new AtomicInteger();
        Runnable unregister = ScheduledChangeBroadcaster.register(5L, calls::incrementAndGet);

        TransactionSynchronizationManager.initSynchronization();
        ScheduledChangeBroadcaster.broadcast(5L);
        ScheduledChangeBroadcaster.broadcast(5L);
        ScheduledChangeBroadcaster.broadcast(5L);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        assertThat(calls.get()).isEqualTo(1);

        unregister.run();
    }
}
