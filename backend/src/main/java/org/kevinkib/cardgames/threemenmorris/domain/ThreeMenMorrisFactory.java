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
        if (nbPlayers != ThreeMenMorris.PLAYERS) {
            throw new IllegalArgumentException("Three Men's Morris is played by exactly "
                    + ThreeMenMorris.PLAYERS + " players, not " + nbPlayers);
        }
        return new ThreeMenMorris(id);
    }
}
