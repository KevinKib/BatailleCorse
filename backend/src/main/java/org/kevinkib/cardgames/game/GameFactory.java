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

    /** The options as they will apply if the game starts now with {@code nbPlayers} players
     *  (defaults that depend on the player count resolved). Games without such defaults keep this. */
    default GameOptions effectiveOptions(GameOptions options, int nbPlayers) {
        return options;
    }
}
