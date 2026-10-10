package org.kevinkib.cardgames.sessionmanagement.presence.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.FakeGame;
import org.kevinkib.cardgames.game.FakeGameFactory;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryConnectionRegistry;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitScheduler;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ScheduledForfeit;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * A rematch needs every expected player to ask for it. When one of them leaves for good after the
 * others have already asked, the departure itself must re-evaluate the rematch: nobody clicks again.
 * Pure session + presence, no real time: the scheduler is captured and fired by hand.
 */
class PresenceRematchTest {

    private static final class CapturingScheduler implements ForfeitScheduler {
        final List<Runnable> tasks = new ArrayList<>();
        final List<Boolean> cancelled = new ArrayList<>();

        @Override
        public ScheduledForfeit schedule(Instant deadline, Runnable task) {
            int index = tasks.size();
            tasks.add(task);
            cancelled.add(false);
            return () -> cancelled.set(index, true);
        }
    }

    private static final class RecordingBroadcaster implements GameLifecycleBroadcaster {
        final List<String> events = new ArrayList<>();

        @Override public boolean supports(Game game) { return game instanceof FakeGame; }
        @Override public void disconnected(Game game, PlayerId player, long deadlineEpochMs) { events.add("disconnected " + player.id()); }
        @Override public void reconnected(Game game, PlayerId player) { events.add("reconnected " + player.id()); }
        @Override public void forfeited(Game game, PlayerId player, ForfeitReason reason) { events.add("forfeited " + player.id()); }
        @Override public void rematchStarted(Game fresh, PlayerId triggeredBy) { events.add("rematch " + triggeredBy.id()); }
    }

    private SessionService sessions;
    private CapturingScheduler scheduler;
    private RecordingBroadcaster broadcaster;
    private PresenceService presence;
    private GameId id;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-09T12:00:00Z"), ZoneOffset.UTC);
        sessions = new SessionService(new InMemorySessionRepository(clock), new GameFactories(List.of(new FakeGameFactory())));
        scheduler = new CapturingScheduler();
        broadcaster = new RecordingBroadcaster();
        presence = new PresenceService(sessions, new InMemoryConnectionRegistry(), scheduler, clock,
                new InMemoryForfeitLog(), new GameLifecycleBroadcasters(List.of(broadcaster)));
    }

    /** Three humans in a started room, game already over. */
    private void finishedGameWithThreeHumans() {
        RoomCreated room = sessions.createRoom("fake", "Alice");
        id = new GameId(room.gameId());
        sessions.joinRoom(id, "Bob");
        sessions.joinRoom(id, "Carol");
        FakeGame game = (FakeGame) sessions.startGame(id, room.hostToken());
        game.finish();
        for (int seat = 0; seat < 3; seat++) {
            presence.onPresence("conn-" + seat, id, new PlayerId(seat));
        }
    }

    private Game currentGame() {
        return sessions.findGame(id).orElseThrow();
    }

    @Test
    void givenOthersAskedForRematch_whenLastExpectedPlayerLeavesForGood_thenRematchStartsForTheRemaining() {
        finishedGameWithThreeHumans();
        sessions.requestRematch(id, new PlayerId(0));
        sessions.requestRematch(id, new PlayerId(1));

        presence.onDisconnect("conn-2");
        scheduler.tasks.get(0).run(); // grace over: Carol is gone for good

        assertThat(currentGame().isFinished(), is(false));
        assertThat(currentGame().getPlayerIds(), hasSize(2));
        assertThat(broadcaster.events, contains("rematch 2"));
    }

    @Test
    void givenOthersAskedForRematch_whenExpectedPlayerOnlyDisconnected_thenRematchWaitsForTheGraceDelay() {
        finishedGameWithThreeHumans();
        sessions.requestRematch(id, new PlayerId(0));
        sessions.requestRematch(id, new PlayerId(1));

        presence.onDisconnect("conn-2"); // grace period still running

        assertThat(currentGame().isFinished(), is(true));
        assertThat(broadcaster.events, is(empty()));
    }

    @Test
    void givenDisconnectedExpectedPlayer_whenReconnectsWithinGrace_thenTimerCancelledAndRematchStillNeedsHisRequest() {
        finishedGameWithThreeHumans();
        sessions.requestRematch(id, new PlayerId(0));
        sessions.requestRematch(id, new PlayerId(1));
        presence.onDisconnect("conn-2");

        presence.onPresence("conn-2b", id, new PlayerId(2));

        assertThat(scheduler.cancelled.get(0), is(true));
        assertThat(broadcaster.events, is(empty())); // no "reconnected" noise on a finished game
        assertThat(sessions.requestRematch(id, new PlayerId(2)), is(true));
    }

    @Test
    void givenExpectedPlayerLeavesBeforeOthersAsk_whenTheyAskAfterwards_thenRematchIsUnanimousAtTheLastRequest() {
        finishedGameWithThreeHumans();
        presence.onDisconnect("conn-2");
        scheduler.tasks.get(0).run();

        assertThat(sessions.requestRematch(id, new PlayerId(0)), is(false));
        assertThat(sessions.requestRematch(id, new PlayerId(1)), is(true));
    }

    @Test
    void givenExplicitLeaveOnFinishedGame_whenOthersAlreadyAsked_thenRematchStartsWithoutWaiting() {
        finishedGameWithThreeHumans();
        sessions.requestRematch(id, new PlayerId(0));
        sessions.requestRematch(id, new PlayerId(1));

        presence.forfeit(id, new PlayerId(2), ForfeitReason.RESIGNED);

        assertThat(currentGame().isFinished(), is(false));
        assertThat(broadcaster.events, contains("rematch 2"));
    }

    @Test
    void givenBotSeat_whenHumansAsked_thenBotNeverBlocksTheRematch() {
        RoomCreated room = sessions.createRoom("fake", "Alice");
        id = new GameId(room.gameId());
        sessions.joinRoom(id, "Bob");
        sessions.addBot(id, room.hostToken());
        ((FakeGame) sessions.startGame(id, room.hostToken())).finish();

        assertThat(sessions.requestRematch(id, new PlayerId(0)), is(false));
        assertThat(sessions.requestRematch(id, new PlayerId(1)), is(true));
    }

    @Test
    void givenTwoPlayersOneAskedAndTheOtherLeaves_thenNoRematchCanStart() {
        RoomCreated room = sessions.createRoom("fake", "Alice");
        id = new GameId(room.gameId());
        sessions.joinRoom(id, "Bob");
        ((FakeGame) sessions.startGame(id, room.hostToken())).finish();
        presence.onPresence("conn-1", id, new PlayerId(1));
        sessions.requestRematch(id, new PlayerId(0));

        presence.onDisconnect("conn-1");
        scheduler.tasks.get(0).run();

        assertThat(currentGame().isFinished(), is(true)); // a lone player cannot play a rematch
        assertThat(broadcaster.events, is(empty()));
    }
}
