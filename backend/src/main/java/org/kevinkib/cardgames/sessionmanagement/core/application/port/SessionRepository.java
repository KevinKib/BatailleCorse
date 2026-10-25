package org.kevinkib.cardgames.sessionmanagement.core.application.port;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionToken;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SessionRepository {

    void save(Game game, SessionGame sessionGame);

    Game load(GameId id);

    SessionToken loadSessionToken(GameId gameId, PlayerId playerId);

    SessionGame loadSessionGame(GameId id);

    /** Records that the game saw activity now, resetting its idle/grace clock. */
    void touch(GameId id);

    /** Removes a game and its session. No-op if absent. */
    void remove(GameId id);

    /**
     * Removes games whose idle time exceeds the relevant threshold: {@code finishedGrace}
     * for finished games, {@code idleTtl} for unfinished ones. Returns the evicted ids.
     */
    List<GameId> evictStale(Duration finishedGrace, Duration idleTtl);

    /** Stores a session that has no game yet (a lobby). */
    void saveLobby(SessionGame sessionGame);

    /**
     * Replaces a room's game and session with a fresh lobby (Play again). Unlike {@link #remove} followed by
     * {@link #saveLobby}, the room keeps the time it was first opened.
     */
    default void reopen(SessionGame lobby) {
        remove(lobby.id());
        saveLobby(lobby);
    }

    /** Every session of a game type, lobbies and started games together; a snapshot. */
    default List<SessionGame> sessionsOfType(String gameType) {
        return List.of();
    }

    /** When the room was first saved (kept by Play again), or the epoch if it is unknown. */
    default Instant openedAt(GameId id) {
        return Instant.EPOCH;
    }

    /** The started game for this id, or empty if it is still a lobby / unknown. */
    Optional<Game> findGame(GameId id);

    /** Every started game currently held (lobbies excluded); a snapshot. */
    default List<Game> games() {
        return List.of();
    }

    /** How many rooms are held, lobbies and started games together. */
    default int roomCount() {
        return 0;
    }
}
