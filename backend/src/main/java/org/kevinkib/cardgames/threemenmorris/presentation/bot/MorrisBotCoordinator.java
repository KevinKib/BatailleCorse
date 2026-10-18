package org.kevinkib.cardgames.threemenmorris.presentation.bot;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.bot.BotSeats;
import org.kevinkib.cardgames.presentation.bot.ThinkingDelay;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.presentation.state.GameStateListener;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.kevinkib.cardgames.threemenmorris.domain.Move;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.domain.bot.MorrisBot;
import org.kevinkib.cardgames.threemenmorris.domain.bot.MorrisBotLevel;
import org.kevinkib.cardgames.threemenmorris.domain.bot.MorrisDecision;
import org.kevinkib.cardgames.threemenmorris.presentation.ThreeMenMorrisGameActions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Decides when a computer seat must act and applies its decision through the same use cases as a human.
 *
 * <p>After every state broadcast it cancels the tasks of the previous state and schedules one task per bot
 * seat that has something to do, after a thinking delay: answering a draw offer when the opponent made one
 * (even off turn), else playing (a placement, a move, or the removal owed after a mill) when it is the
 * seat's turn. A task remembers the game version it reasoned about.
 *
 * <p><b>The search runs outside the monitor of the game</b>, which a human action, a forfeit and the other
 * tasks take: a task takes a snapshot (position, level) under the monitor and drops itself if the version
 * moved on, thinks without any lock, then takes the monitor again, checks the version a second time and only
 * then applies the decision. A human who acts while the bot thinks therefore never waits for a search, and
 * the bot's stale answer is thrown away. The level is read from the seat option {@code level}.
 *
 * <p>A strategy that fails or proposes an illegal decision falls back to the first legal removal, placement
 * or move, so the table never stalls (an unanswered draw offer is declined). Nothing is scheduled once the
 * game is over, no bot is left in it, or it was evicted; a new game under the same id (Play again) gets a
 * fresh table. Bots never offer a draw: the repetition rule ends endless games.
 */
public class MorrisBotCoordinator implements GameStateListener<ThreeMenMorris>, GameEvictionListener {

    /** The seat option that carries the level of a computer seat. */
    public static final String LEVEL_KEY = "level";

    private static final Logger log = LoggerFactory.getLogger(MorrisBotCoordinator.class);

    private final BotSeats botSeats;
    private final BotSeatOptions seatOptions;
    private final ThreeMenMorrisGameActions actions;
    private final BotScheduler scheduler;
    private final ThinkingDelay thinkingDelay;
    private final Function<MorrisBotLevel, MorrisBot> bots;
    private final Map<GameId, Table> tables = new ConcurrentHashMap<>();

    public MorrisBotCoordinator(BotSeats botSeats,
                                BotSeatOptions seatOptions,
                                ThreeMenMorrisGameActions actions,
                                BotScheduler scheduler,
                                ThinkingDelay thinkingDelay,
                                Function<MorrisBotLevel, MorrisBot> bots) {
        this.botSeats = botSeats;
        this.seatOptions = seatOptions;
        this.actions = actions;
        this.scheduler = scheduler;
        this.thinkingDelay = thinkingDelay;
        this.bots = bots;
    }

    /** What the coordinator keeps for one game with bots. */
    private static final class Table {
        final ThreeMenMorris game;
        final List<BotScheduler.BotTask> pending = new ArrayList<>();
        int scheduledVersion = -1;

        Table(ThreeMenMorris game) {
            this.game = game;
        }

        synchronized void cancelPending() {
            pending.forEach(BotScheduler.BotTask::cancel);
            pending.clear();
        }

        synchronized void track(BotScheduler.BotTask task) {
            pending.add(task);
        }
    }

    // ---- state changes -----------------------------------------------------------------------

    @Override
    public void onBroadcast(ThreeMenMorris game, String eventType, EventData eventData) {
        synchronized (game) {
            GameId id = game.getId();
            Set<PlayerId> bots = botsIn(game);
            if (bots.isEmpty() || game.isFinished() || bots.size() == game.getPlayerIds().size()) {
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
                return; // not a state change (a connection notice, for instance): bots keep thinking
            }
            table.cancelPending();
            table.scheduledVersion = game.version();
            int version = game.version();
            for (PlayerId seat : bots) {
                if (mustAnswerOffer(game, seat) || game.currentSeat().equals(seat)) {
                    table.track(scheduler.schedule(thinkingDelay.next(), () -> think(game, seat, version)));
                }
            }
        }
    }

