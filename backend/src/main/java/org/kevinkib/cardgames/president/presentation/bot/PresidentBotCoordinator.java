package org.kevinkib.cardgames.president.presentation.bot;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.PresidentFactory;
import org.kevinkib.cardgames.president.domain.bot.PresidentBotDecision;
import org.kevinkib.cardgames.president.domain.bot.PresidentBotLevel;
import org.kevinkib.cardgames.president.domain.bot.PresidentBotMemory;
import org.kevinkib.cardgames.president.domain.bot.PresidentBotStrategy;
import org.kevinkib.cardgames.president.domain.bot.PresidentBotView;
import org.kevinkib.cardgames.president.domain.card.HandSort;
import org.kevinkib.cardgames.president.domain.card.RankOrder;
import org.kevinkib.cardgames.president.domain.exchange.PendingReturn;
import org.kevinkib.cardgames.president.domain.round.Action;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.president.domain.rules.PresidentException;
import org.kevinkib.cardgames.president.presentation.PresidentGameActions;
import org.kevinkib.cardgames.president.presentation.dto.event.PresidentEventData;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.bot.BotSeats;
import org.kevinkib.cardgames.presentation.bot.ThinkingDelay;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.presentation.state.GameStateListener;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.kevinkib.cards.domain.Card;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Decides when a President computer seat must act and applies its decision through the same use cases as a
 * human: a play or a pass on its turn, the completion of the four it holds (in turn or out of turn, with its own
 * task when it is not the seat on turn; a completion the game refuses as too late is dropped), the cards it owes
 * back in the exchange, its acknowledgement of the exchange view (with no thinking delay, so a bot never keeps a
 * human waiting), and "ready" at the barrier between two rounds.
 *
 * <p>After every state broadcast, under the game's monitor, it feeds the memory of each bot (the cards of the
 * broadcast first, since the last play of a closed trick is no longer on the table, then the seat's own view)
 * and schedules one task per bot seat that has a step to take, after a thinking delay. A task remembers the
 * step it was scheduled for: during play the game version (any move makes the plan stale), during the exchange
 * and at the barrier the phase and the round only, so one bot's give back or "ready" does not restart the
 * thinking of the others. The next broadcast cancels the tasks whose step is gone; a task that fires on a state
 * it was not scheduled for, because the state moved without a broadcast, does nothing.
 *
 * <p>A completion window that opens for a human who is not on turn would close at the next bot's play, often
 * before the human has even seen the card land: so, once per window, the bot on turn waits at least the
 * completion grace before it acts.
 *
 * <p>The decision is taken and applied in one go under the game's monitor (the heuristic is cheap), so it can
 * never be stale. A strategy that fails or proposes something the game refuses falls back to a fixed legal
 * action (the lowest legal set when leading, a pass when following, the lowest cards in the exchange, ready),
 * so the table never stalls. Nothing is scheduled once the match is over, no human is left, or it was evicted;
 * a new game under the same id (Play again) gets a fresh table and fresh memories. The level is read from the
 * seat option {@code level}.
 */
public class PresidentBotCoordinator implements GameStateListener<President>, GameEvictionListener {

    /** The seat option that carries the level of a computer seat. */
    public static final String LEVEL_KEY = PresidentFactory.LEVEL_KEY;

    private static final Logger log = LoggerFactory.getLogger(PresidentBotCoordinator.class);
    private static final RankOrder ORDER = RankOrder.standard();

    private final BotSeats botSeats;
    private final BotSeatOptions seatOptions;
    private final PresidentGameActions actions;
    private final BotScheduler scheduler;
    private final ThinkingDelay thinkingDelay;
    private final Function<PresidentBotLevel, PresidentBotStrategy> strategies;
    /** How long at least the bot on turn waits, once per window, while a human holds the completion out of turn. */
    private final Duration completionGrace;
    private final Map<GameId, Table> tables = new ConcurrentHashMap<>();

    public PresidentBotCoordinator(BotSeats botSeats,
                                   BotSeatOptions seatOptions,
                                   PresidentGameActions actions,
                                   BotScheduler scheduler,
                                   ThinkingDelay thinkingDelay,
                                   Function<PresidentBotLevel, PresidentBotStrategy> strategies) {
        this(botSeats, seatOptions, actions, scheduler, thinkingDelay, strategies, Duration.ZERO);
    }

    public PresidentBotCoordinator(BotSeats botSeats,
                                   BotSeatOptions seatOptions,
                                   PresidentGameActions actions,
                                   BotScheduler scheduler,
                                   ThinkingDelay thinkingDelay,
                                   Function<PresidentBotLevel, PresidentBotStrategy> strategies,
                                   Duration completionGrace) {
        this.botSeats = botSeats;
        this.seatOptions = seatOptions;
        this.actions = actions;
        this.scheduler = scheduler;
        this.thinkingDelay = thinkingDelay;
        this.strategies = strategies;
        this.completionGrace = completionGrace == null ? Duration.ZERO : completionGrace;
    }

