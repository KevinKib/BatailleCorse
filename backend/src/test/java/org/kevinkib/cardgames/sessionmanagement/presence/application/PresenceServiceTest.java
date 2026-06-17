package org.kevinkib.cardgames.sessionmanagement.presence.application;
import org.kevinkib.cardgames.bataillecorse.presentation.BatailleCorseLifecycleBroadcaster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.domain.GameMode;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryConnectionRegistry;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ConnectionRegistry;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitScheduler;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ScheduledForfeit;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.PlayerDto;
import org.kevinkib.cardgames.presentation.GameMessagingService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

class PresenceServiceTest {

    /** Captures scheduled forfeits (last one, plus all in order); run() invokes them; cancel flips a flag. */
    private static final class CapturingScheduler implements ForfeitScheduler {
        Runnable lastTask;
        Instant lastTime;
        boolean cancelled;
        final List<Runnable> scheduled = new ArrayList<>();

        @Override
        public ScheduledForfeit schedule(Instant deadline, Runnable task) {
            this.lastTask = task;
            this.lastTime = deadline;
            this.cancelled = false;
            this.scheduled.add(task);
            return () -> this.cancelled = true;
        }
    }

    /** Records every broadcast instead of touching a real broker. */
    private static final class RecordingMessaging extends GameMessagingService {
        final List<Response> sent = new ArrayList<>();
        RecordingMessaging() { super(null); }
        @Override public void sendToGame(String gameId, Object payload) { sent.add((Response) payload); }
    }

    private SessionService sessionService;
    private CapturingScheduler scheduler;
    private RecordingMessaging messaging;
    private ConnectionRegistry registry;
    private ForfeitLog forfeitLog;
    private PresenceService service;
    private GameId gameId;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-09T12:00:00Z"), ZoneOffset.UTC);
        sessionService = new SessionService(new InMemorySessionRepository(clock), new org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories(java.util.List.of(new org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory())));
        scheduler = new CapturingScheduler();
        messaging = new RecordingMessaging();
        registry = new InMemoryConnectionRegistry();
        forfeitLog = new InMemoryForfeitLog();
        var broadcaster = new BatailleCorseLifecycleBroadcaster(messaging, forfeitLog);
        var broadcasters = new GameLifecycleBroadcasters(List.of(broadcaster));
        service = new PresenceService(sessionService, registry, scheduler, clock, forfeitLog, broadcasters);

        BatailleCorse game = (BatailleCorse) sessionService.createGame("bataille-corse", 2, GameMode.MULTIPLAYER);
        gameId = game.getId();
    }

    private List<String> eventTypes() {
        return messaging.sent.stream().map(Response::getEventType).toList();
    }

    private String forfeitReasonInLastStateForSeat(String seatId) {
        Response last = messaging.sent.get(messaging.sent.size() - 1);
        return ((BatailleCorseDto) last.getState()).getPlayers().stream()
                .filter(p -> p.getId().equals(seatId))
                .map(PlayerDto::getForfeitReason)
                .findFirst()
                .orElse(null);
    }

    @Test
    void givenSeatBound_whenDisconnect_thenSchedulesAndBroadcastsDisconnected() {
        service.onPresence("sess-0", gameId, new PlayerId(0));

        service.onDisconnect("sess-0");

        assertThat(scheduler.lastTask != null, is(true));
        assertThat(eventTypes(), contains("OPPONENT_DISCONNECTED"));
    }

    @Test
    void givenPendingForfeit_whenReconnect_thenCancelsAndBroadcastsReconnected() {
        service.onPresence("sess-0", gameId, new PlayerId(0));
        service.onDisconnect("sess-0");

        service.onPresence("sess-0b", gameId, new PlayerId(0)); // same seat, new session

        assertThat(scheduler.cancelled, is(true));
        assertThat(eventTypes(), contains("OPPONENT_DISCONNECTED", "OPPONENT_RECONNECTED"));
    }

    @Test
    void givenPendingForfeit_whenTimerFires_thenGameConcededAndForfeitBroadcast() {
        service.onPresence("sess-0", gameId, new PlayerId(0));
        service.onDisconnect("sess-0");

        scheduler.lastTask.run(); // simulate the 60s elapsing

        BatailleCorse game = (BatailleCorse) sessionService.getGame(gameId);
        assertThat(game.isFinished(), is(true));
        assertThat(game.getWinner().id(), is(new PlayerId(1)));
        assertThat(eventTypes(), contains("OPPONENT_DISCONNECTED", "FORFEIT"));
        assertThat(forfeitReasonInLastStateForSeat("0"), is("DISCONNECTED"));
    }

    @Test
    void givenFinishedGame_whenDisconnect_thenNoScheduleNoBroadcast() {
        sessionService.getGame(gameId).forfeit(new PlayerId(1)); // already over
        service.onPresence("sess-0", gameId, new PlayerId(0));

        service.onDisconnect("sess-0");

        assertThat(scheduler.lastTask == null, is(true));
        assertThat(eventTypes().isEmpty(), is(true));
    }

    @Test
    void whenForfeitCalledDirectly_thenConcedesAndBroadcastsForfeitWithResigned() {
        service.forfeit(new Seat(gameId, new PlayerId(1)), ForfeitReason.RESIGNED);

        BatailleCorse game = (BatailleCorse) sessionService.getGame(gameId);
        assertThat(game.getWinner().id(), is(new PlayerId(0)));
        assertThat(eventTypes(), contains("FORFEIT"));
        assertThat(forfeitReasonInLastStateForSeat("1"), is("RESIGNED"));
    }

    @Test
    void givenBothPlayersDisconnect_whenBothTimersFire_thenFirstDroppedLosesAndOnlyOneForfeit() {
        service.onPresence("sess-0", gameId, new PlayerId(0));
        service.onPresence("sess-1", gameId, new PlayerId(1));
        service.onDisconnect("sess-0"); // seat 0 dropped first
        service.onDisconnect("sess-1");

        scheduler.scheduled.get(0).run(); // seat 0's timer -> seat 1 wins, game over
        scheduler.scheduled.get(1).run(); // seat 1's timer -> no-op on finished game

        BatailleCorse game = (BatailleCorse) sessionService.getGame(gameId);
        assertThat(game.getWinner().id(), is(new PlayerId(1)));
        long forfeits = eventTypes().stream().filter("FORFEIT"::equals).count();
        assertThat(forfeits, is(1L));
    }

    @Test
    void givenTimerAlreadyFired_whenLateReconnect_thenNoReconnectBroadcastAndStaysFinished() {
        service.onPresence("sess-0", gameId, new PlayerId(0));
        service.onDisconnect("sess-0");
        scheduler.lastTask.run(); // forfeit already happened

        service.onPresence("sess-0b", gameId, new PlayerId(0)); // reconnect arrives too late

        BatailleCorse game = (BatailleCorse) sessionService.getGame(gameId);
        assertThat(game.getWinner().id(), is(new PlayerId(1)));
        assertThat(eventTypes(), contains("OPPONENT_DISCONNECTED", "FORFEIT")); // no OPPONENT_RECONNECTED
    }
}
