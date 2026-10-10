package org.kevinkib.cardgames.bullshit.presentation.bot;

import org.kevinkib.cardgames.bullshit.domain.Action;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.bot.BotDecision;
import org.kevinkib.cardgames.bullshit.domain.bot.BotMemory;
import org.kevinkib.cardgames.bullshit.domain.bot.BotObservation;
import org.kevinkib.cardgames.bullshit.domain.bot.BotStrategy;
import org.kevinkib.cardgames.bullshit.presentation.BullshitCardMapper;
import org.kevinkib.cardgames.bullshit.presentation.BullshitGameActions;
import org.kevinkib.cardgames.bullshit.presentation.BullshitStateListener;
import org.kevinkib.cardgames.bullshit.presentation.dto.event.BullshitEventType;
import org.kevinkib.cardgames.bullshit.presentation.dto.event.CallBullshitEventData;
import org.kevinkib.cardgames.bullshit.presentation.dto.event.DiscardEventData;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.kevinkib.cards.domain.Card;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides when a bot must think and applies its decision through the same use cases as a human.
 *
 * <p>After every state broadcast it cancels the tasks of the previous state and schedules one task
 * per bot seat that has an action, each after a thinking delay. A task remembers the game version it
 * reasoned about and is dropped if the state moved on; the check and the action run under the game's
 * monitor, so two bots can never both act on the same claim. Everything that touches a game's bot
 * state runs under that monitor, so tasks, human actions and forfeits are serialised per game.
 *
 * <p>When no human is left in a live game (all forfeited) nothing is scheduled: the game is left to
 * the idle eviction. A bot that fails (strategy or rules exception) falls back to discarding its
 * lowest card so the table does not stall.
 */
public class BullshitBotCoordinator implements BullshitStateListener, GameEvictionListener {

    private static final Logger log = LoggerFactory.getLogger(BullshitBotCoordinator.class);

    private final BotSeats botSeats;
    private final BullshitGameActions actions;
    private final BotScheduler scheduler;
    private final ThinkingDelay thinkingDelay;
    private final BotStrategyFactory strategies;
    private final Map<GameId, Table> tables = new ConcurrentHashMap<>();

    public BullshitBotCoordinator(BotSeats botSeats,
                                  BullshitGameActions actions,
                                  BotScheduler scheduler,
                                  ThinkingDelay thinkingDelay,
                                  BotStrategyFactory strategies) {
        this.botSeats = botSeats;
        this.actions = actions;
        this.scheduler = scheduler;
        this.thinkingDelay = thinkingDelay;
        this.strategies = strategies;
    }

    /** What the coordinator keeps for one game with bots. */
    private static final class Table {
        final Bullshit game;
        final BotStrategy strategy;
        final Map<PlayerId, BotMemory> memories = new HashMap<>();
        final List<BotScheduler.BotTask> pending = new ArrayList<>();
        PlayerId unresolvedClaimant;
        int scheduledVersion = -1;

        Table(Bullshit game, BotStrategy strategy) {
            this.game = game;
            this.strategy = strategy;
        }

        synchronized void cancelPending() {
            pending.forEach(BotScheduler.BotTask::cancel);
            pending.clear();
        }

        synchronized void track(BotScheduler.BotTask task) {
            pending.add(task);
        }

        BotMemory memoryOf(PlayerId seat) {
            return memories.computeIfAbsent(seat, BotMemory::new);
        }
    }

    // ---- state changes -----------------------------------------------------------------------

