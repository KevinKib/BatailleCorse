package org.kevinkib.cardgames.ninetynine.presentation.bot;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNine;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNineFactory;
import org.kevinkib.cardgames.ninetynine.domain.Phase;
import org.kevinkib.cardgames.ninetynine.domain.bot.NinetyNineBotLevel;
import org.kevinkib.cardgames.ninetynine.domain.bot.NinetyNineBotStrategy;
import org.kevinkib.cardgames.ninetynine.domain.bot.NinetyNineBotView;
import org.kevinkib.cardgames.ninetynine.domain.bot.NinetyNineDecision;
import org.kevinkib.cardgames.ninetynine.presentation.NinetyNineGameActions;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.bot.BotSeats;
import org.kevinkib.cardgames.presentation.bot.ThinkingDelay;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.presentation.state.GameStateListener;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Decides when a computer seat must act and applies its decision through the same use case as a human.
 *
 * <p>After every state broadcast that changed the game, it cancels the task of the previous state and, when the seat
 * on turn is a computer seat of a running round, schedules one task for it after a thinking delay. A task remembers
 * the game version it reasoned about.
 *
 * <p><b>The decision is taken outside the monitor of the game</b>, which a human action, a forfeit and the round
 * pause take: a task takes the seat's view under the monitor and drops itself if the version moved on, decides without
 * any lock (the sampling level can take its time), then takes the monitor again, checks the version a second time and
 * only then plays. A stale answer is thrown away. The level is read from the seat option {@code level}.
 *
 * <p>A strategy that fails or proposes a card the game refuses falls back to the first safe card (an Ace counts 1),
 * so the table never stalls. Nothing is scheduled outside a running round (the round pause deals the next one), once
 * the game is over, or after the game was evicted; a new game under the same id (Play again) gets a fresh table.
 */
public class NinetyNineBotCoordinator implements GameStateListener<NinetyNine>, GameEvictionListener {

    private static final Logger log = LoggerFactory.getLogger(NinetyNineBotCoordinator.class);

    private final BotSeats botSeats;
    private final BotSeatOptions seatOptions;
    private final NinetyNineGameActions actions;
    private final BotScheduler scheduler;
    private final ThinkingDelay thinkingDelay;
    private final Function<NinetyNineBotLevel, NinetyNineBotStrategy> strategies;
    private final Map<GameId, Table> tables = new ConcurrentHashMap<>();

    public NinetyNineBotCoordinator(BotSeats botSeats,
                                    BotSeatOptions seatOptions,
                                    NinetyNineGameActions actions,
                                    BotScheduler scheduler,
                                    ThinkingDelay thinkingDelay,
                                    Function<NinetyNineBotLevel, NinetyNineBotStrategy> strategies) {
        this.botSeats = botSeats;
        this.seatOptions = seatOptions;
        this.actions = actions;
        this.scheduler = scheduler;
        this.thinkingDelay = thinkingDelay;
        this.strategies = strategies;
    }

    /** What the coordinator keeps for one game with bots. */
    private static final class Table {
        final NinetyNine game;
        BotScheduler.BotTask pending;
        int scheduledVersion = -1;

        Table(NinetyNine game) {
            this.game = game;
        }

        synchronized void cancelPending() {
            if (pending != null) {
                pending.cancel();
                pending = null;
            }
        }

        synchronized void track(BotScheduler.BotTask task) {
            pending = task;
        }
    }

    // ---- state changes -----------------------------------------------------------------------

    @Override
    public void onBroadcast(NinetyNine game, String eventType, EventData eventData) {
        synchronized (game) {
            GameId id = game.getId();
            Set<PlayerId> bots = botsIn(game);
            if (bots.isEmpty() || game.isFinished()) {
                drop(id);
                return;
            }
            Table table = tables.get(id);
            if (table != null && table.game != game) {
                drop(id); // the room was reopened for a rematch: a new game under the same id
                table = null;
            }
            if (table == null) {
                table = new Table(game);
                tables.put(id, table);
            }
            if (table.scheduledVersion == game.version()) {
                return; // not a state change (a connection notice, a second event of the same command)
            }
            table.cancelPending();
            table.scheduledVersion = game.version();
            PlayerId current = game.currentSeat();
            if (game.phase() == Phase.PLAYING && bots.contains(current)) {
                int version = game.version();
                table.track(scheduler.schedule(thinkingDelay.next(), () -> think(game, current, version)));
            }
        }
    }

    @Override
    public void onEvicted(GameId id) {
        drop(id);
    }

    private Set<PlayerId> botsIn(NinetyNine game) {
        try {
            Set<PlayerId> seats = botSeats.botSeats(game.getId());
            return game.getPlayerIds().stream().filter(seats::contains).collect(java.util.stream.Collectors.toSet());
        } catch (RuntimeException e) {
            return Set.of();
        }
    }

    private void drop(GameId id) {
        Table table = tables.remove(id);
        if (table != null) {
            table.cancelPending();
        }
    }

    // ---- a bot acts --------------------------------------------------------------------------

    private void think(NinetyNine game, PlayerId seat, int version) {
        NinetyNineBotView view;
        NinetyNineBotLevel level;
        synchronized (game) {
            if (!unchanged(game, version)) {
                return; // the state moved on since this task was scheduled
            }
            try {
                view = NinetyNineBotView.forSeat(game, seat);
                level = NinetyNineBotLevel.fromOption(
                        seatOptions.seatOptions(game.getId(), seat).get(NinetyNineFactory.LEVEL_KEY));
            } catch (RuntimeException e) {
                log.warn("Bot {} could not be set up in game {}; falling back to its first safe card", seat,
                        game.getId(), e);
                fallback(game, seat);
                return;
            }
        }

        NinetyNineDecision decision = null;
        try {
            decision = strategies.apply(level).decide(view);
        } catch (Exception e) {
            log.warn("Bot {} failed in game {}; falling back to its first safe card", seat, game.getId(), e);
        }

        synchronized (game) {
            if (!unchanged(game, version)) {
                return; // a forfeit or the pause moved the game while it was thinking: this answer is stale
            }
            try {
                if (!(decision instanceof NinetyNineDecision.Play play)) {
                    throw new IllegalStateException("no decision");
                }
                actions.play(game.getId(), seat, play.card(), play.aceValue());
            } catch (Exception e) {
                log.warn("Bot {} proposed an illegal play in game {}; falling back to its first safe card", seat,
                        game.getId(), e);
                fallback(game, seat);
            }
        }
    }

    /** Whether the table still follows this game and the state is the one the task reasoned about. */
    private boolean unchanged(NinetyNine game, int version) {
        Table table = tables.get(game.getId());
        return table != null && table.game == game && !game.isFinished() && game.version() == version;
    }

    private void fallback(NinetyNine game, PlayerId seat) {
        try {
            List<Card> safe = game.safeCards(seat);
            if (safe.isEmpty()) {
                return;
            }
            Card card = safe.get(0);
            actions.play(game.getId(), seat, card, card.getRank() == FrenchRank.ACE ? 1 : null);
        } catch (Exception e) {
            log.error("Bot {} fallback failed in game {}", seat, game.getId(), e);
        }
    }
}
