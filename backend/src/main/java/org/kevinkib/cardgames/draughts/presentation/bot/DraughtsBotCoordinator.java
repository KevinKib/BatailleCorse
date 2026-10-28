package org.kevinkib.cardgames.draughts.presentation.bot;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.kevinkib.cardgames.draughts.domain.Colour;
import org.kevinkib.cardgames.draughts.domain.Draughts;
import org.kevinkib.cardgames.draughts.domain.DraughtsFactory;
import org.kevinkib.cardgames.draughts.domain.DraughtsMove;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;
import org.kevinkib.cardgames.draughts.domain.Phase;
import org.kevinkib.cardgames.draughts.domain.bot.DraughtsBot;
import org.kevinkib.cardgames.draughts.domain.bot.DraughtsBotLevel;
import org.kevinkib.cardgames.draughts.domain.bot.DraughtsDecision;
import org.kevinkib.cardgames.draughts.presentation.DraughtsGameActions;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.bot.BotSeats;
import org.kevinkib.cardgames.presentation.bot.ThinkingDelay;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.presentation.state.GameStateListener;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides when a draughts computer seat must act and applies its decision through the same use cases as a human.
 *
 * <p>After every state broadcast that changed the game, it cancels the task of the previous state and, when a
 * computer seat has something to do, schedules one task for it after a thinking delay: answering a draw offer of the
 * opponent first, otherwise playing a move when it is the seat's turn. A task remembers the game version it reasoned
 * about.
 *
 * <p><b>The decision is taken outside the monitor of the game</b>, which a human action and a forfeit take: a task
 * takes a snapshot (the position, the colour, whether an offer is pending, the level) under the monitor and drops
 * itself if the version moved on, decides without any lock (the expert can spend its whole budget), then takes the
 * monitor again, checks the version a second time and only then acts. A stale answer is thrown away. The level is
 * read from the seat option {@code level}. Bots never offer a draw.
 *
 * <p>A strategy that fails or proposes something the game refuses falls back to a fixed safe choice: declining a
 * pending offer, otherwise the first legal move, so the table never stalls. Nothing is scheduled once the game is
 * over, when no human is left, or after the game was evicted; a new game under the same id (Play again) gets a
 * fresh table.
 */
public class DraughtsBotCoordinator implements GameStateListener<Draughts>, GameEvictionListener {

    private static final Logger log = LoggerFactory.getLogger(DraughtsBotCoordinator.class);

    private final BotSeats botSeats;
    private final BotSeatOptions seatOptions;
    private final DraughtsGameActions actions;
    private final BotScheduler scheduler;
    private final ThinkingDelay thinkingDelay;
    private final Function<DraughtsBotLevel, DraughtsBot> bots;
    private final Map<GameId, Table> tables = new ConcurrentHashMap<>();

    public DraughtsBotCoordinator(BotSeats botSeats,
                                  BotSeatOptions seatOptions,
                                  DraughtsGameActions actions,
                                  BotScheduler scheduler,
                                  ThinkingDelay thinkingDelay,
                                  Function<DraughtsBotLevel, DraughtsBot> bots) {
        this.botSeats = botSeats;
        this.seatOptions = seatOptions;
        this.actions = actions;
        this.scheduler = scheduler;
        this.thinkingDelay = thinkingDelay;
        this.bots = bots;
    }

    /** What the coordinator keeps for one game with bots. */
    private static final class Table {
        final Draughts game;
        BotScheduler.BotTask pending;
        int scheduledVersion = -1;

