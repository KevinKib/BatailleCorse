package org.kevinkib.cardgames.bataillecorse.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;

public class BatailleCorseFactory implements GameFactory {

    @Override
    public Game create(GameId id, int nbPlayers) {
        return new BatailleCorse(id, nbPlayers);
    }
}