    @Override
    public void onBroadcast(Bullshit game, String eventType, EventData eventData) {
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
                drop(id); // the room was reopened for a rematch: this is a new game under the same id
                table = null;
            }
            if (table == null) {
                table = new Table(game, strategies.forGame(game));
                tables.put(id, table);
            }
            if (table.scheduledVersion == game.version()) {
                return; // not a state change (a connection notice, for instance): bots keep thinking
            }
            table.cancelPending();
            learn(table, bots, eventType, eventData);
            table.scheduledVersion = game.version();
            schedule(game, table, bots);
        }
    }

    @Override
    public void onEvicted(GameId id) {
        drop(id);
    }

    private Set<PlayerId> botsIn(Bullshit game) {
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

    /** Feeds every bot's memory from public events only. */
    private void learn(Table table, Set<PlayerId> bots, String eventType, EventData eventData) {
        for (PlayerId bot : bots) {
            table.memoryOf(bot);
        }
        if (BullshitEventType.DISCARD.toString().equals(eventType) && eventData instanceof DiscardEventData discard) {
            resolveClaim(table);
            table.unresolvedClaimant = new PlayerId(discard.claimantSeat());
        } else if (BullshitEventType.CALL_BULLSHIT.toString().equals(eventType)
                && eventData instanceof CallBullshitEventData call) {
            resolveClaim(table);
            List<Card> revealed = BullshitCardMapper.toCards(call.revealedCards());
            PlayerId picker = new PlayerId(call.pickerSeat());
            table.memories.values().forEach(memory -> {
                memory.onReveal(picker, revealed);
                memory.onPileTaken();
            });
        }
    }

    /**
     * What a bot knows about a claimant stays valid while their claim is judged; it is dropped once
     * the claim is resolved (called, or buried by the next discard), since the bot cannot tell which
     * of the known cards left.
     */
    private void resolveClaim(Table table) {
        if (table.unresolvedClaimant != null) {
            PlayerId claimant = table.unresolvedClaimant;
            table.memories.values().forEach(memory -> memory.onDiscardBy(claimant));
            table.unresolvedClaimant = null;
        }
    }

    private void schedule(Bullshit game, Table table, Set<PlayerId> bots) {
        int version = game.version();
        for (PlayerId seat : bots) {
            if (game.getAvailableActions(seat).isEmpty()) {
                continue;
            }
            table.track(scheduler.schedule(thinkingDelay.next(), () -> think(game, seat, version)));
        }
    }

    // ---- a bot acts --------------------------------------------------------------------------

    private void think(Bullshit game, PlayerId seat, int version) {
        synchronized (game) {
            Table table = tables.get(game.getId());
            if (table == null || game.isFinished() || game.version() != version) {
                return; // the state moved on since this bot started thinking
            }
            BotMemory memory = table.memoryOf(seat);
            try {
                BotDecision decision = table.strategy.decide(BotObservation.forSeat(game, seat), memory);
                apply(game, seat, memory, decision);
            } catch (Exception e) {
                log.warn("Bot {} failed in game {}; falling back to its lowest card", seat, game.getId(), e);
                fallback(game, seat, memory);
            }
        }
    }

    private void apply(Bullshit game, PlayerId seat, BotMemory memory, BotDecision decision) throws Exception {
        if (decision instanceof BotDecision.CallBullshit) {
            actions.callBullshit(game.getId(), seat);
        } else if (decision instanceof BotDecision.Discard discard) {
            actions.discard(game.getId(), seat, discard.cards());
            memory.onOwnDiscard(discard.cards());
        }
        // Pass: nothing to do until the next state change.
    }

    private void fallback(Bullshit game, PlayerId seat, BotMemory memory) {
        try {
            BotObservation observation = BotObservation.forSeat(game, seat);
            if (!observation.can(Action.DISCARD) || observation.hand().isEmpty()) {
                return;
            }
            Card lowest = observation.hand().stream()
                    .min(Comparator.comparingInt(card -> card.getRank().getStrength()))
                    .orElseThrow();
            actions.discard(game.getId(), seat, List.of(lowest));
            memory.onOwnDiscard(List.of(lowest));
        } catch (Exception e) {
            log.error("Bot {} fallback failed in game {}", seat, game.getId(), e);
        }
    }

    // ---- inspection for tests ----------------------------------------------------------------

    int trackedGameCount() {
        return tables.size();
    }

    BotMemory memoryOf(GameId id, PlayerId seat) {
        Table table = tables.get(id);
        return table == null ? null : table.memories.get(seat);
    }
}
