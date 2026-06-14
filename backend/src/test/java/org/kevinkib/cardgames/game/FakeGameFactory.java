package org.kevinkib.cardgames.game;

public class FakeGameFactory implements GameFactory {

    @Override
    public Game create(GameId id, int nbPlayers) {
        return new FakeGame(id, nbPlayers);
    }
}
