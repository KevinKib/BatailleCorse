package org.kevinkib.cardgames.kobo.presentation.bot;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.KoboFactory;
import org.kevinkib.cardgames.kobo.domain.bot.KoboBotDecision;
import org.kevinkib.cardgames.kobo.domain.bot.KoboBotLevel;
import org.kevinkib.cardgames.kobo.domain.bot.KoboBotMemory;
import org.kevinkib.cardgames.kobo.domain.bot.KoboBotStrategy;
import org.kevinkib.cardgames.kobo.domain.bot.KoboBotTuning;
import org.kevinkib.cardgames.kobo.domain.bot.KoboBotView;
import org.kevinkib.cardgames.kobo.domain.rules.KoboException;
import org.kevinkib.cardgames.kobo.domain.tableau.SlotRef;
import org.kevinkib.cardgames.kobo.domain.turn.Action;
import org.kevinkib.cardgames.kobo.presentation.KoboGameActions;
import org.kevinkib.cardgames.kobo.presentation.dto.event.KoboEventData;
import org.kevinkib.cardgames.kobo.presentation.dto.event.KoboEventType;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.bot.BotSeats;
import org.kevinkib.cardgames.presentation.bot.ThinkingDelay;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.presentation.state.GameStateListener;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.kevinkib.cards.domain.Card;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/**
 * Decides when a Kobo computer seat must act and applies its decision through the same use cases as a human.
 *
 * <p>After every state broadcast, under the game's monitor, it feeds the memory of each bot (the public moves of
 * the broadcast first, then the seat's own view), then schedules two kinds of tasks:
 * <ul>
 *   <li>a <b>turn task</b> per bot that has a step to play (a barrier to ready, its turn, or the gift), after a
 *       thinking delay; it remembers the game version it reasoned about and is dropped if the state moved on;</li>
 *   <li>a <b>match task</b> per bot that remembers a card of the rank on top of the discard pile, after a reaction
 *       delay of its level; it is keyed on the discard top, not on the version, so unrelated actions (a draw, a
 *       failed match) do not restart the race; it is dropped only when the top changes.</li>
 * </ul>
 * A decision the game refuses falls back to a fixed legal action per phase (a King swap refused because the card
 * moved falls back to skipping the power), so the table never stalls; a refused match is a lost race and is
 * ignored. Nothing is scheduled once the game is over, no human is left, or it was evicted; a new game under the
 * same id (Play again) gets a fresh table and fresh memories. The level is read from the seat option
 * {@code level}.
 */
public class KoboBotCoordinator implements GameStateListener<Kobo>, GameEvictionListener {

    private static final Logger log = LoggerFactory.getLogger(KoboBotCoordinator.class);

    private final BotSeats botSeats;
    private final BotSeatOptions seatOptions;
    private final KoboGameActions actions;
    private final BotScheduler scheduler;
    private final ThinkingDelay thinkingDelay;
    private final ReactionDelay reactionDelay;
    private final Function<KoboBotLevel, KoboBotStrategy> strategies;
    private final RandomGenerator random;
    private final Map<GameId, Table> tables = new ConcurrentHashMap<>();

    public KoboBotCoordinator(BotSeats botSeats,
                              BotSeatOptions seatOptions,
                              KoboGameActions actions,
                              BotScheduler scheduler,
                              ThinkingDelay thinkingDelay,
                              ReactionDelay reactionDelay,
                              Function<KoboBotLevel, KoboBotStrategy> strategies,
                              RandomGenerator random) {
        this.botSeats = botSeats;
        this.seatOptions = seatOptions;
        this.actions = actions;
        this.scheduler = scheduler;
        this.thinkingDelay = thinkingDelay;
        this.reactionDelay = reactionDelay;
        this.strategies = strategies;
        this.random = random;
    }

    /** What identifies the top of the discard pile: a match is only worth trying while it is unchanged. */
    private record Top(Optional<Card> card, int size, int round) {

        static Top of(Kobo game) {
            return new Top(game.discardTop(), game.discardSize(), game.round());
        }
    }

