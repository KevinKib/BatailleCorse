package org.kevinkib.cardgames.president.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.presentation.state.GameStateListener;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stops the table waiting for the exchange view of a President round: when a state broadcast shows the game in
 * {@link Phase#EXCHANGE_VIEW}, it arms one task for the round; when the task fires it closes the view, so the
 * seats that have not acknowledged count as having seen the exchange and the first trick starts. Any other
 * phase, another round, a new game under the same id (Play again) or an eviction cancels the task; a task that
 * still fires late finds a stale round and the domain does nothing.
 *
 * <p>Every state change of a President game goes through the state broadcaster (commands, bot moves, forfeits),
 * so this listener sees every way into and out of the view. Time stays out of the domain.
 */
public class PresidentExchangeViewTimeout implements GameStateListener<President>, GameEvictionListener {

    private final PresidentGameActions actions;
    private final BotScheduler scheduler;
    private final Duration timeout;
    private final Map<GameId, Armed> armed = new ConcurrentHashMap<>();

    public PresidentExchangeViewTimeout(PresidentGameActions actions, BotScheduler scheduler, Duration timeout) {
        this.actions = actions;
        this.scheduler = scheduler;
        this.timeout = timeout;
    }

    /** The task armed for the view of {@code round} of this game object. */
    private record Armed(President game, int round, BotScheduler.BotTask task) {
    }

    @Override
    public void onBroadcast(President game, String eventType, EventData eventData) {
        synchronized (game) {
            GameId id = game.getId();
            Armed current = armed.get(id);
            if (game.isFinished() || game.phase() != Phase.EXCHANGE_VIEW) {
                cancel(id);
                return;
            }
            int round = game.round();
            if (current != null && current.game() == game && current.round() == round) {
                return; // already waiting for this view
            }
            cancel(id);
            BotScheduler.BotTask task = scheduler.schedule(timeout, () -> actions.closeExchangeView(id, round));
            armed.put(id, new Armed(game, round, task));
        }
    }

    @Override
    public void onEvicted(GameId id) {
        cancel(id);
    }

    private void cancel(GameId id) {
        Armed removed = armed.remove(id);
        if (removed != null) {
            removed.task().cancel();
        }
    }
}
