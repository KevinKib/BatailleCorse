package org.kevinkib.cardgames.sessionmanagement.presence.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.FakeGameFactory;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameDirectory;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryConnectionRegistry;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitScheduler;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ScheduledForfeit;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

/** Presence follows the seat token: the seat number is resolved when the connection drops. */
class PresenceByTokenTest {

    /** A directory whose token resolution can be changed after the binding, like a seat shift. */
    private static final class ShiftableDirectory implements GameDirectory {
        final GameDirectory real;
        final Map<String, PlayerId> overrides = new HashMap<>();
        final List<String> unresolved = new ArrayList<>();

        ShiftableDirectory(GameDirectory real) { this.real = real; }

        @Override public Optional<Game> findGame(GameId id) { return real.findGame(id); }
        @Override public void touch(GameId id) { real.touch(id); }

        @Override
        public Optional<PlayerId> findPlayerIdByToken(GameId id, String token) {
            if (unresolved.contains(token)) return Optional.empty();
            if (overrides.containsKey(token)) return Optional.of(overrides.get(token));
            return real.findPlayerIdByToken(id, token);
        }
    }

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

        @Override public boolean supports(Game game) { return true; }
        @Override public void disconnected(Game game, PlayerId player, long deadlineEpochMs) { events.add("disconnected " + player.id()); }
        @Override public void reconnected(Game game, PlayerId player) { events.add("reconnected " + player.id()); }
        @Override public void forfeited(Game game, PlayerId player, ForfeitReason reason) { events.add("forfeited " + player.id()); }
    }

    private SessionService sessions;
    private ShiftableDirectory directory;
    private CapturingScheduler scheduler;
    private RecordingBroadcaster broadcaster;
    private PresenceService presence;
    private GameId id;
    private String hostToken;
    private String guestToken;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-09T12:00:00Z"), ZoneOffset.UTC);
        sessions = new SessionService(new InMemorySessionRepository(clock), new GameFactories(List.of(new FakeGameFactory())));
        directory = new ShiftableDirectory(sessions);
        scheduler = new CapturingScheduler();
        broadcaster = new RecordingBroadcaster();
        presence = new PresenceService(directory, new InMemoryConnectionRegistry(), scheduler, clock,
                new InMemoryForfeitLog(), new GameLifecycleBroadcasters(List.of(broadcaster)));

        RoomCreated room = sessions.createRoom("fake", "Alice");
        id = new GameId(room.gameId());
        hostToken = room.hostToken();
        guestToken = sessions.joinRoom(id, "Bob").token();
        sessions.startGame(id, hostToken);
    }

    @Test
    void givenTokenNoLongerResolving_whenDisconnect_thenNoTimerAndNoBroadcast() {
        presence.onPresence("conn-leaver", id, guestToken);
        directory.unresolved.add(guestToken); // the leaver's token was released

        presence.onDisconnect("conn-leaver");

        assertThat(scheduler.tasks, is(empty()));
        assertThat(broadcaster.events, is(empty()));
    }

    @Test
    void givenSeatNumberChangedAfterBinding_whenDisconnect_thenTheNewSeatOfTheSameTokenIsForfeited() {
        presence.onPresence("conn-guest", id, guestToken);
        directory.overrides.put(guestToken, new PlayerId(0)); // shifted from seat 1 to seat 0

        presence.onDisconnect("conn-guest");
        scheduler.tasks.get(0).run();

        assertThat(broadcaster.events, contains("disconnected 0", "forfeited 0"));
    }

    @Test
    void givenSeatShifted_whenSameTokenReconnects_thenTheTimerOfTheResolvedSeatIsCancelled() {
        presence.onPresence("conn-guest", id, guestToken);
        directory.overrides.put(guestToken, new PlayerId(0));
        presence.onDisconnect("conn-guest");

        presence.onPresence("conn-guest-b", id, guestToken);

        assertThat(scheduler.cancelled.get(0), is(true));
        assertThat(broadcaster.events, contains("disconnected 0", "reconnected 0"));
    }

    @Test
    void givenUnknownToken_whenPresence_thenNothingIsBound() {
        presence.onPresence("conn-x", id, "not-a-token");

        presence.onDisconnect("conn-x");

        assertThat(scheduler.tasks, is(empty()));
    }
}