    /** One bot seat of a table: its level, strategy, memory and the match it has scheduled, if any. */
    private static final class BotSeat {
        final PlayerId seat;
        final KoboBotTuning tuning;
        final KoboBotStrategy strategy;
        final KoboBotMemory memory;
        KoboBotDecision.Match scheduledMatch;
        BotScheduler.BotTask matchTask;

        BotSeat(PlayerId seat, KoboBotLevel level, KoboBotStrategy strategy) {
            this.seat = seat;
            this.tuning = level.tuning();
            this.strategy = strategy;
            this.memory = new KoboBotMemory(seat, tuning.followsMoves());
        }

        void cancelMatch() {
            if (matchTask != null) {
                matchTask.cancel();
            }
            matchTask = null;
            scheduledMatch = null;
        }
    }

    /** What the coordinator keeps for one game with bots. */
    private static final class Table {
        final Kobo game;
        final Map<PlayerId, BotSeat> bots = new LinkedHashMap<>();
        final List<BotScheduler.BotTask> turnTasks = new ArrayList<>();
        int scheduledVersion = -1;
        Top lastTop;

        Table(Kobo game) {
            this.game = game;
        }

        synchronized void cancelTurnTasks() {
            turnTasks.forEach(BotScheduler.BotTask::cancel);
            turnTasks.clear();
        }

        synchronized void trackTurnTask(BotScheduler.BotTask task) {
            turnTasks.add(task);
        }

        synchronized void cancelAll() {
            cancelTurnTasks();
            bots.values().forEach(BotSeat::cancelMatch);
        }
    }

    // ---- state changes -----------------------------------------------------------------------

