package org.kevinkib.cardgames.threemenmorris.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;

public class ThreeMenMorrisFactory implements GameFactory {

    public static final String GAME_TYPE = "three-men-morris";

    @Override
    public String gameType() {
        return GAME_TYPE;
    }

    @Override
    public int minPlayers() {
        return ThreeMenMorris.PLAYERS;
    }

    @Override
    public int maxPlayers() {
        return ThreeMenMorris.PLAYERS;
    }

    @Override
    public Game create(GameId id, int nbPlayers) {
        return new ThreeMenMorris(id);
    }
}
