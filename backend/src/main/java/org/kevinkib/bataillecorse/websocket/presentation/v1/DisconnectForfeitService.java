package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.application.InvalidGameIdException;
import org.kevinkib.bataillecorse.sessionmanagement.application.SessionService;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.Response;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.SuccessResponse;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.BatailleCorseDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.ConnectionEventData;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.EventType;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.ForfeitEventData;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Detects multiplayer disconnects and runs the 60s auto-loss timer, plus the
 * shared forfeit path. Reconnect (a fresh presence for the same seat) cancels a
 * pending timer. The timer firing — and explicit forfeit — both run {@link #forfeit}.
 */
@Service
public class DisconnectForfeitService {

    /** Grace before a dropped player auto-loses. */
    public static final Duration FORFEIT_GRACE = Duration.ofSeconds(60);

    private final SessionService sessionService;
    private final GameMessagingService messaging;
    private final PresenceRegistry registry;
    private final TaskScheduler scheduler;
    private final Clock clock;

    private final Map<Seat, ScheduledFuture<?>> pendingForfeits = new ConcurrentHashMap<>();

    public DisconnectForfeitService(SessionService sessionService,
                                    GameMessagingService messaging,
                                    PresenceRegistry registry,
                                    TaskScheduler scheduler,
                                    Clock clock) {
        this.sessionService = sessionService;
        this.messaging = messaging;
        this.registry = registry;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    /** Records presence; if this seat had a pending forfeit, cancels it and announces the return. */
    public void onPresence(String sessionId, BatailleCorseId gameId, PlayerId playerId) {
        Seat seat = new Seat(gameId, playerId);
        registry.bind(sessionId, seat);

        ScheduledFuture<?> pending = pendingForfeits.remove(seat);
        if (pending != null) {
            pending.cancel(false);
            broadcastReconnected(seat);
        }
    }

    /** Attributes a dropped session to a seat and, if the game is live, starts the auto-loss timer. */
    public void onDisconnect(String sessionId) {
        Optional<Seat> maybeSeat = registry.unbind(sessionId);
        if (maybeSeat.isEmpty()) {
            return;
        }
        Seat seat = maybeSeat.get();

        BatailleCorse game = findGame(seat.gameId());
        if (game == null || game.isFinished()) {
            return;
        }

        Instant deadline = clock.instant().plus(FORFEIT_GRACE);
        ScheduledFuture<?> task = scheduler.schedule(() -> forfeit(seat), deadline);
        pendingForfeits.put(seat, task);
        broadcastDisconnected(seat, deadline.toEpochMilli());
    }

    /** Terminal path shared by the timer and explicit /app/forfeit. Idempotent on a finished game. */
    public void forfeit(Seat seat) {
        pendingForfeits.remove(seat);

        BatailleCorse game = findGame(seat.gameId());
        if (game == null || game.isFinished()) {
            return;
        }
        game.concede(seat.playerId());
        sessionService.touch(seat.gameId()); // start the finished-grace clock
        broadcast(seat.gameId(), new SuccessResponse(
                EventType.FORFEIT,
                new ForfeitEventData(seat.playerId().id()),
                "Player " + seat.playerId() + " forfeited.",
                BatailleCorseDto.from(game)));
    }

    private void broadcastDisconnected(Seat seat, long deadlineEpochMs) {
        BatailleCorse game = findGame(seat.gameId());
        if (game == null) {
            return;
        }
        broadcast(seat.gameId(), new SuccessResponse(
                EventType.OPPONENT_DISCONNECTED,
                new ConnectionEventData(seat.playerId().id(), deadlineEpochMs),
                "Player " + seat.playerId() + " disconnected.",
                BatailleCorseDto.from(game)));
    }

    private void broadcastReconnected(Seat seat) {
        BatailleCorse game = findGame(seat.gameId());
        if (game == null) {
            return;
        }
        broadcast(seat.gameId(), new SuccessResponse(
                EventType.OPPONENT_RECONNECTED,
                new ConnectionEventData(seat.playerId().id(), null),
                "Player " + seat.playerId() + " reconnected.",
                BatailleCorseDto.from(game)));
    }

    private void broadcast(BatailleCorseId gameId, Response response) {
        messaging.sendToGame(gameId.uuid().toString(), response);
    }

    private BatailleCorse findGame(BatailleCorseId gameId) {
        try {
            return sessionService.getGame(gameId);
        } catch (InvalidGameIdException e) {
            return null;
        }
    }
}
