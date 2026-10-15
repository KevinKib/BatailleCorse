package org.kevinkib.cardgames.mangola.presentation.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaFactory;
import org.kevinkib.cardgames.mangola.domain.MangolaPosition;
import org.kevinkib.cardgames.mangola.domain.MangolaResult;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.Phase;
import org.kevinkib.cardgames.mangola.domain.Side;
import org.kevinkib.cardgames.mangola.domain.StuckStartException;
import org.kevinkib.cardgames.mangola.domain.bot.ExpertMangolaBot;
import org.kevinkib.cardgames.mangola.domain.bot.MangolaBot;
import org.kevinkib.cardgames.mangola.domain.bot.MangolaBotLevel;
import org.kevinkib.cardgames.mangola.presentation.MangolaGameActions;
import org.kevinkib.cardgames.mangola.presentation.dto.MangolaDto;
import org.kevinkib.cardgames.presentation.bot.FixedThinkingDelay;
import org.kevinkib.cardgames.presentation.bot.ManualBotScheduler;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.presentation.support.SessionTable;
import org.kevinkib.cardgames.sessionmanagement.core.application.JoinResult;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Whole games through the real lobby, session core, broadcaster, coordinator and use cases, with a manual
 * scheduler and seeded randomness: a "human" who arranges and plays at random against a computer seat of every
 * level, whoever starts. The bot seat must never be messaged, never forfeit, never stall the
 * table and never play an illegal pit (a fallback would hide it, so the strategies are wrapped and checked).
 */
class MangolaBotGameIT {

    private static final int STEP_BOUND = 5_000;
    private static final PlayerId HUMAN = new PlayerId(0);
    private static final PlayerId BOT = new PlayerId(1);

    /** Wraps a strategy to record every decision the game would have refused. */
    private static final class CheckedBot implements MangolaBot {
        private final MangolaBot inner;
        private final List<String> violations;

        CheckedBot(MangolaBot inner, List<String> violations) {
            this.inner = inner;
            this.violations = violations;
        }

        @Override
        public int[] decideLayout(MangolaRules rules, Side side, Collection<List<Integer>> rejected) {
            int[] layout = inner.decideLayout(rules, side, rejected);
            if (layout.length != 16 || java.util.Arrays.stream(layout).sum() != 32
                    || rejected.contains(java.util.Arrays.stream(layout).boxed().toList())) {
                violations.add("illegal or repeated layout " + java.util.Arrays.toString(layout));
            }
            return layout;
        }

        @Override
        public int decideMove(MangolaPosition position, Side side) {
            int pit = inner.decideMove(position, side);
            PlayerId mover = new PlayerId(position.toMove());
            PlayerId seatA = side == Side.A ? mover : new PlayerId(1 - position.toMove());
            Mangola probe = Mangola.fromLayout(GameId.generate(), position.rules(), seatA,
                    org.kevinkib.cardgames.mangola.domain.MangolaBoard.of(
                            position.board().stream().mapToInt(Integer::intValue).toArray()),
                    mover);
            if (!probe.legalPits(mover).contains(pit)) {
                violations.add("illegal pit " + pit + " in " + position);
            }
            return pit;
        }
    }

    private static final class Table {
        final SessionService sessions;
        final ManualBotScheduler scheduler = new ManualBotScheduler();
        final MangolaGameActions actions;
        final SeatStateBroadcaster<Mangola, MangolaDto> broadcaster;
        final MangolaBotCoordinator coordinator;
        final RecordingMessaging messaging;
        final List<String> violations = new ArrayList<>();
        final Random human;
        final GameId id;
        Mangola game;

        Table(String firstPlayer, MangolaBotLevel level, long seed) throws Exception {
            sessions = new SessionTable(new MangolaFactory(new Random(seed * 13))).sessions;
            messaging = new RecordingMessaging() {
                @Override
                public void sendToSeat(GameId gameId, PlayerId seat, Object payload) {
                    if (sessions.botSeats(gameId).contains(seat)) {
                        throw new AssertionError("A bot seat was messaged: " + seat);
                    }
                    super.sendToSeat(gameId, seat, payload);
                }
            };
            broadcaster = new SeatStateBroadcaster<>(messaging, sessions::botSeats, sessions::seatNames,
                    (g, viewer, names, bots) -> MangolaDto.forViewer(g, viewer, names));
            actions = new MangolaGameActions(sessions, broadcaster);
            Random botRandom = new Random(seed);
            human = new Random(seed * 31 + 7);
            ExpertMangolaBot.Budget budget = new ExpertMangolaBot.Budget(0, 300, 0);
            coordinator = new MangolaBotCoordinator(sessions::botSeats, sessions::seatOptions, actions, scheduler,
                    new FixedThinkingDelay(Duration.ZERO),
                    l -> new CheckedBot(l.bot(botRandom, budget), violations));
            broadcaster.addListener(coordinator);

            RoomCreated room = sessions.createRoom(MangolaFactory.GAME_TYPE, "Alice", GameOptions.of(Map.of(
                    MangolaFactory.FIRST_PLAYER_KEY, firstPlayer)));
            id = new GameId(room.gameId());
            sessions.addBot(id, room.hostToken());
            sessions.setSeatOption(id, room.hostToken(), 1, "level", level.key());
            game = (Mangola) sessions.startGame(id, room.hostToken());
            start();
        }