    /** What a task was scheduled for; {@code version} is -1 outside play. */
    private record Step(Phase phase, int round, int version) {

        static Step of(PresidentBotView view) {
            return new Step(view.phase(), view.round(), view.phase() == Phase.PLAYING ? view.version() : -1);
        }
    }

    /** One bot seat of a table: its strategy and what it remembers of the round. */
    private static final class BotSeat {
        final PlayerId seat;
        final PresidentBotStrategy strategy;
        final PresidentBotMemory memory;

        BotSeat(PlayerId seat, PresidentBotStrategy strategy) {
            this.seat = seat;
            this.strategy = strategy;
            this.memory = new PresidentBotMemory(seat);
        }
    }

    /** The task of one bot seat and the step it was scheduled for. */
    private record Pending(Step step, BotScheduler.BotTask task) {
    }

    /** What the coordinator keeps for one game with bots. */
    private static final class Table {
        final President game;
        final Map<PlayerId, BotSeat> bots = new LinkedHashMap<>();
        final Map<PlayerId, Pending> pending = new ConcurrentHashMap<>();
        int scheduledVersion = -1;
        /** The completion window a human was last given the grace for (round and chain cards); null if none. */
        Object gracedWindow;

        Table(President game) {
            this.game = game;
        }

        void cancel(PlayerId seat) {
            Pending removed = pending.remove(seat);
            if (removed != null) {
                removed.task().cancel();
            }
        }

        void cancelAll() {
            List.copyOf(pending.keySet()).forEach(this::cancel);
        }
    }

    // ---- state changes -----------------------------------------------------------------------

    @Override
    public void onBroadcast(President game, String eventType, EventData eventData) {
        synchronized (game) {
            GameId id = game.getId();
            Set<PlayerId> bots = botsIn(game);
            if (bots.isEmpty() || game.isFinished() || bots.size() == game.getPlayerIds().size()) {
                drop(id); // no bots, match over, or no human left: nothing to drive
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
                return; // not a state change (a connection notice, for instance): the bots keep thinking
            }
            table.scheduledVersion = game.version();
            syncSeats(table, bots);
            List<Card> announced = cardsOf(game, eventData);
            for (BotSeat bot : table.bots.values()) {
                bot.memory.learnPlayed(announced);
                bot.memory.observe(PresidentBotView.forSeat(game, bot.seat));
            }
            schedule(table);
        }
    }

    @Override
    public void onEvicted(GameId id) {
        drop(id);
    }

