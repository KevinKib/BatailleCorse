package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.application.InvalidGameIdException;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.presentation.dto.event.EventType;
import org.kevinkib.cardgames.presentation.dto.event.ForfeitEventData;
import org.kevinkib.cardgames.presentation.dto.event.OpponentDisconnectedEventData;
import org.kevinkib.cardgames.presentation.dto.event.OpponentReconnectedEventData;
import org.springframework.scheduling.TaskScheduler;

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
public class DisconnectForfeitService {

    /** Grace before a dropped player auto-loses. */
    public static final Duration FORFEIT_GRACE = Duration.ofSeconds(60);

    private final SessionService sessionService;
    private final GameMessagingService messaging;
    private final StompSessionSeatRegistry registry;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final ForfeitReasonRegistry forfeitReasonRegistry;

    private final Map<Seat, ScheduledFuture<?>> pendingForfeits = new ConcurrentHashMap<>();

    public DisconnectForfeitService(SessionService sessionService,
                                    GameMessagingService messaging,
                                    StompSessionSeatRegistry registry,
                                    TaskScheduler scheduler,
                                    Clock clock,
                                    ForfeitReasonRegistry forfeitReasonRegistry) {
        this.sessionService = sessionService;
        this.messaging = messaging;
        this.registry = registry;
        this.scheduler = scheduler;
        this.clock = clock;
        this.forfeitReasonRegistry = forfeitReasonRegistry;
    }

    /** Records presence; if this seat had a pending forfeit, cancels it and announces the return. */
    public void onPresence(String sessionId, GameId gameId, PlayerId playerId) {
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
        ScheduledFuture<?> task = scheduler.schedule(() -> forfeit(seat, ForfeitReason.DISCONNECTED), deadline);
        pendingForfeits.put(seat, task);
        broadcastDisconnected(seat, deadline.toEpochMilli());
    }

    /** Terminal path shared by the timer (DISCONNECTED) and explicit /app/forfeit (RESIGNED). Idempotent on a finished game. */
    public void forfeit(Seat seat, ForfeitReason reason) {
        pendingForfeits.remove(seat);

        BatailleCorse game = findGame(seat.gameId());
        if (game == null || game.isFinished()) {
            return;
        }
        game.forfeit(seat.playerId());
        forfeitReasonRegistry.record(seat, reason);
        sessionService.touch(seat.gameId()); // start the finished-grace clock
        broadcast(seat.gameId(), new SuccessResponse(
                EventType.FORFEIT,
                new ForfeitEventData(seat.playerId().id()),
                "Player " + seat.playerId() + " forfeited.",
                BatailleCorseDto.from(game, forfeitReasonRegistry.reasonsBySeat(seat.gameId()))));
    }

    private void broadcastDisconnected(Seat seat, long deadlineEpochMs) {
        BatailleCorse game = findGame(seat.gameId());
        if (game == null) {
            return;
        }
        broadcast(seat.gameId(), new SuccessResponse(
                EventType.OPPONENT_DISCONNECTED,
                new OpponentDisconnectedEventData(seat.playerId().id(), deadlineEpochMs),
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
                new OpponentReconnectedEventData(seat.playerId().id()),
                "Player " + seat.playerId() + " reconnected.",
                BatailleCorseDto.from(game)));
    }

    private void broadcast(GameId gameId, Response response) {
        messaging.sendToGame(gameId.uuid().toString(), response);
    }

    private BatailleCorse findGame(GameId gameId) {
        try {
            return (BatailleCorse) sessionService.getGame(gameId);
        } catch (InvalidGameIdException e) {
            return null;
        }
    }
}
