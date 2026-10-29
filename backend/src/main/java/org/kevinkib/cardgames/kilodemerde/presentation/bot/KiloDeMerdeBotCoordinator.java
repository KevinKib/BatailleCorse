package org.kevinkib.cardgames.kilodemerde.presentation.bot;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeFactory;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeRules;
import org.kevinkib.cardgames.kilodemerde.domain.Phase;
import org.kevinkib.cardgames.kilodemerde.domain.bot.KiloDeMerdeBotLevel;
import org.kevinkib.cardgames.kilodemerde.domain.bot.KiloDeMerdeBotStrategy;
import org.kevinkib.cardgames.kilodemerde.domain.bot.KiloDeMerdeBotView;
import org.kevinkib.cardgames.kilodemerde.domain.bot.KiloDeMerdeReflex;
import org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeGameActions;
import org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeReadyKey;
import org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeStepKey;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.bot.BotSeats;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.presentation.state.GameStateListener;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.kevinkib.cards.domain.Card;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * Decides when a computer seat must act and applies its decision through the same use cases as a human.
 *
 * <p>Unlike the turn-based games, Kilo de merde is a race: several bots act at the same time and one bot's slap must
 * not cancel the others'. After every state broadcast the coordinator works out which tasks should exist, then
 * cancels the ones that no longer should and arms the missing ones, never touching the ones that are still right.
 * Four kinds of task, each keyed on a domain counter and never on {@code version()}:
 * <ul>
 *   <li>PASS: while passing, a bot that has not chosen passes a card after the pass delay, keyed on the {@link
 *       KiloDeMerdeStepKey}; not when its own hand is four of a kind, since any pass would break the set;</li>
 *   <li>OWN SET: while passing, a bot whose own hand is four of a kind slaps after its own-set delay, keyed the same;</li>
 *   <li>FOLLOW: while a race is open, a bot that has not slapped slaps after its follow delay, keyed on the race;</li>
 *   <li>READY: while the table waits for confirmations (before the first round, between two rounds), a bot that is
 *       not ready confirms after a short reflex delay (the pass delay), keyed on the {@link KiloDeMerdeReadyKey}.</li>
 * </ul>
 * A bot never opens a race without a four of a kind in its own hand, so it never false-slaps. A task re-checks under
 * the monitor of the game that its key is still wanted and drops itself otherwise. A strategy that fails or proposes a
 * card outside the hand falls back to {@link KiloDeMerdeRules#defaultPass}. Nothing is scheduled once the game is
 * over or after it was evicted; a new game under the same id (Play again) gets a fresh table.
 */
public class KiloDeMerdeBotCoordinator implements GameStateListener<KiloDeMerde>, GameEvictionListener {

    private static final Logger log = LoggerFactory.getLogger(KiloDeMerdeBotCoordinator.class);

    private enum Kind { PASS, OWN_SET, FOLLOW, READY }

    /** One task of one seat; {@code counter} is the step key (PASS, OWN_SET), the race number (FOLLOW) or the ready key (READY). */
    private record TaskKey(Kind kind, PlayerId seat, Object counter) {
    }

    private final BotSeats botSeats;
    private final BotSeatOptions seatOptions;
    private final KiloDeMerdeGameActions actions;
    private final BotScheduler scheduler;
    private final KiloDeMerdeReflex reflex;
    private final RandomGenerator random;
    private final Function<KiloDeMerdeBotLevel, KiloDeMerdeBotStrategy> strategies;
    private final Map<GameId, Table> tables = new ConcurrentHashMap<>();

    public KiloDeMerdeBotCoordinator(BotSeats botSeats,
                                     BotSeatOptions seatOptions,
                                     KiloDeMerdeGameActions actions,
                                     BotScheduler scheduler,
                                     KiloDeMerdeReflex reflex,
                                     RandomGenerator random,
                                     Function<KiloDeMerdeBotLevel, KiloDeMerdeBotStrategy> strategies) {
        this.botSeats = botSeats;
        this.seatOptions = seatOptions;
        this.actions = actions;
        this.scheduler = scheduler;
        this.reflex = reflex;
        this.random = random;
        this.strategies = strategies;
    }

    /** What the coordinator keeps for one game with bots. */
    private static final class Table {
        final KiloDeMerde game;
        final Map<TaskKey, BotScheduler.BotTask> tasks = new HashMap<>();

        Table(KiloDeMerde game) {
            this.game = game;
        }

        void cancelAll() {
            tasks.values().forEach(BotScheduler.BotTask::cancel);
            tasks.clear();
        }
    }

    // ---- state changes -----------------------------------------------------------------------

    @Override
    public void onBroadcast(KiloDeMerde game, String eventType, EventData eventData) {
        synchronized (game) {
            GameId id = game.getId();
            Set<PlayerId> bots;
            try {
                bots = botsIn(game);
            } catch (RuntimeException e) {
                // Unknown is not "no bots": keep what is armed; the next broadcast looks again.
                log.warn("The bot seats of game {} could not be read; keeping the current tasks", id, e);
                return;
            }
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
            Set<TaskKey> wanted = wantedTasks(game, bots);
            table.tasks.entrySet().removeIf(entry -> {
                if (wanted.contains(entry.getKey())) {
                    return false;
                }
                entry.getValue().cancel();
                return true;
            });
            for (TaskKey key : wanted) {
                if (!table.tasks.containsKey(key)) {
                    table.tasks.put(key, scheduler.schedule(delayOf(game, key), () -> run(game, key)));
                }
            }
        }
    }

    @Override
    public void onEvicted(GameId id) {
        drop(id);
    }

    private Set<PlayerId> botsIn(KiloDeMerde game) {
        Set<PlayerId> seats = botSeats.botSeats(game.getId());
        return game.getPlayerIds().stream().filter(seats::contains).collect(Collectors.toSet());
    }

    private void drop(GameId id) {
        Table table = tables.remove(id);
        if (table != null) {
            synchronized (table.game) {
                table.cancelAll();
            }
        }
    }

    /** The tasks the game calls for right now, read under the monitor of the game. */
    private Set<TaskKey> wantedTasks(KiloDeMerde game, Set<PlayerId> bots) {
        Set<TaskKey> wanted = new HashSet<>();
        if (game.phase() == Phase.PASSING) {
            KiloDeMerdeStepKey step = KiloDeMerdeStepKey.of(game);
            for (PlayerId seat : game.activeSeats()) {
                if (!bots.contains(seat)) {
                    continue;
                }
                if (KiloDeMerdeRules.isFourOfAKind(game.hand(seat))) {
                    // Any pass would break the set (the step could resolve before the slap): slap, never pass.
                    wanted.add(new TaskKey(Kind.OWN_SET, seat, step));
                } else if (!game.hasChosen(seat)) {
                    wanted.add(new TaskKey(Kind.PASS, seat, step));
                }
            }
        } else if (KiloDeMerdeReadyKey.of(game) != null) {
            KiloDeMerdeReadyKey ready = KiloDeMerdeReadyKey.of(game);
            for (PlayerId seat : game.activeSeats()) {
                if (bots.contains(seat) && !game.isReady(seat)) {
                    wanted.add(new TaskKey(Kind.READY, seat, ready));
                }
            }
        } else if (game.phase() == Phase.RACE) {
            int race = game.raceId();
            for (PlayerId seat : game.activeSeats()) {
                if (bots.contains(seat) && !game.slapOrder().contains(seat)) {
                    wanted.add(new TaskKey(Kind.FOLLOW, seat, race));
                }
            }
        }
        return wanted;
    }

    private Duration delayOf(KiloDeMerde game, TaskKey key) {
        long ms = switch (key.kind()) {
            case PASS, READY -> reflex.passDelayMs(random);
            case OWN_SET -> reflex.ownSetDelayMs(levelOf(game, key.seat()), random);
            case FOLLOW -> reflex.followDelayMs(levelOf(game, key.seat()), random);
        };
        return Duration.ofMillis(ms);
    }

    private KiloDeMerdeBotLevel levelOf(KiloDeMerde game, PlayerId seat) {
        try {
            return KiloDeMerdeBotLevel.fromOption(
                    seatOptions.seatOptions(game.getId(), seat).get(KiloDeMerdeFactory.LEVEL_KEY));
        } catch (RuntimeException e) {
            log.warn("The level of bot {} in game {} could not be read; using the default", seat, game.getId(), e);
            return KiloDeMerdeBotLevel.DEFAULT;
        }
    }

    // ---- a bot acts --------------------------------------------------------------------------

    private void run(KiloDeMerde game, TaskKey key) {
        synchronized (game) {
            Table table = tables.get(game.getId());
            if (table == null || table.game != game || table.tasks.remove(key) == null) {
                return; // the table moved on, or the task was cancelled while it was starting
            }
            if (game.isFinished() || !wantedTasks(game, botsOrNone(game)).contains(key)) {
                return; // the step, the race or the round moved on since this task was armed
            }
            try {
                if (key.kind() == Kind.PASS) {
                    pass(game, key.seat());
                } else if (key.kind() == Kind.READY) {
                    actions.ready(game.getId(), key.seat());
                } else {
                    actions.slap(game.getId(), key.seat());
                }
            } catch (Exception e) {
                log.warn("Bot {} could not {} in game {}", key.seat(), key.kind(), game.getId(), e);
            }
        }
    }

    private Set<PlayerId> botsOrNone(KiloDeMerde game) {
        try {
            return botsIn(game);
        } catch (RuntimeException e) {
            log.warn("The bot seats of game {} could not be read", game.getId(), e);
            return Set.of();
        }
    }

    /** The bot passes the card its strategy chose, or the default pass when the strategy fails or cheats. */
    private void pass(KiloDeMerde game, PlayerId seat) throws Exception {
        KiloDeMerdeBotView view = KiloDeMerdeBotView.forSeat(game, seat);
        Card card;
        try {
            card = strategies.apply(levelOf(game, seat)).choosePass(view);
            if (!view.hand().contains(card)) {
                throw new IllegalStateException("The strategy proposed a card outside the hand: " + card);
            }
        } catch (Exception e) {
            log.warn("Bot {} failed in game {}; falling back to the default pass", seat, game.getId(), e);
            card = KiloDeMerdeRules.defaultPass(view.hand());
        }
        try {
            actions.choosePass(game.getId(), seat, card);
        } catch (Exception e) {
            if (card.equals(KiloDeMerdeRules.defaultPass(view.hand()))) {
                throw e;
            }
            log.warn("Bot {} was refused its pass in game {}; falling back to the default pass", seat, game.getId(), e);
            actions.choosePass(game.getId(), seat, KiloDeMerdeRules.defaultPass(view.hand()));
        }
    }
}
