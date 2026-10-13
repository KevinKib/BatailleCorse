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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

/**
 * Presence on a finished game: there is nothing to concede and nothing to announce, whatever a player does.
 * Pure session + presence, no real time: the scheduler is captured and fired by hand.
 */
class PresenceFinishedGameTest {

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
            presence.onPresence("conn-" + seat, id, sessions.tokenForSeat(id, new PlayerId(seat)));
        }
    }

    private Game currentGame() {
        return sessions.findGame(id).orElseThrow();
    }

    @Test
    void givenFinishedGame_whenAPlayerLeavesForGoodAfterTheGrace_thenNothingIsConcededOrBroadcast() {
        finishedGameWithThreeHumans();

        presence.onDisconnect("conn-2");
        scheduler.tasks.get(0).run(); // grace over

        assertThat(currentGame().isFinished(), is(true));
        assertThat(broadcaster.events, is(empty()));
    }

    @Test
    void givenFinishedGame_whenAPlayerResigns_thenNothingIsConcededOrBroadcast() {
        finishedGameWithThreeHumans();

        presence.forfeit(id, new PlayerId(2), ForfeitReason.RESIGNED);

        assertThat(currentGame().isFinished(), is(true));
        assertThat(broadcaster.events, is(empty()));
    }

    @Test
    void givenFinishedGame_whenADisconnectedPlayerReconnectsWithinGrace_thenTheTimerIsCancelledWithoutNoise() {
        finishedGameWithThreeHumans();
        presence.onDisconnect("conn-2");

        presence.onPresence("conn-2b", id, sessions.tokenForSeat(id, new PlayerId(2)));

        assertThat(scheduler.cancelled.get(0), is(true));
        assertThat(broadcaster.events, is(empty())); // no "reconnected" noise on a finished game
    }
}
