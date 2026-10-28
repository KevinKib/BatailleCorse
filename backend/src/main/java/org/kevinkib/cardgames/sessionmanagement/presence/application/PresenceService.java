package org.kevinkib.cardgames.sessionmanagement.presence.application;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.AddressedLobbyView;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameDirectory;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.SeatClaim;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ConnectionRegistry;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitScheduler;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ScheduledForfeit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Detects multiplayer disconnects and runs the 60s auto-loss timer, plus the
 * shared forfeit path. Reconnect (a fresh presence for the same seat) cancels a
 * pending timer. Broadcasting is delegated to the per-game {@link GameLifecycleBroadcasters};
 * this service knows only the lifecycle, never a game's state shape.
 */
public class PresenceService {

    /** Grace before a dropped player auto-loses. */
    public static final Duration FORFEIT_GRACE = Duration.ofSeconds(60);

    private final Duration forfeitGrace;
    private final GameDirectory games;
    private final ConnectionRegistry registry;
    private final ForfeitScheduler scheduler;
    private final Clock clock;
    private final ForfeitLog forfeitLog;
    private final GameLifecycleBroadcasters broadcasters;

    private final Map<Seat, ScheduledForfeit> pendingForfeits = new ConcurrentHashMap<>();
    private final Map<GameId, Duration> graceOverrides = new ConcurrentHashMap<>();
    private volatile BiConsumer<GameId, List<AddressedLobbyView>> lobbyPresenceListener = (id, views) -> { };

    /**
     * Called with the lobby views to publish when a human's connection to a not-yet-started lobby drops or
     * returns. A dropped lobby connection starts no timer and frees no seat: the host decides.
     */
    public void setLobbyPresenceListener(BiConsumer<GameId, List<AddressedLobbyView>> listener) {
        this.lobbyPresenceListener = listener;
    }

    public PresenceService(GameDirectory games,
                           ConnectionRegistry registry,
                           ForfeitScheduler scheduler,
                           Clock clock,
                           ForfeitLog forfeitLog,
                           GameLifecycleBroadcasters broadcasters) {
        this(games, registry, scheduler, clock, forfeitLog, broadcasters, FORFEIT_GRACE);
    }

    /** As above, with an explicit grace (the e2e profile shortens it; production keeps {@link #FORFEIT_GRACE}). */
    public PresenceService(GameDirectory games,
                           ConnectionRegistry registry,
                           ForfeitScheduler scheduler,
                           Clock clock,
                           ForfeitLog forfeitLog,
                           GameLifecycleBroadcasters broadcasters,
                           Duration forfeitGrace) {
        this.forfeitGrace = forfeitGrace;
        this.games = games;
        this.registry = registry;
        this.scheduler = scheduler;
        this.clock = clock;
        this.forfeitLog = forfeitLog;
        this.broadcasters = broadcasters;
    }

    public Duration forfeitGrace() {
        return forfeitGrace;
    }

    /** The grace that applies to one game: its override if any (e2e profile only), the default otherwise. */
    public Duration forfeitGraceFor(GameId gameId) {
        return graceOverrides.getOrDefault(gameId, forfeitGrace);
    }

    /** Shortens (or lengthens) the grace of one game only; other games keep the default. */
    public void overrideForfeitGrace(GameId gameId, Duration grace) {
        graceOverrides.put(gameId, grace);
    }

    /**
     * Records presence for a seat token; if the seat it currently holds had a pending forfeit, cancels
     * it and announces the return. A token that resolves to no seat binds nothing.
     */
    public void onPresence(String connectionId, GameId gameId, String token) {
        Optional<PlayerId> playerId = resolve(gameId, token);
        if (playerId.isEmpty()) {
            return;
        }
        registry.bind(connectionId, new SeatClaim(gameId, token));
        publishLobbyConnection(gameId, token, true);

        Seat seat = new Seat(gameId, playerId.get());
        ScheduledForfeit pending = pendingForfeits.remove(seat);
        if (pending != null) {
            pending.cancel();
            Game game = findGame(seat.gameId());
            // On a finished game the pending task was a rematch departure: nothing to announce.
            if (game != null && !game.isFinished()) {
                broadcasters.broadcasterFor(game).reconnected(game, seat.playerId());
            }
        }
    }

    /**
     * Attributes a dropped connection to a seat and starts the grace timer. On a live game the
     * timer is the auto-loss; on a finished game it is the departure from the rematch (same delay,
     * same cancel-on-reconnect, but silent: nothing is broadcast until the delay is over).
     */
    public void onDisconnect(String connectionId) {
        Optional<SeatClaim> claim = registry.unbind(connectionId);
        if (claim.isEmpty()) {
            return;
        }
        // The seat is resolved now, not at bind time: a token that no longer resolves holds nothing.
        Optional<PlayerId> playerId = resolve(claim.get().gameId(), claim.get().token());
        if (playerId.isEmpty()) {
            return;
        }
        Seat seat = new Seat(claim.get().gameId(), playerId.get());

        Game game = findGame(seat.gameId());
        if (game == null) {
            publishLobbyConnection(seat.gameId(), claim.get().token(), false);
            return;
        }

        Instant deadline = clock.instant().plus(forfeitGraceFor(seat.gameId()));
        ScheduledForfeit task = scheduler.schedule(deadline,
                () -> forfeit(seat.gameId(), seat.playerId(), ForfeitReason.DISCONNECTED));
        pendingForfeits.put(seat, task);
        if (!game.isFinished()) {
            broadcasters.broadcasterFor(game).disconnected(game, seat.playerId(), deadline.toEpochMilli());
        }
    }

    /**
     * Terminal path shared by the timer (DISCONNECTED) and explicit /app/forfeit (RESIGNED). On a
     * finished game there is nothing to concede.
     */
    public void forfeit(GameId gameId, PlayerId playerId, ForfeitReason reason) {
        Seat seat = new Seat(gameId, playerId);
        pendingForfeits.remove(seat);

        Game game = findGame(gameId);
        if (game == null) {
            return;
        }
        if (game.isFinished()) {
            return;
        }
        game.forfeit(playerId);
        forfeitLog.record(seat, reason);
        games.touch(gameId); // start the finished-grace clock
        broadcasters.broadcasterFor(game).forfeited(game, playerId, reason);
    }

    private void publishLobbyConnection(GameId gameId, String token, boolean connected) {
        List<AddressedLobbyView> views = games.markSeatConnection(gameId, token, connected);
        if (!views.isEmpty()) {
            lobbyPresenceListener.accept(gameId, views);
        }
    }

    private Optional<PlayerId> resolve(GameId gameId, String token) {
        try {
            return games.findPlayerIdByToken(gameId, token);
        } catch (IllegalArgumentException unknownGame) {
            return Optional.empty();
        }
    }

    private Game findGame(GameId gameId) {
        return games.findGame(gameId).orElse(null);
    }
}
