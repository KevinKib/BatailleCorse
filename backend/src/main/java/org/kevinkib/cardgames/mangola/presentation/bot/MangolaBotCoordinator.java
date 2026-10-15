package org.kevinkib.cardgames.mangola.presentation.bot;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaException;
import org.kevinkib.cardgames.mangola.domain.MangolaPosition;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.Phase;
import org.kevinkib.cardgames.mangola.domain.Side;
import org.kevinkib.cardgames.mangola.domain.StuckStartException;
import org.kevinkib.cardgames.mangola.domain.bot.MangolaBot;
import org.kevinkib.cardgames.mangola.domain.bot.MangolaBotLevel;
import org.kevinkib.cardgames.mangola.presentation.MangolaGameActions;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.bot.BotSeats;
import org.kevinkib.cardgames.presentation.bot.ThinkingDelay;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.presentation.state.GameStateListener;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Decides when a Mangola computer seat must act and applies its decision through the same use cases as a human.
 *
 * <p>After every state broadcast it cancels the tasks of the previous state and schedules one task per bot seat
 * that has something to do, after a thinking delay: arranging and confirming its layout during the placement
 * phase, sowing a pit when it is the seat's turn. A task remembers the game version it reasoned about.
 *
 * <p><b>The search runs outside the monitor of the game</b>, which a human action, a forfeit and the other
 * tasks take: a task first takes a snapshot of what it needs (the position, the side, the level) under the
 * monitor and drops itself if the version moved on, then thinks without any lock, then takes the monitor again,
 * checks the version a second time and only then applies the decision. A human who moves while the bot thinks
 * therefore never waits for a search, and the bot's stale answer is thrown away.
 *
 * <p>Hidden placement: the strategy is given the rules, its own side and the layouts the game already refused it,
 * never the opponent's draft. A confirmation the game refuses because the revealed board would leave the
 * starting seat without a move ({@link StuckStartException}) is retried with another layout, up to
 * {@value #MAX_REFUSED_LAYOUTS} times; the memory of refusals is dropped as soon as the opponent takes its
 * confirmation back, since the reason may be gone.
 *
 * <p>A strategy that fails or proposes something illegal falls back to a fixed legal choice (the first legal
 * pit, two seeds per pit), so the table never stalls. Nothing is scheduled once the game is over, no human is
 * left, or it was evicted; a new game under the same id (Play again) gets a fresh table. The level is read from
 * the seat option {@code level}.
 */
public class MangolaBotCoordinator implements GameStateListener<Mangola>, GameEvictionListener {

    /** The seat option that carries the level of a computer seat. */
    public static final String LEVEL_KEY = "level";

    /** The layouts a bot may have refused as a stuck start before it stops trying until the opponent edits. */
    static final int MAX_REFUSED_LAYOUTS = 8;

    private static final Logger log = LoggerFactory.getLogger(MangolaBotCoordinator.class);

    private final BotSeats botSeats;
    private final BotSeatOptions seatOptions;
    private final MangolaGameActions actions;
    private final BotScheduler scheduler;
    private final ThinkingDelay thinkingDelay;
    private final Function<MangolaBotLevel, MangolaBot> bots;
    private final Map<GameId, Table> tables = new ConcurrentHashMap<>();

    public MangolaBotCoordinator(BotSeats botSeats,
                                 BotSeatOptions seatOptions,
                                 MangolaGameActions actions,
                                 BotScheduler scheduler,
                                 ThinkingDelay thinkingDelay,
                                 Function<MangolaBotLevel, MangolaBot> bots) {
        this.botSeats = botSeats;
        this.seatOptions = seatOptions;
        this.actions = actions;
        this.scheduler = scheduler;
        this.thinkingDelay = thinkingDelay;
        this.bots = bots;
    }

    /** The layouts the game refused to one bot seat, and how many times it tried. */
    private static final class Refusals {
        final Set<List<Integer>> layouts = new LinkedHashSet<>();
        int attempts;
    }

    /** What the coordinator keeps for one game with bots. */
    private static final class Table {
        final Mangola game;
        final List<BotScheduler.BotTask> pending = new ArrayList<>();
        final Map<PlayerId, Refusals> refusals = new HashMap<>();
        int scheduledVersion = -1;

        Table(Mangola game) {
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
    public void onBroadcast(Mangola game, String eventType, EventData eventData) {
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
                if (game.phase() == Phase.PLACEMENT && !game.isReady(seat)) {
                    table.track(scheduler.schedule(thinkingDelay.next(), () -> arrange(game, seat, version)));
                } else if (game.phase() == Phase.PLAY && game.currentSeat().equals(seat)) {
                    table.track(scheduler.schedule(thinkingDelay.next(), () -> play(game, seat, version)));
                }
            }
        }
    }

    @Override
    public void onEvicted(GameId id) {
        drop(id);
    }

    private Set<PlayerId> botsIn(Mangola game) {
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

    /** Whether the table still follows this game and the state is the one the task reasoned about. */
    private boolean unchanged(Mangola game, int version) {
        Table table = tables.get(game.getId());
        return table != null && table.game == game && !game.isFinished() && game.version() == version;
    }

    private MangolaBotLevel levelOf(Mangola game, PlayerId seat) {
        return MangolaBotLevel.fromOption(seatOptions.seatOptions(game.getId(), seat).get(LEVEL_KEY));
    }

    private static PlayerId otherSeat(Mangola game, PlayerId seat) {
        return game.getPlayerIds().stream().filter(s -> !s.equals(seat)).findFirst().orElseThrow();
    }

    // ---- placement ---------------------------------------------------------------------------

    private record LayoutRequest(MangolaRules rules, Side side, MangolaBotLevel level, List<List<Integer>> rejected) {
    }

    private void arrange(Mangola game, PlayerId seat, int version) {
        LayoutRequest request;
        synchronized (game) {
            if (!unchanged(game, version) || game.phase() != Phase.PLACEMENT || game.isReady(seat)) {
                return; // the state moved on since this bot started thinking
            }
            Table table = tables.get(game.getId());
            if (!game.isReady(otherSeat(game, seat))) {
                table.refusals.remove(seat); // the opponent is editing: what was refused may well be fine now
            }
            Refusals refusals = table.refusals.computeIfAbsent(seat, s -> new Refusals());
            if (refusals.attempts >= MAX_REFUSED_LAYOUTS) {
                return; // nothing it can arrange suits that opponent: wait for them to change
            }
            request = new LayoutRequest(game.rules(), game.sideOf(seat), levelOf(game, seat),
                    List.copyOf(refusals.layouts));
        }

        int[] layout = null;
        try {
            layout = bots.apply(request.level()).decideLayout(request.rules(), request.side(), request.rejected());
        } catch (Exception e) {
            log.warn("Bot {} failed to choose a layout in game {}; using a fixed one", seat, game.getId(), e);
        }

        synchronized (game) {
            if (!unchanged(game, version)) {
                return; // the opponent or the game moved while it was thinking: this layout is stale
            }
            Refusals refusals = tables.get(game.getId()).refusals.computeIfAbsent(seat, s -> new Refusals());
            place(game, seat, layout, refusals);
        }
    }

    private void place(Mangola game, PlayerId seat, int[] layout, Refusals refusals) {
        GameId id = game.getId();
        int[] chosen = layout;
        try {
            try {
                actions.arrange(id, seat, chosen);
            } catch (MangolaException | RuntimeException e) {
                chosen = fixedLayout();
                actions.arrange(id, seat, chosen);
            }
            actions.confirmLayout(id, seat);
        } catch (StuckStartException e) {
            refusals.layouts.add(boxed(chosen));
            refusals.attempts++; // the draft stays arranged; the next task (scheduled by the arrangement) retries
        } catch (MangolaException | RuntimeException e) {
            log.error("Bot {} could not arrange and confirm in game {}", seat, id, e);
        }
    }

    private static int[] fixedLayout() {
        int[] counts = new int[16];
        Arrays.fill(counts, 2);
        return counts;
    }

    private static List<Integer> boxed(int[] counts) {
        return Arrays.stream(counts).boxed().toList();
    }

    // ---- play --------------------------------------------------------------------------------

    private record MoveRequest(MangolaPosition position, Side side, MangolaBotLevel level) {
    }

    private void play(Mangola game, PlayerId seat, int version) {
        MoveRequest request;
        synchronized (game) {
            if (!unchanged(game, version) || game.phase() != Phase.PLAY || !game.currentSeat().equals(seat)) {
                return; // the state moved on since this bot started thinking
            }
            request = new MoveRequest(game.position(), game.sideOf(seat), levelOf(game, seat));
        }

        Integer pit = null;
        try {
            pit = bots.apply(request.level()).decideMove(request.position(), request.side());
        } catch (Exception e) {
            log.warn("Bot {} failed to choose a pit in game {}; falling back to its first legal pit", seat,
                    game.getId(), e);
        }

        synchronized (game) {
            if (!unchanged(game, version)) {
                return; // a human acted or the game ended while it was thinking: this answer is stale
            }
            sow(game, seat, pit);
        }
    }

    private void sow(Mangola game, PlayerId seat, Integer pit) {
        try {
            if (pit == null) {
                throw new IllegalStateException("no decision");
            }
            actions.sow(game.getId(), seat, pit);
        } catch (MangolaException | RuntimeException e) {
            log.warn("Bot {} proposed an illegal pit {} in game {}; falling back to its first legal pit", seat, pit,
                    game.getId());
            fallback(game, seat);
        }
    }

    private void fallback(Mangola game, PlayerId seat) {
        try {
            List<Integer> legal = game.legalPits(seat);
            if (!legal.isEmpty()) {
                actions.sow(game.getId(), seat, legal.get(0));
            }
        } catch (MangolaException | RuntimeException e) {
            log.error("Bot {} fallback failed in game {}", seat, game.getId(), e);
        }
    }

    // ---- inspection for tests ----------------------------------------------------------------

    int trackedGameCount() {
        return tables.size();
    }
}
