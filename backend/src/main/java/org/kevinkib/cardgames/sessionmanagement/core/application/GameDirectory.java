package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;

import java.util.Optional;

/** Core's published view of live games, for downstream contexts (e.g. presence). */
public interface GameDirectory {
    Optional<Game> findGame(GameId id);
    void touch(GameId id);
}
