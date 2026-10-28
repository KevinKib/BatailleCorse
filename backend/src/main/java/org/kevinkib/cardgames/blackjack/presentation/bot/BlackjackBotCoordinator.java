package org.kevinkib.cardgames.blackjack.presentation.bot;

import org.kevinkib.cardgames.blackjack.domain.Action;
import org.kevinkib.cardgames.blackjack.domain.Blackjack;
import org.kevinkib.cardgames.blackjack.domain.BlackjackFactory;
import org.kevinkib.cardgames.blackjack.domain.BlackjackOptions;
import org.kevinkib.cardgames.blackjack.domain.Phase;
import org.kevinkib.cardgames.blackjack.domain.bot.BlackjackBotLevel;
import org.kevinkib.cardgames.blackjack.domain.bot.BlackjackBotStrategy;
import org.kevinkib.cardgames.blackjack.domain.bot.BlackjackBotView;
import org.kevinkib.cardgames.blackjack.presentation.BlackjackGameActions;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.bot.BotSeats;
import org.kevinkib.cardgames.presentation.bot.ThinkingDelay;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.presentation.state.GameStateListener;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Decides when a computer seat must act and applies its decision through the same use case as a human.
 *
 * <p>After every state broadcast that changed the game it looks at the phase. While <b>betting</b>, every computer
 * seat that has not bet gets its own task after a thinking delay (the bets are simultaneous and sealed, so the bots do
 * not wait for each other); the tasks of one hand survive the broadcasts of the other bets. While <b>playing</b>, it
 * cancels the task of the previous state and, when the seat on turn is a computer seat, schedules one task for it. A
 * play task remembers the game version it reasoned about; a bet task the hand number.
 *
 * <p><b>The decision is taken outside the monitor of the game</b>, which a human action and a forfeit take: a task
 * takes the seat's view under the monitor and drops itself if the state moved on, decides without any lock, then takes
 * the monitor again, checks a second time and only then acts. A stale answer is thrown away. The level is read from the
 * seat option {@code level}.
 *
 * <p>A strategy that fails or proposes a refused bet or action falls back to the smallest bet or to {@code STAND}, so
 * the table never stalls. Nothing is scheduled once the game is over or after it was evicted; a new game under the
 * same id (Play again) gets a fresh table.
 */
public class BlackjackBotCoordinator implements GameStateListener<Blackjack>, GameEvictionListener {

    private static final Logger log = LoggerFactory.getLogger(BlackjackBotCoordinator.class);

    private final BotSeats botSeats;
    private final BotSeatOptions seatOptions;
    private final BlackjackGameActions actions;
    private final BotScheduler scheduler;
    private final ThinkingDelay thinkingDelay;
    private final Function<BlackjackBotLevel, BlackjackBotStrategy> strategies;
    private final Map<GameId, Table> tables = new ConcurrentHashMap<>();

    public BlackjackBotCoordinator(BotSeats botSeats,
                                   BotSeatOptions seatOptions,
                                   BlackjackGameActions actions,
                                   BotScheduler scheduler,
                                   ThinkingDelay thinkingDelay,
                                   Function<BlackjackBotLevel, BlackjackBotStrategy> strategies) {
        this.botSeats = botSeats;
        this.seatOptions = seatOptions;
        this.actions = actions;
        this.scheduler = scheduler;
        this.thinkingDelay = thinkingDelay;
        this.strategies = strategies;
    }

    /** What the coordinator keeps for one game with bots. */
    private static final class Table {
        final Blackjack game;
        BotScheduler.BotTask pendingPlay;
        final Map<PlayerId, BotScheduler.BotTask> betTasks = new HashMap<>();
        int betHand = -1;
        int scheduledVersion = -1;

        Table(Blackjack game) {
            this.game = game;
        }

        synchronized void cancelPlay() {
            if (pendingPlay != null) {
                pendingPlay.cancel();
                pendingPlay = null;
            }
        }

        synchronized void cancelBets() {
            betTasks.values().forEach(BotScheduler.BotTask::cancel);
            betTasks.clear();
            betHand = -1;
        }

        synchronized void cancelAll() {
            cancelPlay();
            cancelBets();
        }
    }

    // ---- state changes -----------------------------------------------------------------------

