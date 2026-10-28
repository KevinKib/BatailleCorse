package org.kevinkib.cardgames.ninetynine.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNine;
import org.kevinkib.cardgames.ninetynine.domain.Phase;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.presentation.state.GameStateListener;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The pause between two rounds, so that a table of 12 never waits for a click: when a state broadcast shows the game
 * in {@link Phase#ROUND_OVER}, it arms one task for that state; when the task fires it deals the next round. Any
 * other phase, a state that moved on (a forfeit changes the version, so the task is re-armed on the new one), a new
 * game under the same id (Play again) or an eviction cancels the task; a task that still fires late finds a stale
 * version and the use case does nothing. Time stays out of the domain.
 *
 * <p>Every state change of a 99 game goes through the state broadcaster (commands, bot plays, forfeits), so this
 * listener sees every way into and out of the round end.
 */
public class NinetyNineRoundPause implements GameStateListener<NinetyNine>, GameEvictionListener {

    private static final Logger log = LoggerFactory.getLogger(NinetyNineRoundPause.class);

    /** How many times a deal is tried before the pause gives up and logs it (a failed deal changes nothing). */
    public static final int MAX_ATTEMPTS = 3;

    private final NinetyNineGameActions actions;
    private final BotScheduler scheduler;
    private final Duration pause;
    private final Map<GameId, Armed> armed = new ConcurrentHashMap<>();

    public NinetyNineRoundPause(NinetyNineGameActions actions, BotScheduler scheduler, Duration pause) {
        this.actions = actions;
        this.scheduler = scheduler;
        this.pause = pause;
    }

    /** The task armed for the round end of this game object at this version. */
    private record Armed(NinetyNine game, int version, BotScheduler.BotTask task) {
    }

    @Override
    public void onBroadcast(NinetyNine game, String eventType, EventData eventData) {
        synchronized (game) {
            GameId id = game.getId();
            if (game.isFinished() || game.phase() != Phase.ROUND_OVER) {
                cancel(id);
                return;
            }
            int version = game.version();
            Armed current = armed.get(id);
            if (current != null && current.game() == game && current.version() == version) {
                return; // already waiting for this round end
            }
            cancel(id);
            armed.put(id, new Armed(game, version, scheduler.schedule(pause, () -> fire(id, version, 1))));
        }
    }

    @Override
    public void onEvicted(GameId id) {
        cancel(id);
    }

    private void fire(GameId id, int version, int attempt) {
        try {
            actions.nextRound(id, version);
        } catch (Exception e) {
            if (attempt >= MAX_ATTEMPTS) {
                log.error("The next round of game {} could not be dealt after {} attempts; the game stays at its "
                        + "round end", id, attempt, e);
                return;
            }
            log.warn("The next round of game {} could not be dealt (attempt {} of {}); retrying", id, attempt,
                    MAX_ATTEMPTS, e);
            retry(id, version, attempt + 1);
        }
    }

    /** Re-arms the task of the same round end, unless the state moved on (then the new state armed its own). */
    private void retry(GameId id, int version, int attempt) {
        Armed current = armed.get(id);
        if (current == null || current.version() != version) {
            return;
        }
        synchronized (current.game()) {
            if (armed.get(id) != current) {
                return;
            }
            armed.put(id, new Armed(current.game(), version,
                    scheduler.schedule(pause, () -> fire(id, version, attempt))));
        }
    }

    private void cancel(GameId id) {
        Armed removed = armed.remove(id);
        if (removed != null) {
            removed.task().cancel();
        }
    }
}