        Table(Draughts game) {
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
    public void onBroadcast(Draughts game, String eventType, EventData eventData) {
        synchronized (game) {
            GameId id = game.getId();
            Set<PlayerId> botsInGame = botsIn(game);
            if (botsInGame.isEmpty() || game.isFinished() || botsInGame.size() == game.getPlayerIds().size()) {
                drop(id); // no bots, game over, or no human left: nothing to drive
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
            if (game.phase() != Phase.PLAY) {
                return;
            }
            int version = game.version();
            for (PlayerId seat : botsInGame) {
                if (mustAct(game, seat)) {
                    table.track(scheduler.schedule(thinkingDelay.next(), () -> act(game, seat, version)));
                    return; // one task: a pending offer is answered before the move, and one seat is on turn
                }
            }
        }
    }

    @Override
    public void onEvicted(GameId id) {
        drop(id);
    }

    private Set<PlayerId> botsIn(Draughts game) {
        Set<PlayerId> inGame = new TreeSet<>(Comparator.comparingInt(PlayerId::id));
        Set<PlayerId> seats;
        try {
            seats = botSeats.botSeats(game.getId());
        } catch (RuntimeException e) {
            return Set.of();
        }
        for (PlayerId seat : game.getPlayerIds()) {
            if (seats.contains(seat)) {
                inGame.add(seat);
            }
        }
        return inGame;
    }

    private void drop(GameId id) {
        Table table = tables.remove(id);
        if (table != null) {
            table.cancelPending();
        }
    }

    /** An offer of the opponent waits for an answer, or it is the seat's turn with no offer in the way. */
    private static boolean mustAct(Draughts game, PlayerId seat) {
        return offerToAnswer(game, seat) || game.drawOffer() == null && game.currentSeat().equals(seat);
    }

    private static boolean offerToAnswer(Draughts game, PlayerId seat) {
        PlayerId offerer = game.drawOffer();
        return offerer != null && !offerer.equals(seat);
    }

    /** Whether the table still follows this game and the state is the one the task reasoned about. */
    private boolean unchanged(Draughts game, int version) {
        Table table = tables.get(game.getId());
        return table != null && table.game == game && !game.isFinished() && game.version() == version;
    }

    // ---- a bot acts --------------------------------------------------------------------------

    private record Snapshot(DraughtsPosition position, Colour colour, boolean offerPending, DraughtsBotLevel level) {
    }

    private void act(Draughts game, PlayerId seat, int version) {
        Snapshot snapshot;
        synchronized (game) {
            if (!unchanged(game, version) || !mustAct(game, seat)) {
                return; // the state moved on since this task was scheduled
            }
            try {
                snapshot = new Snapshot(game.position(), game.colourOf(seat), offerToAnswer(game, seat),
                        DraughtsBotLevel.fromOption(
                                seatOptions.seatOptions(game.getId(), seat).get(DraughtsFactory.LEVEL_KEY)));
            } catch (RuntimeException e) {
                log.warn("Bot {} could not be set up in game {}; falling back", seat, game.getId(), e);
                fallback(game, seat);
                return;
            }
        }

        DraughtsDecision decision = null;
        try {
            decision = bots.apply(snapshot.level()).decide(snapshot.position(), snapshot.colour(),
                    snapshot.offerPending());
        } catch (Exception e) {
            log.warn("Bot {} failed in game {}; falling back", seat, game.getId(), e);
        }

        synchronized (game) {
            if (!unchanged(game, version)) {
                return; // a human acted or the game ended while it was thinking: this answer is stale
            }
            try {
                apply(game, seat, snapshot.offerPending(), decision);
            } catch (Exception e) {
                log.warn("Bot {} proposed {} in game {} which the game refused; falling back", seat, decision,
                        game.getId(), e);
                fallback(game, seat);
            }
        }
    }

    private void apply(Draughts game, PlayerId seat, boolean offerPending, DraughtsDecision decision)
            throws Exception {
        GameId id = game.getId();
        if (decision instanceof DraughtsDecision.Play play && !offerPending) {
            actions.move(id, seat, play.move().from(), play.move().to(), play.move().captures());
        } else if (decision instanceof DraughtsDecision.AcceptDraw && offerPending) {
            actions.acceptDraw(id, seat);
        } else if (decision instanceof DraughtsDecision.DeclineDraw && offerPending) {
            actions.declineDraw(id, seat);
        } else {
            throw new IllegalStateException("no usable decision");
        }
    }

    /** Declines a pending offer, otherwise plays the first legal move. */
    private void fallback(Draughts game, PlayerId seat) {
        try {
            if (offerToAnswer(game, seat)) {
                actions.declineDraw(game.getId(), seat);
                return;
            }
            List<DraughtsMove> legal = game.legalMoves(seat);
            if (!legal.isEmpty()) {
                DraughtsMove move = legal.get(0);
                actions.move(game.getId(), seat, move.from(), move.to(), move.captures());
            }
        } catch (Exception e) {
            log.error("Bot {} fallback failed in game {}", seat, game.getId(), e);
        }
    }
}
