package org.kevinkib.cardgames.bataillecorse.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;

public class BatailleCorseFactory implements GameFactory {

    public static final String GAME_TYPE = "bataille-corse";

    @Override
    public String gameType() {
        return GAME_TYPE;
    }

    @Override
    public int minPlayers() {
        return 2;
    }

    @Override
    public int maxPlayers() {
        return 2;
    }

    @Override
    public Game create(GameId id, int nbPlayers) {
        return new BatailleCorse(id, nbPlayers);
    }
}
