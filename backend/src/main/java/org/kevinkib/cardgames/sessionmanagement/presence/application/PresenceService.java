package org.kevinkib.cardgames.sessionmanagement.presence.application;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameDirectory;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ConnectionRegistry;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitScheduler;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ScheduledForfeit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects multiplayer disconnects and runs the 60s auto-loss timer, plus the
 * shared forfeit path. Reconnect (a fresh presence for the same seat) cancels a
 * pending timer. Broadcasting is delegated to the per-game {@link GameLifecycleBroadcasters};
 * this service knows only the lifecycle, never a game's state shape.
 */
public class PresenceService {

    /** Grace before a dropped player auto-loses. */
    public static final Duration FORFEIT_GRACE = Duration.ofSeconds(60);

    private final GameDirectory games;
    private final ConnectionRegistry registry;
    private final ForfeitScheduler scheduler;
    private final Clock clock;
    private final ForfeitLog forfeitLog;
    private final GameLifecycleBroadcasters broadcasters;

    private final Map<Seat, ScheduledForfeit> pendingForfeits = new ConcurrentHashMap<>();

    public PresenceService(GameDirectory games,
                           ConnectionRegistry registry,
                           ForfeitScheduler scheduler,
                           Clock clock,
                           ForfeitLog forfeitLog,
                           GameLifecycleBroadcasters broadcasters) {
        this.games = games;
        this.registry = registry;
        this.scheduler = scheduler;
        this.clock = clock;
        this.forfeitLog = forfeitLog;
        this.broadcasters = broadcasters;
    }

    /** Records presence; if this seat had a pending forfeit, cancels it and announces the return. */
    public void onPresence(String connectionId, GameId gameId, PlayerId playerId) {
        Seat seat = new Seat(gameId, playerId);
        registry.bind(connectionId, seat);

        ScheduledForfeit pending = pendingForfeits.remove(seat);
        if (pending != null) {
            pending.cancel();
            Game game = findGame(seat.gameId());
            if (game != null) {
                broadcasters.broadcasterFor(game).reconnected(game, seat.playerId());
            }
        }
    }

    /** Attributes a dropped connection to a seat and, if the game is live, starts the auto-loss timer. */
    public void onDisconnect(String connectionId) {
        Optional<Seat> maybeSeat = registry.unbind(connectionId);
        if (maybeSeat.isEmpty()) {
            return;
        }
        Seat seat = maybeSeat.get();

        Game game = findGame(seat.gameId());
        if (game == null || game.isFinished()) {
            return;
        }

        Instant deadline = clock.instant().plus(FORFEIT_GRACE);
        ScheduledForfeit task = scheduler.schedule(deadline,
                () -> forfeit(seat.gameId(), seat.playerId(), ForfeitReason.DISCONNECTED));
        pendingForfeits.put(seat, task);
        broadcasters.broadcasterFor(game).disconnected(game, seat.playerId(), deadline.toEpochMilli());
    }

    /** Terminal path shared by the timer (DISCONNECTED) and explicit /app/forfeit (RESIGNED). Idempotent on a finished game. */
    public void forfeit(GameId gameId, PlayerId playerId, ForfeitReason reason) {
        Seat seat = new Seat(gameId, playerId);
        pendingForfeits.remove(seat);

        Game game = findGame(gameId);
        if (game == null || game.isFinished()) {
            return;
        }
        game.forfeit(playerId);
        forfeitLog.record(seat, reason);
        games.touch(gameId); // start the finished-grace clock
        broadcasters.broadcasterFor(game).forfeited(game, playerId, reason);
    }

    private Game findGame(GameId gameId) {
        return games.findGame(gameId).orElse(null);
    }
}
