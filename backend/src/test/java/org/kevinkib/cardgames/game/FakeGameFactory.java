package org.kevinkib.cardgames.game;

public class FakeGameFactory implements GameFactory {

    public static final String GAME_TYPE = "fake";

    @Override
    public String gameType() {
        return GAME_TYPE;
    }

    @Override
    public Game create(GameId id, int nbPlayers) {
        return new FakeGame(id, nbPlayers);
    }
}
