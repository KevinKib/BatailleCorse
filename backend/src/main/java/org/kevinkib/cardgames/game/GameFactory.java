package org.kevinkib.cardgames.game;

public interface GameFactory {

    /** Stable identifier used to select this game when creating a session. */
    String gameType();

    /** Fewest players the game can start with. */
    int minPlayers();

    /** Most players the game can seat. */
    int maxPlayers();

    Game create(GameId id, int nbPlayers);

    /** Creates the game with host-selected options. Games that ignore options inherit this default. */
    default Game create(GameId id, int nbPlayers, GameOptions options) {
        return create(id, nbPlayers);
    }
}