    @Override
    public void onBroadcast(Blackjack game, String eventType, EventData eventData) {
        synchronized (game) {
            GameId id = game.getId();
            Set<PlayerId> bots;
            try {
                bots = botsIn(game);
            } catch (RuntimeException e) {
                // Unknown is not "no bots": keep what is armed and look again shortly, so the table never stalls.
                log.warn("The bot seats of game {} could not be read; keeping the current tasks and retrying", id, e);
                retryLookup(game);
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
            if (table.scheduledVersion == game.version()) {
                return; // not a state change (a connection notice, a second event of the same command)
            }
            table.scheduledVersion = game.version();
            if (game.phase() == Phase.BETTING) {
                scheduleBets(game, table, bots);
            } else {
                table.cancelBets();
                table.cancelPlay();
                PlayerId current = game.currentSeat();
                if (game.phase() == Phase.PLAYING && current != null && bots.contains(current)) {
                    int version = game.version();
                    table.pendingPlay = scheduler.schedule(thinkingDelay.next(), () -> thinkPlay(game, current, version));
                }
            }
        }
    }

    private void scheduleBets(Blackjack game, Table table, Set<PlayerId> bots) {
        synchronized (table) {
            table.cancelPlay();
            int hand = game.handNumber();
            if (table.betHand != hand) {
                table.cancelBets();
                table.betHand = hand;
            }
            for (PlayerId bot : game.getPlayerIds()) {
                if (bots.contains(bot) && !table.betTasks.containsKey(bot)
                        && game.availableActions(bot).contains(Action.BET)) {
                    table.betTasks.put(bot, scheduler.schedule(thinkingDelay.next(), () -> thinkBet(game, bot, hand)));
                }
            }
        }
    }

    @Override
    public void onEvicted(GameId id) {
        drop(id);
    }

    private Set<PlayerId> botsIn(Blackjack game) {
        Set<PlayerId> seats = botSeats.botSeats(game.getId());
        return game.getPlayerIds().stream().filter(seats::contains).collect(Collectors.toSet());
    }

    /**
     * After a failed lookup: when the state this table last reasoned about is not the current one (so no task is
     * armed for it), arms a retry of the whole decision. A task armed for the current state is left alone.
     */
    private void retryLookup(Blackjack game) {
        Table table = tables.get(game.getId());
        if (table == null || table.game != game || game.isFinished()) {
            return; // nothing was ever armed for this game: the next broadcast looks again
        }
        if (table.scheduledVersion == game.version()) {
            return;
        }
        table.cancelPlay();
        table.pendingPlay = scheduler.schedule(thinkingDelay.next(),
                () -> onBroadcast(game, "RETRY", new EmptyEventData()));
    }

    private void drop(GameId id) {
        Table table = tables.remove(id);
        if (table != null) {
            table.cancelAll();
        }
    }

    // ---- a bot bets --------------------------------------------------------------------------

    private void thinkBet(Blackjack game, PlayerId seat, int hand) {
        BlackjackBotView view;
        BlackjackBotLevel level;
        synchronized (game) {
            if (!stillBetting(game, seat, hand)) {
                return; // the hand moved on, or the seat is no longer waiting to bet
            }
            try {
                view = BlackjackBotView.forSeat(game, seat);
                level = levelOf(game, seat);
            } catch (RuntimeException e) {
                log.warn("Bot {} could not be set up in game {}; falling back to the smallest bet", seat,
                        game.getId(), e);
                fallbackBet(game, seat);
                return;
            }
        }
        Integer amount = null;
        try {
            amount = strategies.apply(level).decideBet(view);
        } catch (Exception e) {
            log.warn("Bot {} failed in game {}; falling back to the smallest bet", seat, game.getId(), e);
        }
        synchronized (game) {
            if (!stillBetting(game, seat, hand)) {
                return; // a forfeit moved the game while it was thinking: this answer is stale
            }
            try {
                if (amount == null) {
                    throw new IllegalStateException("no decision");
                }
                actions.bet(game.getId(), seat, amount);
            } catch (Exception e) {
                log.warn("Bot {} proposed an illegal bet in game {}; falling back to the smallest bet", seat,
                        game.getId(), e);
                fallbackBet(game, seat);
            }
        }
    }

    private boolean stillBetting(Blackjack game, PlayerId seat, int hand) {
        Table table = tables.get(game.getId());
        return table != null && table.game == game && !game.isFinished() && game.phase() == Phase.BETTING
                && game.handNumber() == hand && game.availableActions(seat).contains(Action.BET);
    }

    private void fallbackBet(Blackjack game, PlayerId seat) {
        try {
            actions.bet(game.getId(), seat, BlackjackOptions.MIN_BET);
        } catch (Exception e) {
            log.error("Bot {} bet fallback failed in game {}", seat, game.getId(), e);
        }
    }

    // ---- a bot plays -------------------------------------------------------------------------

    private void thinkPlay(Blackjack game, PlayerId seat, int version) {
        BlackjackBotView view;
        BlackjackBotLevel level;
        synchronized (game) {
            if (!unchanged(game, version)) {
                return; // the state moved on since this task was scheduled
            }
            try {
                view = BlackjackBotView.forSeat(game, seat);
                level = levelOf(game, seat);
            } catch (RuntimeException e) {
                log.warn("Bot {} could not be set up in game {}; falling back to standing", seat, game.getId(), e);
                fallbackStand(game, seat);
                return;
            }
        }
        Action action = null;
        try {
            action = strategies.apply(level).decidePlay(view);
        } catch (Exception e) {
            log.warn("Bot {} failed in game {}; falling back to standing", seat, game.getId(), e);
        }
        synchronized (game) {
            if (!unchanged(game, version)) {
                return; // a forfeit moved the game while it was thinking: this answer is stale
            }
            try {
                if (action == null) {
                    throw new IllegalStateException("no decision");
                }
                switch (action) {
                    case HIT -> actions.hit(game.getId(), seat);
                    case STAND -> actions.stand(game.getId(), seat);
                    case DOUBLE -> actions.doubleDown(game.getId(), seat);
                    case SPLIT -> actions.split(game.getId(), seat);
                    default -> throw new IllegalStateException("not a play: " + action);
                }
            } catch (Exception e) {
                log.warn("Bot {} proposed an illegal action in game {}; falling back to standing", seat,
                        game.getId(), e);
                fallbackStand(game, seat);
            }
        }
    }

    /** Whether the table still follows this game and the state is the one the task reasoned about. */
    private boolean unchanged(Blackjack game, int version) {
        Table table = tables.get(game.getId());
        return table != null && table.game == game && !game.isFinished() && game.version() == version;
    }

    private BlackjackBotLevel levelOf(Blackjack game, PlayerId seat) {
        return BlackjackBotLevel.fromOption(
                seatOptions.seatOptions(game.getId(), seat).get(BlackjackFactory.LEVEL_KEY));
    }

    private void fallbackStand(Blackjack game, PlayerId seat) {
        try {
            actions.stand(game.getId(), seat);
        } catch (Exception e) {
            log.error("Bot {} fallback failed in game {}", seat, game.getId(), e);
        }
    }
}