    @Override
    public void onBroadcast(Kobo game, String eventType, EventData eventData) {
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
                return; // not a state change (a connection notice, for instance): the bots keep thinking
            }
            syncSeats(table, bots);
            if (eventData instanceof KoboEventData data) {
                feedMoves(table, data);
            }
            Map<PlayerId, KoboBotView> views = new LinkedHashMap<>();
            for (BotSeat bot : table.bots.values()) {
                KoboBotView view = KoboBotView.forSeat(game, bot.seat);
                bot.memory.observe(view);
                views.put(bot.seat, view);
            }
            table.scheduledVersion = game.version();
            scheduleTurns(table, views);
            scheduleMatches(table, views);
        }
    }

    @Override
    public void onEvicted(GameId id) {
        drop(id);
    }

    private Set<PlayerId> botsIn(Kobo game) {
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

    /** Creates the seat state of a new bot (level from its seat option) and forgets the seats that left. */
    private void syncSeats(Table table, Set<PlayerId> bots) {
        table.bots.entrySet().removeIf(entry -> {
            boolean gone = !bots.contains(entry.getKey());
            if (gone) {
                entry.getValue().cancelMatch();
            }
            return gone;
        });
        for (PlayerId seat : bots) {
            table.bots.computeIfAbsent(seat, s -> {
                KoboBotLevel level = levelOf(table.game, s);
                return new BotSeat(s, level, strategies.apply(level));
            });
        }
    }

    private KoboBotLevel levelOf(Kobo game, PlayerId seat) {
        try {
            return KoboBotLevel.fromOption(seatOptions.seatOptions(game.getId(), seat).get(KoboFactory.LEVEL_KEY));
        } catch (RuntimeException e) {
            return KoboBotLevel.DEFAULT;
        }
    }

    // ---- what the bots learn from a broadcast ------------------------------------------------

    /**
     * Public moves of the broadcast, as every human at the table can see them: a swap or a match gift relocates a
     * card a bot remembers; a bot's own swap places the card it held; a bot's end of turn makes it forget.
     */
    private void feedMoves(Table table, KoboEventData data) {
        PlayerId actor = new PlayerId(data.seat());
        String action = data.action();
        if (KoboEventType.SWAP.name().equals(action)) {
            BotSeat bot = table.bots.get(actor);
            if (bot != null && data.slot() != null) {
                bot.memory.onOwnDrawnPlaced(data.slot());
            }
        } else if ((KoboEventType.BLIND_SWAP.name().equals(action) || KoboEventType.KING_SWAP.name().equals(action))
                && data.slot() != null && data.targetSeat() != null && data.targetSlot() != null) {
            SlotRef own = new SlotRef(actor, data.slot());
            SlotRef other = new SlotRef(new PlayerId(data.targetSeat()), data.targetSlot());
            table.bots.values().forEach(bot -> bot.memory.onSwapped(own, other));
        } else if (KoboEventType.MATCH.name().equals(action) && Boolean.TRUE.equals(data.success())
                && data.givenSlot() != null && data.targetSeat() != null && data.targetSlot() != null) {
            SlotRef given = new SlotRef(actor, data.givenSlot());
            SlotRef target = new SlotRef(new PlayerId(data.targetSeat()), data.targetSlot());
            table.bots.values().forEach(bot -> bot.memory.onMoved(given, target));
        } else if (KoboEventType.END_TURN.name().equals(action) || KoboEventType.KOBO.name().equals(action)) {
            BotSeat bot = table.bots.get(actor);
            if (bot != null) {
                bot.memory.onOwnTurnEnded(random, bot.tuning.forgetProbability());
            }
        }
    }

    // ---- scheduling --------------------------------------------------------------------------

    private void scheduleTurns(Table table, Map<PlayerId, KoboBotView> views) {
        table.cancelTurnTasks();
        int version = table.game.version();
        for (BotSeat bot : table.bots.values()) {
            if (hasOwnStep(views.get(bot.seat))) {
                table.trackTurnTask(scheduler.schedule(thinkingDelay.next(),
                        () -> think(table.game, bot.seat, version)));
            }
        }
    }

    /** A barrier to ready, the seat's turn step or the gift: anything offered besides the matching discard. */
    private static boolean hasOwnStep(KoboBotView view) {
        return view.actions().stream().anyMatch(action -> action != Action.MATCH_DISCARD);
    }

    private void scheduleMatches(Table table, Map<PlayerId, KoboBotView> views) {
        Top top = Top.of(table.game);
        if (!top.equals(table.lastTop)) {
            table.bots.values().forEach(BotSeat::cancelMatch);
            table.lastTop = top;
        }
        for (BotSeat bot : table.bots.values()) {
            if (bot.matchTask != null) {
                continue;
            }
            Optional<KoboBotDecision.Match> match;
            try {
                match = bot.strategy.matchFor(views.get(bot.seat), bot.memory);
            } catch (RuntimeException e) {
                log.warn("Bot {} failed to look for a match in game {}", bot.seat, table.game.getId(), e);
                continue;
            }
            match.ifPresent(m -> {
                bot.scheduledMatch = m;
                bot.matchTask = scheduler.schedule(reactionDelay.next(bot.tuning),
                        () -> tryMatch(table.game, bot.seat, m, top));
            });
        }
    }

    // ---- a bot acts --------------------------------------------------------------------------

    private void think(Kobo game, PlayerId seat, int version) {
        synchronized (game) {
            BotSeat bot = liveSeat(game, seat);
            if (bot == null || game.version() != version) {
                return; // the state moved on since this bot started thinking
            }
            KoboBotDecision decision = null;
            try {
                decision = bot.strategy.decide(KoboBotView.forSeat(game, seat), bot.memory);
                apply(game.getId(), seat, decision);
            } catch (Exception e) {
                if (decision instanceof KoboBotDecision.KingSwap && isCardMoved(e)) {
                    log.debug("Bot {} in game {}: the card it looked at moved, skipping the power", seat, game.getId());
                } else {
                    log.warn("Bot {} failed in game {}; falling back to a legal action", seat, game.getId(), e);
                }
                fallback(game, seat);
            }
        }
    }

    private static boolean isCardMoved(Exception e) {
        return e instanceof org.kevinkib.cardgames.kobo.domain.tableau.StaleSlotException
                || e instanceof org.kevinkib.cardgames.kobo.domain.tableau.EmptySlotException;
    }

    /** The bot's state when the game is still live and tracked, else null. */
    private BotSeat liveSeat(Kobo game, PlayerId seat) {
        Table table = tables.get(game.getId());
        if (table == null || table.game != game || game.isFinished()) {
            return null;
        }
        return table.bots.get(seat);
    }

    private void apply(GameId id, PlayerId seat, KoboBotDecision decision) throws KoboException {
        if (decision instanceof KoboBotDecision.Ready) {
            actions.ready(id, seat);
        } else if (decision instanceof KoboBotDecision.Draw) {
            actions.draw(id, seat);
        } else if (decision instanceof KoboBotDecision.SwapDrawn d) {
            actions.swapDrawn(id, seat, d.slot());
        } else if (decision instanceof KoboBotDecision.DiscardDrawn) {
            actions.discardDrawn(id, seat);
        } else if (decision instanceof KoboBotDecision.Peek d) {
            actions.peek(id, seat, d.target());
        } else if (decision instanceof KoboBotDecision.BlindSwap d) {
            actions.blindSwap(id, seat, d.ownSlot(), d.other());
        } else if (decision instanceof KoboBotDecision.KingSwap d) {
            actions.kingSwap(id, seat, d.ownSlot());
        } else if (decision instanceof KoboBotDecision.SkipPower) {
            actions.skipPower(id, seat);
        } else if (decision instanceof KoboBotDecision.EndTurn) {
            actions.endTurn(id, seat);
        } else if (decision instanceof KoboBotDecision.AnnounceKobo) {
            actions.announceKobo(id, seat);
        } else if (decision instanceof KoboBotDecision.GiveTen d) {
            actions.giveTen(id, seat, d.target());
        } else if (decision instanceof KoboBotDecision.Match d) {
            actions.match(id, seat, d.target(), d.giveSlot(), d.expectedTopRank(), d.expectedRevision());
        } else {
            throw new IllegalStateException("No decision from the strategy");
        }
    }

    /** One fixed legal action per phase, so a failing or refused decision never stalls the table. */
    private void fallback(Kobo game, PlayerId seat) {
        GameId id = game.getId();
        try {
            switch (game.phase()) {
                case MEMORISING, ROUND_OVER -> actions.ready(id, seat);
                case DRAW -> actions.draw(id, seat);
                case DECISION -> actions.discardDrawn(id, seat);
                case POWER, KING_DECISION -> actions.skipPower(id, seat);
                case TURN_END -> actions.endTurn(id, seat);
                case GIFT -> actions.giveTen(id, seat, firstOtherSeat(game, seat));
                default -> { }
            }
        } catch (Exception e) {
            log.error("Bot {} fallback failed in game {}", seat, id, e);
        }
    }

    private static PlayerId firstOtherSeat(Kobo game, PlayerId seat) {
        return game.getPlayerIds().stream().filter(p -> !p.equals(seat)).findFirst().orElseThrow();
    }

    /**
     * The reaction delay of a match elapsed. Re-checked under the monitor: the discard top is the one the bot
     * reacted to and it still remembers the card at the same revision. Anything the game still refuses (another
     * seat was quicker, the slot changed) is a lost race: no penalty, no fallback.
     */
    private void tryMatch(Kobo game, PlayerId seat, KoboBotDecision.Match match, Top top) {
        synchronized (game) {
            BotSeat bot = liveSeat(game, seat);
            if (bot == null || bot.scheduledMatch != match) {
                return; // cancelled or replaced meanwhile
            }
            bot.scheduledMatch = null;
            bot.matchTask = null;
            boolean remembered = bot.memory.known(match.target())
                    .filter(known -> known.revision() == match.expectedRevision())
                    .isPresent();
            if (!top.equals(Top.of(game)) || !remembered) {
                return;
            }
            try {
                actions.match(game.getId(), seat, match.target(), match.giveSlot(), match.expectedTopRank(),
                        match.expectedRevision());
            } catch (KoboException e) {
                log.debug("Bot {} lost the race in game {}: {}", seat, game.getId(), e.getMessage());
            } catch (RuntimeException e) {
                log.warn("Bot {} failed to match in game {}", seat, game.getId(), e);
            }
        }
    }

    // ---- inspection for tests ----------------------------------------------------------------

    int trackedGameCount() {
        return tables.size();
    }

    Optional<KoboBotMemory> memoryOf(GameId id, PlayerId seat) {
        Table table = tables.get(id);
        return table == null ? Optional.empty() : Optional.ofNullable(table.bots.get(seat)).map(bot -> bot.memory);
    }
}
