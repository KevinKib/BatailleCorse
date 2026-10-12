package org.kevinkib.cardgames.presentation.support;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Proves that an action broadcasts under the monitor of its game: the first action is held inside its first
 * broadcast, a second action is started on another thread, and the second must not complete until the first has
 * finished broadcasting. Without the monitor the second action mutates and broadcasts in between, so its state
 * would reach the seats before the state of the first one.
 */
public final class ActionUnderMonitorProbe {

    @FunctionalInterface
    public interface Action {
        void run() throws Exception;
    }

    /** Messaging that parks the first send until released. */
    public static final class BlockingMessaging extends RecordingMessaging {
        public final CountDownLatch insideFirstSend = new CountDownLatch(1);
        public final CountDownLatch release = new CountDownLatch(1);
        private boolean first = true;

        private void parkOnce() {
            boolean park;
            synchronized (this) {
                park = first;
                first = false;
            }
            if (park) {
                insideFirstSend.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public void sendToGame(String gameId, Object payload) {
            parkOnce();
            super.sendToGame(gameId, payload);
        }

        @Override
        public void sendToSeat(GameId gameId, PlayerId seat, Object payload) {
            parkOnce();
            super.sendToSeat(gameId, seat, payload);
        }
    }

    private ActionUnderMonitorProbe() {
    }

    /**
     * Returns whether {@code second} completed while {@code first} was still parked in its broadcast. Both
     * actions are always completed before returning.
     */
    public static boolean secondRanDuringFirstBroadcast(BlockingMessaging messaging, Action first, Action second)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstDone = pool.submit(() -> {
                first.run();
                return null;
            });
            if (!messaging.insideFirstSend.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("the first action never reached its broadcast");
            }
            Future<?> secondDone = pool.submit(() -> {
                second.run();
                return null;
            });
            boolean overtook;
            try {
                secondDone.get(500, TimeUnit.MILLISECONDS);
                overtook = true;
            } catch (TimeoutException e) {
                overtook = false;
            }
            messaging.release.countDown();
            firstDone.get(5, TimeUnit.SECONDS);
            secondDone.get(5, TimeUnit.SECONDS);
            return overtook;
        } finally {
            messaging.release.countDown();
            pool.shutdownNow();
        }
    }
}
