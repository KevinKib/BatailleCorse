package org.kevinkib.cardgames.sessionmanagement.presence.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.FakeGame;
import org.kevinkib.cardgames.game.FakeGameFactory;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.JoinResult;
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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

/**
 * The grace timer belongs to the game it was armed in and to a seat no connection holds any more: a timer left by
 * the previous game never forfeits a seat of the rematch, and closing one of two tabs on a seat arms nothing.
 */
class PresenceGraceTimerTest {

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
    }

    private SessionService sessions;
    private CapturingScheduler scheduler;
    private RecordingBroadcaster broadcaster;
    private PresenceService presence;
    private GameId id;
    private String hostToken;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-09T12:00:00Z"), ZoneOffset.UTC);
        sessions = new SessionService(new InMemorySessionRepository(clock), new GameFactories(List.of(new FakeGameFactory())));
        scheduler = new CapturingScheduler();
        broadcaster = new RecordingBroadcaster();
        presence = new PresenceService(sessions, new InMemoryConnectionRegistry(), scheduler, clock,
                new InMemoryForfeitLog(), new GameLifecycleBroadcasters(List.of(broadcaster)));
        RoomCreated room = sessions.createRoom(FakeGameFactory.GAME_TYPE, "Alice");
        id = new GameId(room.gameId());
        hostToken = room.hostToken();
        sessions.joinRoom(id, "Bob");
    }

    private String tokenOf(int seat) {
        return sessions.tokenForSeat(id, new PlayerId(seat));
    }

    private Game currentGame() {
        return sessions.findGame(id).orElseThrow();
    }

    @Test
    void givenAPlayerWhoLeftTheFinishedGame_whenTheRematchStartsWithinTheGrace_thenTheOldTimerForfeitsNobody() {
        FakeGame first = (FakeGame) sessions.startGame(id, hostToken);
        presence.onPresence("conn-alice", id, tokenOf(0));
        presence.onPresence("conn-bob", id, tokenOf(1));
        first.finish();

        // Alice closes her tab on the end screen; Bob plays again (he is now the host, seat 0) with a bot.
        presence.onDisconnect("conn-alice");
        JoinResult bob = sessions.playAgain(id, "Bob");
        sessions.addBot(id, bob.token());
        sessions.startGame(id, bob.token());

        scheduler.tasks.get(0).run(); // Alice's grace is over, in the rematch

        assertThat(currentGame().isFinished(), is(false));
        assertThat(broadcaster.events, is(empty()));
    }

    @Test
    void givenATimerFromThePreviousGame_whenTheNewHolderOfThatSeatConnects_thenNoReturnIsAnnounced() {
        FakeGame first = (FakeGame) sessions.startGame(id, hostToken);
        presence.onPresence("conn-alice", id, tokenOf(0));
        presence.onPresence("conn-bob", id, tokenOf(1));
        first.finish();
        presence.onDisconnect("conn-alice");
        JoinResult bob = sessions.playAgain(id, "Bob");
        sessions.addBot(id, bob.token());
        sessions.startGame(id, bob.token());

        presence.onPresence("conn-bob-2", id, bob.token()); // Bob holds seat 0 now

        assertThat(broadcaster.events, is(empty()));
    }

    @Test
    void givenTwoConnectionsOnOneSeat_whenOneOfThemDrops_thenNoTimerIsArmedAndNothingIsAnnounced() {
        sessions.startGame(id, hostToken);
        presence.onPresence("conn-alice-old", id, tokenOf(0));
        presence.onPresence("conn-alice-new", id, tokenOf(0)); // the new socket is up before the old one times out
        presence.onPresence("conn-bob", id, tokenOf(1));

        presence.onDisconnect("conn-alice-old");

        assertThat(scheduler.tasks, is(empty()));
        assertThat(broadcaster.events, is(empty()));
    }

    @Test
    void givenTwoConnectionsOnOneSeat_whenBothDrop_thenTheTimerIsArmedOnce() {
        sessions.startGame(id, hostToken);
        presence.onPresence("conn-alice-old", id, tokenOf(0));
        presence.onPresence("conn-alice-new", id, tokenOf(0));

        presence.onDisconnect("conn-alice-old");
        presence.onDisconnect("conn-alice-new");

        assertThat(scheduler.tasks.size(), is(1));
        assertThat(broadcaster.events, is(List.of("disconnected 0")));
    }
}