    private Set<PlayerId> botsIn(President game) {
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
            table.cancelAll();
        }
    }

    private void syncSeats(Table table, Set<PlayerId> bots) {
        table.bots.keySet().removeIf(seat -> {
            if (bots.contains(seat)) {
                return false;
            }
            table.cancel(seat);
            return true;
        });
        for (PlayerId seat : bots) {
            table.bots.computeIfAbsent(seat, s -> new BotSeat(s, strategies.apply(levelOf(table.game, s))));
        }
    }

    private PresidentBotLevel levelOf(President game, PlayerId seat) {
        return PresidentBotLevel.fromOption(seatOptions.seatOptions(game.getId(), seat).get(LEVEL_KEY));
    }

    /** The cards a play event names, found among the cards already played (the last play of a trick is in the discard). */
    private static List<Card> cardsOf(President game, EventData eventData) {
        if (!(eventData instanceof PresidentEventData data) || data.cards() == null || data.cards().isEmpty()) {
            return List.of();
        }
        List<CardDto> named = data.cards();
        List<Card> candidates = new ArrayList<>(game.discard());
        game.trick().plays().forEach(play -> candidates.addAll(play.play().cards()));
        return candidates.stream().filter(card -> named.contains(CardDto.from(card))).distinct().toList();
    }

    // ---- scheduling --------------------------------------------------------------------------

    private void schedule(Table table) {
        President game = table.game;
        Duration grace = graceFor(table);
        for (BotSeat bot : table.bots.values()) {
            PresidentBotView view = PresidentBotView.forSeat(game, bot.seat);
            Step step = view.actions().isEmpty() ? null : Step.of(view);
            Pending current = table.pending.get(bot.seat);
            if (current != null && current.step().equals(step)) {
                continue; // the same step is already waiting its delay
            }
            table.cancel(bot.seat);
            if (step != null) {
                PlayerId seat = bot.seat;
                // seeing the exchange is not a decision: the bots acknowledge at once, so they never make a human wait
                Duration delay = step.phase() == Phase.EXCHANGE_VIEW ? Duration.ZERO : thinkingDelay.next();
                if (seat.equals(game.currentSeat()) && grace.compareTo(delay) > 0) {
                    delay = grace; // a human out of turn gets the time to see the window and complete
                }
                BotScheduler.BotTask task = scheduler.schedule(delay, () -> act(game, seat, step));
                table.pending.put(seat, new Pending(step, task));
            }
        }
    }

    /**
     * The grace the bot on turn gets: the completion grace when a human seat that is not on turn holds the
     * completion of a window it was not yet given the grace for, else nothing. The window is known by the round
     * and the cards of its chain, which no later window of the round can share; passes keep it, so the grace is
     * given once and the next bots think as usual.
     */
    private Duration graceFor(Table table) {
        President game = table.game;
        if (completionGrace.isZero() || game.phase() != Phase.PLAYING || game.trick().chain() == null) {
            return Duration.ZERO;
        }
        PlayerId current = game.currentSeat();
        if (!table.bots.containsKey(current)) {
            return Duration.ZERO; // a human on turn: nobody is made to wait
        }
        boolean humanHolds = game.getPlayerIds().stream()
                .filter(seat -> !table.bots.containsKey(seat) && !seat.equals(current))
                .anyMatch(seat -> !game.completion(seat).isEmpty());
        if (!humanHolds) {
            return Duration.ZERO;
        }
        Object window = List.of(game.round(), game.trick().chain().cards());
        if (window.equals(table.gracedWindow)) {
            return Duration.ZERO;
        }
        table.gracedWindow = window;
        return completionGrace;
    }

    /** Whether the table still follows this game, which is not over. */
    private Table followed(President game) {
        Table table = tables.get(game.getId());
        return table != null && table.game == game && !game.isFinished() ? table : null;
    }

    private void act(President game, PlayerId seat, Step step) {
        synchronized (game) {
            Table table = followed(game);
            if (table == null) {
                return;
            }
            Pending pending = table.pending.get(seat);
            if (pending != null && pending.step().equals(step)) {
                table.pending.remove(seat);
            }
            BotSeat bot = table.bots.get(seat);
            if (bot == null) {
                return;
            }
            PresidentBotView view = PresidentBotView.forSeat(game, seat);
            if (view.actions().isEmpty() || !Step.of(view).equals(step)) {
                return; // the state moved on without a broadcast since this bot started thinking
            }
            PresidentBotDecision decision = null;
            try {
                decision = bot.strategy.decide(view, bot.memory);
            } catch (RuntimeException e) {
                log.warn("Bot {} failed to decide in game {}; using a fixed action", seat, game.getId(), e);
            }
            try {
                if (decision == null) {
                    throw new IllegalStateException("no decision");
                }
                apply(game, seat, decision);
            } catch (PresidentException | RuntimeException e) {
                log.warn("Bot {} proposed {} that game {} refused; using a fixed action", seat, decision,
                        game.getId());
                fallback(game, seat, view);
            }
        }
    }

    private void apply(President game, PlayerId seat, PresidentBotDecision decision) throws PresidentException {
        GameId id = game.getId();
        if (decision instanceof PresidentBotDecision.Play play) {
            actions.play(id, seat, play.cards());
        } else if (decision instanceof PresidentBotDecision.Complete complete) {
            actions.play(id, seat, complete.cards()); // the same command as a play, in turn or out of turn
        } else if (decision instanceof PresidentBotDecision.Keep) {
            return; // the cards of a completion are kept: nothing to send
        } else if (decision instanceof PresidentBotDecision.Pass) {
            actions.pass(id, seat);
        } else if (decision instanceof PresidentBotDecision.GiveBack giveBack) {
            actions.giveBack(id, seat, giveBack.cards());
        } else if (decision instanceof PresidentBotDecision.SeeExchange) {
            actions.seeExchange(id, seat, game.round());
        } else {
            actions.ready(id, seat);
        }
    }

    /** The lowest legal set when leading, a pass when following, the lowest cards owed, ready. */
    private void fallback(President game, PlayerId seat, PresidentBotView view) {
        GameId id = game.getId();
        try {
            switch (view.phase()) {
                case EXCHANGE -> {
                    int owed = view.pendingReturns().stream()
                            .filter(p -> p.high().equals(seat) && !p.done())
                            .mapToInt(PendingReturn::count).sum();
                    actions.giveBack(id, seat, HandSort.sorted(view.hand(), ORDER).subList(0, owed));
                }
                case EXCHANGE_VIEW -> actions.seeExchange(id, seat, view.round());
                case ROUND_OVER -> actions.ready(id, seat);
                case PLAYING -> {
                    if (!view.can(Action.PLAY) && !view.can(Action.PASS)) {
                        return; // a completion out of turn that the game refused (a play came first): dropped
                    }
                    boolean leading = view.trick().plays().isEmpty();
                    if (!leading && view.can(Action.PASS)) {
                        actions.pass(id, seat);
                    } else {
                        actions.play(id, seat, view.legalPlays().get(0).cards());
                    }
                }
                case FINISHED -> { }
            }
        } catch (PresidentException | RuntimeException e) {
            log.error("Bot {} fallback failed in game {}", seat, id, e);
        }
    }

    // ---- inspection for tests ----------------------------------------------------------------

    int trackedGameCount() {
        return tables.size();
    }
}
