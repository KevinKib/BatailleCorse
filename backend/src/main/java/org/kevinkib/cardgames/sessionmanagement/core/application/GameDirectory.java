package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;
import java.util.Optional;

/** Core's published view of live games, for downstream contexts (e.g. presence). */
public interface GameDirectory {
    Optional<Game> findGame(GameId id);
    void touch(GameId id);

    /** The seat a token currently holds in a game; empty when the token (or the game) is unknown. */
    Optional<PlayerId> findPlayerIdByToken(GameId id, String token);

    /**
     * A player leaves a finished game's rematch for good; returns the fresh game when that
     * departure completes the rematch (every other expected player had already asked).
     */
    Optional<Game> leaveRematch(GameId id, PlayerId playerId);

    /**
     * A human's connection to a not-yet-started lobby dropped or came back; returns the lobby views to publish,
     * empty when nothing changed. The default knows no lobby.
     */
    default List<AddressedLobbyView> markSeatConnection(GameId id, String token, boolean connected) {
        return List.of();
    }
}
