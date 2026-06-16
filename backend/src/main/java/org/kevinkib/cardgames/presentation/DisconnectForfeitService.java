package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.application.InvalidGameIdException;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;
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
 * pending timer. Broadcasting is delegated to the per-game {@link GameLifecycleBroadcaster};
 * this service knows only the lifecycle, never a game's state shape.
 */
public class DisconnectForfeitService {

    /** Grace before a dropped player auto-loses. */
    public static final Duration FORFEIT_GRACE = Duration.ofSeconds(60);

    private final SessionService sessionService;
    private final StompSessionSeatRegistry registry;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final ForfeitReasonRegistry forfeitReasonRegistry;
    private final GameLifecycleBroadcasters broadcasters;

    private final Map<Seat, ScheduledFuture<?>> pendingForfeits = new ConcurrentHashMap<>();

    public DisconnectForfeitService(SessionService sessionService,
                                    StompSessionSeatRegistry registry,
                                    TaskScheduler scheduler,
                                    Clock clock,
                                    ForfeitReasonRegistry forfeitReasonRegistry,
                                    GameLifecycleBroadcasters broadcasters) {
        this.sessionService = sessionService;
        this.registry = registry;
        this.scheduler = scheduler;
        this.clock = clock;
        this.forfeitReasonRegistry = forfeitReasonRegistry;
        this.broadcasters = broadcasters;
    }

    /** Records presence; if this seat had a pending forfeit, cancels it and announces the return. */
    public void onPresence(String sessionId, GameId gameId, PlayerId playerId) {
        Seat seat = new Seat(gameId, playerId);
        registry.bind(sessionId, seat);

        ScheduledFuture<?> pending = pendingForfeits.remove(seat);
        if (pending != null) {
            pending.cancel(false);
            Game game = findGame(seat.gameId());
            if (game != null) {
                broadcasters.broadcasterFor(game).reconnected(game, seat);
            }
        }
    }

    /** Attributes a dropped session to a seat and, if the game is live, starts the auto-loss timer. */
    public void onDisconnect(String sessionId) {
        Optional<Seat> maybeSeat = registry.unbind(sessionId);
        if (maybeSeat.isEmpty()) {
            return;
        }
        Seat seat = maybeSeat.get();

        Game game = findGame(seat.gameId());
        if (game == null || game.isFinished()) {
            return;
        }

        Instant deadline = clock.instant().plus(FORFEIT_GRACE);
        ScheduledFuture<?> task = scheduler.schedule(() -> forfeit(seat, ForfeitReason.DISCONNECTED), deadline);
        pendingForfeits.put(seat, task);
        broadcasters.broadcasterFor(game).disconnected(game, seat, deadline.toEpochMilli());
    }

    /** Terminal path shared by the timer (DISCONNECTED) and explicit /app/forfeit (RESIGNED). Idempotent on a finished game. */
    public void forfeit(Seat seat, ForfeitReason reason) {
        pendingForfeits.remove(seat);

        Game game = findGame(seat.gameId());
        if (game == null || game.isFinished()) {
            return;
        }
        game.forfeit(seat.playerId());
        forfeitReasonRegistry.record(seat, reason);
        sessionService.touch(seat.gameId()); // start the finished-grace clock
        broadcasters.broadcasterFor(game).forfeited(game, seat, reason);
    }

    private Game findGame(GameId gameId) {
        try {
            return sessionService.getGame(gameId);
        } catch (InvalidGameIdException e) {
            return null;
        }
    }
}
