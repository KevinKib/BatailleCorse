package org.kevinkib.cardgames.bataillecorse.presentation.bot;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.Player;
import org.kevinkib.cardgames.bataillecorse.domain.bot.BatailleCorseBotStrategy;
import org.kevinkib.cardgames.bataillecorse.domain.bot.BatailleCorseBotStrategy.Move;
import org.kevinkib.cardgames.bataillecorse.domain.bot.BatailleCorseBotTier;
import org.kevinkib.cardgames.bataillecorse.presentation.BatailleCorseGameActions;
import org.kevinkib.cardgames.presentation.state.GameStateListener;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.bot.BotSeats;
import org.kevinkib.cardgames.presentation.bot.ThinkingDelay;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameDirectory;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Plays the computer seats of Bataille Corse rooms the way the former local AI did: after every state
 * change the bot waits its tier reaction time, then slaps if the pile is slappable, otherwise sends if
 * it is its turn. When the bot is entitled to collect a won pile it grabs it itself after 1500 ms, the
 * delay the client used to apply for it.
 *
 * <p>After every state broadcast the tasks of the previous state are cancelled and new ones scheduled
 * (the old AI replaced its pending action on each state update). A task remembers the game version it
 * was scheduled for and is dropped if the state moved on; the check and the action run under the game
 * monitor, so a bot and a human never act on the same state. A task also checks that its game is still
 * the live one of its room, which covers the lobby being reopened by Play again.
 *
 * <p>The table of a game is created on its first broadcast (START), dropped when the game is over, when
 * no human is left, on eviction and when a new game replaces it under the same id (the next START after
 * Play again). The reaction time is the tier time plus a jitter of +/- {@link BatailleCorseBotTier#JITTER_MS}
 * (the {@link ThinkingDelay} supplies a value in {@code [0, 2 * JITTER_MS)} added to the tier time minus
 * the jitter bound), plus an optional extra delay.
 */
public class BatailleCorseBotCoordinator implements GameStateListener<BatailleCorse>, GameEvictionListener {

    /** How long a won pile waits before the bot collects it, as the client used to wait. */
    public static final Duration AUTO_GRAB_DELAY = Duration.ofMillis(1500);

    private static final Logger log = LoggerFactory.getLogger(BatailleCorseBotCoordinator.class);

    private final BotSeats botSeats;
    private final BotSeatOptions seatOptions;
    private final GameDirectory games;
    private final BatailleCorseGameActions actions;
    private final BotScheduler scheduler;
    private final ThinkingDelay jitter;
    private final Duration extraDelay;
    private final BatailleCorseBotStrategy strategy = new BatailleCorseBotStrategy();
    private final Map<GameId, Table> tables = new ConcurrentHashMap<>();

    public BatailleCorseBotCoordinator(BotSeats botSeats,
                                       BotSeatOptions seatOptions,
                                       GameDirectory games,
                                       BatailleCorseGameActions actions,
                                       BotScheduler scheduler,
                                       ThinkingDelay jitter,
                                       Duration extraDelay) {
        this.botSeats = botSeats;
        this.seatOptions = seatOptions;
        this.games = games;
        this.actions = actions;
        this.scheduler = scheduler;
        this.jitter = jitter;
        this.extraDelay = extraDelay;
    }

    /** What the coordinator keeps for one game with bots. */
    private static final class Table {
        final BatailleCorse game;
        final List<BotScheduler.BotTask> pending = new ArrayList<>();
        int scheduledVersion = -1;

        Table(BatailleCorse game) {
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
    public void onBroadcast(BatailleCorse game, String eventType, EventData eventData) {
        synchronized (game) {
            GameId id = game.getId();
            Set<PlayerId> bots = botsIn(game);
            if (bots.isEmpty() || game.isFinished() || bots.size() == game.getPlayerIds().size()) {
                // No bots, game over, or no human left: nothing to drive.
                drop(id);
                return;
            }

            Table table = tables.get(id);
            if (table != null && table.game != game) {
                drop(id); // the room was reopened: this is a new game under the same id
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
            schedule(game, table, bots);
        }
    }

    @Override
    public void onEvicted(GameId id) {
        drop(id);
    }

    private Set<PlayerId> botsIn(BatailleCorse game) {
        Set<PlayerId> seats;
        try {
            seats = botSeats.botSeats(game.getId());
        } catch (RuntimeException e) {
            return Set.of();
        }
        return game.getPlayerIds().stream().filter(seats::contains).collect(Collectors.toSet());
    }

    private void drop(GameId id) {
        Table table = tables.remove(id);
        if (table != null) {
            table.cancelPending();
        }
    }

    private void schedule(BatailleCorse game, Table table, Set<PlayerId> bots) {
        int version = game.version();
        Optional<Player> grabber = game.autoGrabPlayer();
        for (PlayerId seat : bots) {
            table.track(scheduler.schedule(reactionTime(game, seat), () -> act(game, seat, version)));
            if (grabber.isPresent() && grabber.get().id().equals(seat)) {
                table.track(scheduler.schedule(AUTO_GRAB_DELAY, () -> grab(game, seat, version)));
            }
        }
    }

    private Duration reactionTime(BatailleCorse game, PlayerId seat) {
        BatailleCorseBotTier tier = BatailleCorseBotTier.fromOption(
                seatOptions.seatOptions(game.getId(), seat).get("tier"));
        return Duration.ofMillis(tier.reactionTimeMs() - BatailleCorseBotTier.JITTER_MS)
                .plus(jitter.next())
                .plus(extraDelay);
    }

    // ---- a bot acts --------------------------------------------------------------------------

    private void act(BatailleCorse game, PlayerId seat, int version) {
        synchronized (game) {
            if (!isCurrent(game, version)) {
                return; // the state moved on since this bot started reacting
            }
            try {
                Move move = strategy.decide(game, seat);
                if (move == Move.SLAP) {
                    actions.slap(game.getId(), seat); // the server validates; the bot accepts any penalty
                } else if (move == Move.SEND) {
                    actions.send(game.getId(), seat);
                }
            } catch (Exception e) {
                log.warn("Bot {} could not act in game {}", seat, game.getId(), e);
            }
        }
    }

    private void grab(BatailleCorse game, PlayerId seat, int version) {
        synchronized (game) {
            if (!isCurrent(game, version)
                    || game.autoGrabPlayer().filter(player -> player.id().equals(seat)).isEmpty()) {
                return;
            }
            try {
                actions.grab(game.getId(), seat);
            } catch (Exception e) {
                log.warn("Bot {} could not grab the pile in game {}", seat, game.getId(), e);
            }
        }
    }

    /** The table still tracks this game, it is not over, it is the room live game, and its state is unchanged. */
    private boolean isCurrent(BatailleCorse game, int version) {
        Table table = tables.get(game.getId());
        if (table == null || table.game != game || game.isFinished() || game.version() != version) {
            return false;
        }
        Optional<Game> live = games.findGame(game.getId());
        return live.isPresent() && live.get() == game;
    }
}
