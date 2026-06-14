package org.kevinkib.cardgames.sessionmanagement.application.port;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseId;
import org.kevinkib.cardgames.bataillecorse.domain.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionToken;

import java.time.Duration;
import java.util.List;

public interface SessionRepository {

    void save(BatailleCorse batailleCorse, SessionGame sessionGame);

    BatailleCorse load(BatailleCorseId id);

    SessionToken loadSessionToken(BatailleCorseId batailleCorseId, PlayerId playerId);

    SessionGame loadSessionGame(BatailleCorseId id);

    /** Records that the game saw activity now, resetting its idle/grace clock. */
    void touch(BatailleCorseId id);

    /** Removes a game and its session. No-op if absent. */
    void remove(BatailleCorseId id);

    /**
     * Removes games whose idle time exceeds the relevant threshold: {@code finishedGrace}
     * for finished games, {@code idleTtl} for unfinished ones. Returns the evicted ids.
     */
    List<BatailleCorseId> evictStale(Duration finishedGrace, Duration idleTtl);
}
