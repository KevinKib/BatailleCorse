package org.kevinkib.cardgames.president.presentation.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.PresidentFactory;
import org.kevinkib.cardgames.president.domain.RandomDeckSource;
import org.kevinkib.cardgames.president.domain.ScriptedDeckSource;
import org.kevinkib.cardgames.president.domain.bot.HeuristicPresidentBot;
import org.kevinkib.cardgames.president.domain.bot.PresidentBotDecision;
import org.kevinkib.cardgames.president.domain.bot.PresidentBotLevel;
import org.kevinkib.cardgames.president.domain.bot.PresidentBotMemory;
import org.kevinkib.cardgames.president.domain.bot.PresidentBotStrategy;
import org.kevinkib.cardgames.president.domain.bot.PresidentBotView;
import org.kevinkib.cardgames.president.domain.exchange.PendingReturn;
import org.kevinkib.cardgames.president.domain.play.Play;
import org.kevinkib.cardgames.president.domain.round.Action;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.president.presentation.PresidentGameActions;
import org.kevinkib.cardgames.president.presentation.dto.PresidentDto;
import org.kevinkib.cardgames.presentation.bot.FixedThinkingDelay;
import org.kevinkib.cardgames.presentation.bot.ManualBotScheduler;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.presentation.support.SessionTable;
import org.kevinkib.cardgames.sessionmanagement.core.application.JoinResult;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cards.domain.Card;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/**
 * A round through the real lobby, session core, broadcaster, coordinator and use cases, with a manual
 * scheduler and a fixed deal: two humans and one hard computer seat. The bot seat is never messaged, never
 * refused a command (a fallback would hide it, so the strategy is wrapped and checked), and keeps its seat and
 * its level when the room is replayed.
 */
class PresidentBotGameIT {

    private static final PlayerId HUMAN = new PlayerId(0);
    private static final PlayerId BOT = new PlayerId(1);
    private static final PlayerId OTHER_HUMAN = new PlayerId(2);
    private static final int STEP_BOUND = 2_000;

    /** Wraps a strategy to record every decision the game would have refused. */
    private static final class CheckedBot implements PresidentBotStrategy {
        private final PresidentBotStrategy inner;
        private final List<String> violations;
        int decisions;

        CheckedBot(PresidentBotStrategy inner, List<String> violations) {
            this.inner = inner;
            this.violations = violations;
        }

        @Override
        public PresidentBotDecision decide(PresidentBotView view, PresidentBotMemory memory) {
            PresidentBotDecision decision = inner.decide(view, memory);
            decisions++;
            if (decision instanceof PresidentBotDecision.Play play) {
                boolean legal = view.legalPlays().stream().anyMatch(p -> new HashSet<>(p.cards())
                        .equals(new HashSet<>(play.cards())));
                if (!view.can(Action.PLAY) || !legal) {
                    violations.add("illegal play " + play.cards() + " in " + view.phase());
                }
            } else if (decision instanceof PresidentBotDecision.Pass) {
                if (!view.can(Action.PASS)) {
                    violations.add("illegal pass in " + view.phase());
                }
            } else if (decision instanceof PresidentBotDecision.GiveBack giveBack) {
                int owed = view.pendingReturns().stream().filter(p -> p.high().equals(view.seat()))
                        .mapToInt(PendingReturn::count).sum();
                if (!view.can(Action.GIVE_BACK) || giveBack.cards().size() != owed
                        || !view.hand().containsAll(giveBack.cards())) {
                    violations.add("illegal give back " + giveBack.cards());
                }
            } else if (!view.can(Action.READY)) {
                violations.add("ready out of the barrier in " + view.phase());
            }
            return decision;
        }
    }

    private static final class Table {
        final SessionService sessions;
        final ManualBotScheduler scheduler = new ManualBotScheduler();
        final PresidentGameActions actions;
        final SeatStateBroadcaster<President, PresidentDto> broadcaster;
        final PresidentBotCoordinator coordinator;
        final RecordingMessaging messaging;
        final List<String> violations = new ArrayList<>();
        final CheckedBot checked;
        final ScriptedDeckSource decks = new ScriptedDeckSource(new RandomDeckSource());
        final GameId id;
        final String hostToken;
        President game;

        Table(PresidentBotLevel level, long seed) {
            sessions = new SessionTable(new PresidentFactory(decks)).sessions;
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
                    (g, viewer, names, bots) -> PresidentDto.forViewer(g, viewer, names, bots));
            actions = new PresidentGameActions(sessions, broadcaster);
            checked = new CheckedBot(new HeuristicPresidentBot(level.tuning(), new Random(seed)), violations);
            coordinator = new PresidentBotCoordinator(sessions::botSeats, sessions::seatOptions, actions, scheduler,
                    new FixedThinkingDelay(Duration.ZERO), l -> checked);
            broadcaster.addListener(coordinator);

            RoomCreated room = sessions.createRoom(PresidentFactory.GAME_TYPE, "Alice");
            id = new GameId(room.gameId());
            hostToken = room.hostToken();
            sessions.addBot(id, hostToken);
            sessions.setSeatOption(id, hostToken, 1, "level", level.key());
            sessions.joinRoom(id, "Carl");
        }

