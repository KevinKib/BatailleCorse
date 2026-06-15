package org.kevinkib.cardgames.presentation;
import org.kevinkib.cardgames.bataillecorse.presentation.BatailleCorseLifecycleBroadcaster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.domain.GameMode;
import org.kevinkib.cardgames.sessionmanagement.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.PlayerDto;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

class DisconnectForfeitServiceTest {

    /** Captures scheduled tasks (last one, plus all in order); run() invokes them; cancel flips a flag. */
    private static final class CapturingScheduler implements TaskScheduler {
        Runnable lastTask;
        Instant lastTime;
        boolean cancelled;
        final List<Runnable> scheduled = new ArrayList<>();
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            this.lastTask = task;
            this.lastTime = startTime;
            this.cancelled = false;
            this.scheduled.add(task);
            return new FakeFuture();
        }
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) { throw new UnsupportedOperationException(); }
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, java.time.Duration period) { throw new UnsupportedOperationException(); }
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, java.time.Duration period) { throw new UnsupportedOperationException(); }
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, java.time.Duration delay) { throw new UnsupportedOperationException(); }
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, java.time.Duration delay) { throw new UnsupportedOperationException(); }

        private final class FakeFuture implements ScheduledFuture<Object> {
            public long getDelay(java.util.concurrent.TimeUnit unit) { return 0; }
            public int compareTo(java.util.concurrent.Delayed o) { return 0; }
            public boolean cancel(boolean mayInterrupt) { cancelled = true; return true; }
            public boolean isCancelled() { return cancelled; }
            public boolean isDone() { return false; }
            public Object get() { return null; }
            public Object get(long t, java.util.concurrent.TimeUnit u) { return null; }
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
    private StompSessionSeatRegistry registry;
    private ForfeitReasonRegistry forfeitReasonRegistry;
    private DisconnectForfeitService service;
    private GameId gameId;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-09T12:00:00Z"), ZoneOffset.UTC);
        sessionService = new SessionService(new InMemorySessionRepository(clock), new org.kevinkib.cardgames.sessionmanagement.application.GameFactories(java.util.List.of(new org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory())));
        scheduler = new CapturingScheduler();
        messaging = new RecordingMessaging();
        registry = new StompSessionSeatRegistry();
        forfeitReasonRegistry = new ForfeitReasonRegistry();
        var broadcaster = new BatailleCorseLifecycleBroadcaster(messaging, forfeitReasonRegistry);
        var broadcasters = new GameLifecycleBroadcasters(List.of(broadcaster));
        service = new DisconnectForfeitService(sessionService, registry, scheduler, clock, forfeitReasonRegistry, broadcasters);

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
