package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;

public class BullshitFactory implements GameFactory {

    public static final String GAME_TYPE = "bullshit";

    @Override
    public String gameType() {
        return GAME_TYPE;
    }

    @Override
    public Game create(GameId id, int nbPlayers) {
        return new Bullshit(id, nbPlayers);
    }
}