        /** The bot holds the king of hearts, so it leads; the rest of the deck is random. */
        void fixTheDealAndStart() {
            decks.fix(id, 1, cards("3C KH 4D 3D 5C 5D"));
            game = (President) sessions.startGame(id, hostToken);
            broadcaster.broadcast(game, "START", new EmptyEventData(), "Game started.");
        }
    }

    /** A human plays its lowest legal set, passes when nothing beats, gives back its lowest cards, readies. */
    private static boolean humanActs(Table table, PlayerId human) throws Exception {
        President game = table.game;
        GameId id = game.getId();
        switch (game.phase()) {
            case PLAYING -> {
                if (!game.currentSeat().equals(human)) {
                    return false;
                }
                List<Play> options = game.legalPlays(human);
                if (game.trick().plays().isEmpty() || !options.isEmpty()) {
                    table.actions.play(id, human, options.get(0).cards());
                } else {
                    table.actions.pass(id, human);
                }
                return true;
            }
            case EXCHANGE -> {
                for (PendingReturn pending : game.pendingReturns()) {
                    if (pending.high().equals(human)) {
                        table.actions.giveBack(id, human, game.hand(human).subList(0, pending.count()));
                        return true;
                    }
                }
                return false;
            }
            case ROUND_OVER -> {
                if (game.readySeats().contains(human)) {
                    return false;
                }
                table.actions.ready(id, human);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static void playUntil(Table table, java.util.function.BooleanSupplier done) throws Exception {
        int steps = 0;
        while (!done.getAsBoolean() && steps++ < STEP_BOUND) {
            if (!table.scheduler.pending().isEmpty()) {
                table.scheduler.runNext();
            } else if (!humanActs(table, HUMAN) && !humanActs(table, OTHER_HUMAN)) {
                throw new AssertionError("The table stalled at step " + steps + ": phase " + table.game.phase()
                        + ", current seat " + table.game.currentSeat());
            }
        }
        assertThat("the awaited state was reached", done.getAsBoolean(), is(true));
    }

    @Test
    void theBotHoldingTheKingOfHeartsLeadsTheFirstTrickOfTheFixedDeal() {
        Table table = new Table(PresidentBotLevel.HARD, 3);

        table.fixTheDealAndStart();

        assertThat(table.game.currentSeat(), is(BOT));
        assertThat(table.scheduler.pending().size(), is(1));
        table.scheduler.runNext();
        assertThat(table.game.trick().plays().size(), is(1));
        assertThat(table.game.trick().plays().get(0).seat(), is(BOT));
        assertThat(table.violations, is(empty()));
    }

    @Test
    void aWholeRoundIsPlayedWithoutAnIllegalDecisionAMessageToTheBotOrAStall() throws Exception {
        Table table = new Table(PresidentBotLevel.HARD, 5);
        table.fixTheDealAndStart();

        playUntil(table, () -> table.game.phase() == Phase.ROUND_OVER);

        assertThat(table.violations, is(empty()));
        assertThat(table.checked.decisions, greaterThan(0));
        assertThat(table.game.finishingOrder().size(), is(3));
        Set<Card> everything = new HashSet<>(table.game.discard());
        assertThat(everything.size(), is(table.game.discard().size()));
        assertThat(table.messaging.seats.contains(BOT), is(false));
        assertThat(table.messaging.seats.contains(HUMAN), is(true));
        assertThat(table.messaging.seats.contains(OTHER_HUMAN), is(true));
    }

    @Test
    void theBotReadiesAtTheBarrierAndTheNextRoundStartsOnceTheHumansAreReady() throws Exception {
        Table table = new Table(PresidentBotLevel.HARD, 7);
        table.fixTheDealAndStart();
        playUntil(table, () -> table.game.phase() == Phase.ROUND_OVER);

        playUntil(table, () -> table.game.round() == 2);

        assertThat(table.violations, is(empty()));
        assertThat(table.game.phase() == Phase.EXCHANGE || table.game.phase() == Phase.PLAYING, is(true));
    }

    @Test
    void playAgainKeepsTheBotItsLevelAndGivesTheNextGameItsOwnTable() throws Exception {
        Table table = new Table(PresidentBotLevel.HARD, 9);
        table.fixTheDealAndStart();
        playUntil(table, () -> table.game.phase() == Phase.ROUND_OVER);

        JoinResult again = table.sessions.playAgain(table.id, "Alice");
        table.sessions.joinRoom(table.id, "Carl");
        table.game = (President) table.sessions.startGame(table.id, again.token());
        table.broadcaster.broadcast(table.game, "START", new EmptyEventData(), "Game started.");

        assertThat(table.coordinator.trackedGameCount(), is(1));
        assertThat(table.sessions.botSeats(table.id), is(Set.of(BOT)));
        assertThat(table.sessions.seatOptions(table.id, BOT).get("level"), is("hard"));
        playUntil(table, () -> table.game.phase() == Phase.ROUND_OVER);
        assertThat(table.violations, is(empty()));
        assertThat(table.game.finishingOrder().size(), is(3));
    }
}
