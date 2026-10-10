package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.Optional;

/** Core's published view of live games, for downstream contexts (e.g. presence). */
public interface GameDirectory {
    Optional<Game> findGame(GameId id);
    void touch(GameId id);

    /**
     * A player leaves a finished game's rematch for good; returns the fresh game when that
     * departure completes the rematch (every other expected player had already asked).
     */
    Optional<Game> leaveRematch(GameId id, PlayerId playerId);
}
