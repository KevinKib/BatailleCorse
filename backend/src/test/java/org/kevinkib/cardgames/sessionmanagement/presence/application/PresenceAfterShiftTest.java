package org.kevinkib.cardgames.sessionmanagement.presence.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.hamcrest.Matchers.is;

/**
 * Presence with the real session core: a lobby seat shifted by a departure is followed by its token, and the
 * leaver's connection, still open after the start, forfeits nobody.
 */
class PresenceAfterShiftTest {

    private static final class CapturingScheduler implements ForfeitScheduler {
        final List<Runnable> tasks = new ArrayList<>();

        @Override
        public ScheduledForfeit schedule(Instant deadline, Runnable task) {
            tasks.add(task);
            return () -> { };
        }
    }

    private static final class RecordingBroadcaster implements GameLifecycleBroadcaster {
        final List<String> events = new ArrayList<>();

        @Override public boolean supports(Game game) { return true; }
        @Override public void disconnected(Game game, PlayerId player, long deadlineEpochMs) { events.add("disconnected " + player.id()); }
        @Override public void reconnected(Game game, PlayerId player) { events.add("reconnected " + player.id()); }
        @Override public void forfeited(Game game, PlayerId player, ForfeitReason reason) { events.add("forfeited " + player.id()); }
    }

    private SessionService sessions;
    private CapturingScheduler scheduler;
    private RecordingBroadcaster broadcaster;
    private PresenceService presence;
    private GameId id;
    private String aliceToken;
    private String bobToken;
    private String carolToken;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-09T12:00:00Z"), ZoneOffset.UTC);
        sessions = new SessionService(new InMemorySessionRepository(clock), new GameFactories(List.of(new FakeGameFactory())));
        scheduler = new CapturingScheduler();
        broadcaster = new RecordingBroadcaster();
        presence = new PresenceService(sessions, new InMemoryConnectionRegistry(), scheduler, clock,
                new InMemoryForfeitLog(), new GameLifecycleBroadcasters(List.of(broadcaster)));

        RoomCreated room = sessions.createRoom("fake", "Alice");
        id = new GameId(room.gameId());
        aliceToken = room.hostToken();
        bobToken = sessions.joinRoom(id, "Bob").token();
        carolToken = sessions.joinRoom(id, "Carol").token();
        presence.onPresence("conn-alice", id, aliceToken);
        presence.onPresence("conn-bob", id, bobToken);
        presence.onPresence("conn-carol", id, carolToken);
    }

    @Test
    void givenAHostWhoLeft_whenTheShiftedGuestsStartAndDisconnect_thenTheirNewSeatIsForfeitedAndNobodyElse() {
        sessions.leaveLobby(id, aliceToken);
        sessions.startGame(id, bobToken); // Bob is the host at seat 0, Carol sits at seat 1

        presence.onDisconnect("conn-carol");
        scheduler.tasks.get(0).run();

        assertThat(broadcaster.events, contains("disconnected 1", "forfeited 1"));
    }

    @Test
    void givenALeaverWhoStaysConnected_whenTheGameStartsAndTheirTabCloses_thenNobodyIsForfeited() {
        sessions.leaveLobby(id, aliceToken);
        sessions.startGame(id, bobToken);

        presence.onDisconnect("conn-alice");

        assertThat(scheduler.tasks, is(empty()));
        assertThat(broadcaster.events, is(empty()));
    }

    @Test
    void givenAGuestWhoLeft_whenTheGameStartsAndTheirTabCloses_thenTheGuestNowAtTheirSeatIsNotForfeited() {
        sessions.leaveLobby(id, bobToken); // Carol shifts to seat 1, the seat Bob held
        sessions.joinRoom(id, "Dave");
        sessions.startGame(id, aliceToken);

        presence.onDisconnect("conn-bob");

        assertThat(scheduler.tasks, is(empty()));
        assertThat(broadcaster.events, is(empty()));
    }
}