    @Override
    public void onEvicted(GameId id) {
        drop(id);
    }

    private Set<PlayerId> botsIn(ThreeMenMorris game) {
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

    private static boolean mustAnswerOffer(ThreeMenMorris game, PlayerId seat) {
        PlayerId offerer = game.drawOffer();
        return offerer != null && !offerer.equals(seat);
    }

    // ---- a bot acts --------------------------------------------------------------------------

    /** What a task needs to reason, copied under the monitor so the search can run without it. */
    private record Request(MorrisPosition position, MorrisBotLevel level, boolean offerPending) {
    }

    private void think(ThreeMenMorris game, PlayerId seat, int version) {
        Request request;
        synchronized (game) {
            if (!unchanged(game, version)) {
                return; // the state moved on since this bot started thinking
            }
            try {
                request = new Request(game.position(),
                        MorrisBotLevel.fromOption(seatOptions.seatOptions(game.getId(), seat).get(LEVEL_KEY)),
                        mustAnswerOffer(game, seat));
            } catch (RuntimeException e) {
                log.warn("Bot {} could not be set up in game {}; falling back to its first legal action", seat,
                        game.getId(), e);
                fallback(game, seat, mustAnswerOffer(game, seat));
                return;
            }
        }

        MorrisDecision decision = null;
        try {
            decision = bots.apply(request.level()).decide(request.position(), seat, request.offerPending());
        } catch (Exception e) {
            log.warn("Bot {} failed in game {}; falling back to its first legal action", seat, game.getId(), e);
        }

        synchronized (game) {
            if (!unchanged(game, version)) {
                return; // a human acted or the game ended while it was thinking: this answer is stale
            }
            try {
                if (decision == null) {
                    throw new IllegalStateException("no decision");
                }
                apply(game, seat, decision);
            } catch (Exception e) {
                log.warn("Bot {} proposed an illegal decision in game {}; falling back to its first legal action",
                        seat, game.getId(), e);
                fallback(game, seat, request.offerPending());
            }
        }
    }

    /** Whether the table still follows this game and the state is the one the task reasoned about. */
    private boolean unchanged(ThreeMenMorris game, int version) {
        Table table = tables.get(game.getId());
        return table != null && table.game == game && !game.isFinished() && game.version() == version;
    }

    private void apply(ThreeMenMorris game, PlayerId seat, MorrisDecision decision) throws Exception {
        GameId id = game.getId();
        if (decision instanceof MorrisDecision.Place place) {
            actions.place(id, seat, place.point());
        } else if (decision instanceof MorrisDecision.Step step) {
            actions.move(id, seat, step.from(), step.to());
        } else if (decision instanceof MorrisDecision.Remove remove) {
            actions.remove(id, seat, remove.point());
        } else if (decision instanceof MorrisDecision.AcceptDraw) {
            actions.acceptDraw(id, seat);
        } else if (decision instanceof MorrisDecision.DeclineDraw) {
            actions.declineDraw(id, seat);
        }
    }

    private void fallback(ThreeMenMorris game, PlayerId seat, boolean offerPending) {
        try {
            if (offerPending) {
                actions.declineDraw(game.getId(), seat);
                return;
            }
            List<Integer> removals = game.legalRemovals(seat);
            if (!removals.isEmpty()) {
                actions.remove(game.getId(), seat, removals.get(0));
                return;
            }
            List<Integer> placements = game.legalPlacements(seat);
            if (!placements.isEmpty()) {
                actions.place(game.getId(), seat, placements.get(0));
                return;
            }
            List<Move> moves = game.legalMoves(seat);
            if (!moves.isEmpty()) {
                actions.move(game.getId(), seat, moves.get(0).from(), moves.get(0).to());
            }
        } catch (Exception e) {
            log.error("Bot {} fallback failed in game {}", seat, game.getId(), e);
        }
    }

    // ---- inspection for tests ----------------------------------------------------------------

    int trackedGameCount() {
        return tables.size();
    }
}