        void start() {
            broadcaster.broadcast(game, "START", new EmptyEventData(), "Game started.");
        }
    }

    private static int[] randomLayout(Random random) {
        int[] counts = new int[16];
        for (int seed = 0; seed < 32; seed++) {
            counts[random.nextInt(16)]++;
        }
        return counts;
    }

    /** The human arranges and confirms at random while placing, then plays a random legal pit on its turn. */
    private static boolean humanActs(Table table) throws Exception {
        Mangola game = table.game;
        if (game.phase() == Phase.PLACEMENT && !game.isReady(HUMAN)) {
            table.actions.arrange(game.getId(), HUMAN, randomLayout(table.human));
            try {
                table.actions.confirmLayout(game.getId(), HUMAN);
            } catch (StuckStartException refused) {
                // the next call arranges another layout
            }
            return true;
        }
        if (game.phase() == Phase.PLAY && game.currentSeat().equals(HUMAN)) {
            List<Integer> legal = game.legalPits(HUMAN);
            table.actions.sow(game.getId(), HUMAN, legal.get(table.human.nextInt(legal.size())));
            return true;
        }
        return false;
    }

    private static void play(Table table) throws Exception {
        int steps = 0;
        while (!table.game.isFinished() && steps++ < STEP_BOUND) {
            if (!table.scheduler.pending().isEmpty()) {
                table.scheduler.runNext();
            } else if (!humanActs(table)) {
                throw new AssertionError("The table stalled at step " + steps + " with nobody able to act: phase "
                        + table.game.phase() + ", ready " + table.game.isReady(HUMAN) + "/" + table.game.isReady(BOT));
            }
        }
    }

    private static void assertCleanFinish(Table table, String context) {
        assertThat(context + " finished", table.game.isFinished(), is(true));
        assertThat(context + " violations", table.violations, is(empty()));
        assertThat(context, table.game.result().reason(), is(not(MangolaResult.Reason.FORFEIT)));
        assertThat(context, table.game.result().reason(), is(not(MangolaResult.Reason.ENDLESS_SOWING)));
    }

    @Test
    void everyLevelFinishesWithoutStallingForfeitingOrPlayingAnIllegalPit() throws Exception {
        for (String first : List.of("host", "guest")) {
            for (MangolaBotLevel level : MangolaBotLevel.values()) {
                for (long seed = 1; seed <= 4; seed++) {
                    Table table = new Table(first, level, seed);

                    play(table);

                    assertCleanFinish(table, first + " " + level + " seed " + seed);
                }
            }
        }
    }

    @Test
    void theBotSeatNeverReceivesAMessage() throws Exception {
        // The messaging double throws when a bot seat is addressed: playing whole games proves it never is.
        Table table = new Table("random", MangolaBotLevel.EXPERT, 5);
        play(table);
        assertThat(table.messaging.seats.contains(BOT), is(false));
        assertThat(table.messaging.seats.contains(HUMAN), is(true));
    }

    @Test
    void thePlacementOfTheBotNeverDependsOnTheDraftOfTheHuman() throws Exception {
        // Same seeds, two different human layouts: the bot arranges the same layout, since it never sees the draft.
        List<List<Integer>> layouts = new ArrayList<>();
        for (int humanLayout = 0; humanLayout < 2; humanLayout++) {
            Table table = new Table("host", MangolaBotLevel.GREEDY, 11);
            int[] draft = new int[16];
            java.util.Arrays.fill(draft, 2);
            if (humanLayout == 1) {
                draft = new int[]{32, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
            }
            table.actions.arrange(table.game.getId(), HUMAN, draft); // the human edits first, a broadcast follows
            table.scheduler.runNext();
            layouts.add(table.game.layoutOf(BOT).orElseThrow());
        }
        assertThat(layouts.get(0), is(layouts.get(1)));
    }

    @Test
    void playAgainKeepsTheBotAndTheNextGameGetsItsOwnTable() throws Exception {
        Table table = new Table("host", MangolaBotLevel.GREEDY, 3);
        play(table);
        assertCleanFinish(table, "first game");

        JoinResult again = table.sessions.playAgain(table.id, "Alice");
        table.game = (Mangola) table.sessions.startGame(table.id, again.token());
        table.start();
        assertThat(table.coordinator.trackedGameCount(), is(1));
        play(table);

        assertCleanFinish(table, "second game");
        assertThat(table.sessions.botSeats(table.id), is(java.util.Set.of(BOT)));
        assertThat(table.sessions.seatOptions(table.id, BOT).get("level"), is("greedy"));
    }
}
