package org.kevinkib.cardgames.sessionmanagement.application.port;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.bataillecorse.domain.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionToken;

import java.time.Duration;
import java.util.List;

public interface SessionRepository {

    void save(BatailleCorse batailleCorse, SessionGame sessionGame);

    BatailleCorse load(GameId id);

    SessionToken loadSessionToken(GameId batailleCorseId, PlayerId playerId);

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
}
