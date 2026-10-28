package org.kevinkib.cardgames.kilodemerde.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.presentation.state.GameStateListener;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A timer of one kind of wait of a Kilo de merde game (the pass step, the open race, the pause between rounds). After
 * every state broadcast, whatever path changed the state, it asks the subclass for the key of the wait the game is in
 * now: nothing when the game waits for something else, in which case an armed task is cancelled. One task is armed
 * per wait; a broadcast that shows the same wait (a choice, a slap, a connection notice) keeps it. A different key,
 * a new game object under the same id (Play again) or an eviction cancels the task.
 *
 * <p>The key is a domain counter (never {@code version()}, which every command bumps): see {@link
 * KiloDeMerdeStepKey}. A task that still fires late finds the key moved on and the use case does nothing. Time stays
 * out of the domain.
 */
abstract class KiloDeMerdeTimer<K> implements GameStateListener<KiloDeMerde>, GameEvictionListener {

    private static final Logger log = LoggerFactory.getLogger(KiloDeMerdeTimer.class);

    /** How many times a firing is tried before the timer gives up and logs it (a failed command changes nothing). */
    public static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofMillis(500);

    private final BotScheduler scheduler;
    private final Map<GameId, Armed<K>> armed = new ConcurrentHashMap<>();

    KiloDeMerdeTimer(BotScheduler scheduler) {
        this.scheduler = scheduler;
    }

    private record Armed<K>(KiloDeMerde game, K key, BotScheduler.BotTask task) {
    }

    /** The key of the wait the game is in, read under the monitor of the game; empty when this timer has no wait. */
    protected abstract Optional<K> keyOf(KiloDeMerde game);

    /** How long to wait for {@code key}, read under the monitor of the game. */
    protected abstract Duration delayOf(KiloDeMerde game, K key);

    /** The use case to run when the wait ends; it drops itself when the key no longer matches. */
    protected abstract void fire(GameId id, KiloDeMerde game, K key) throws Exception;

    @Override
    public void onBroadcast(KiloDeMerde game, String eventType, EventData eventData) {
        synchronized (game) {
            GameId id = game.getId();
            Optional<K> key = keyOf(game);
            if (key.isEmpty()) {
                cancel(id);
                return;
            }
            Armed<K> current = armed.get(id);
            if (current != null && current.game() == game && current.key().equals(key.get())) {
                return; // already waiting for this very wait
            }
            cancel(id);
            K wanted = key.get();
            armed.put(id, new Armed<>(game, wanted,
                    scheduler.schedule(delayOf(game, wanted), () -> run(id, game, wanted, 1))));
        }
    }

    @Override
    public void onEvicted(GameId id) {
        cancel(id);
    }

    private void run(GameId id, KiloDeMerde game, K key, int attempt) {
        try {
            fire(id, game, key);
        } catch (Exception e) {
            if (attempt >= MAX_ATTEMPTS) {
                log.error("The {} of game {} could not run after {} attempts; the game stays as it is",
                        getClass().getSimpleName(), id, attempt, e);
                return;
            }
            log.warn("The {} of game {} failed (attempt {} of {}); retrying", getClass().getSimpleName(), id, attempt,
                    MAX_ATTEMPTS, e);
            retry(id, game, key, attempt + 1);
        }
    }

    /** Re-arms the same wait, unless the state moved on (then the new state armed its own task). */
    private void retry(GameId id, KiloDeMerde game, K key, int attempt) {
        synchronized (game) {
            Armed<K> current = armed.get(id);
            if (current == null || current.game() != game || !current.key().equals(key)) {
                return;
            }
            armed.put(id, new Armed<>(game, key, scheduler.schedule(RETRY_DELAY, () -> run(id, game, key, attempt))));
        }
    }

    private void cancel(GameId id) {
        Armed<K> removed = armed.remove(id);
        if (removed != null) {
            removed.task().cancel();
        }
    }
}
