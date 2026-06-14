package org.kevinkib.cardgames.game;

public interface GameFactory {

    Game create(GameId id, int nbPlayers);
}
