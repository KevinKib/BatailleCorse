package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cardgames.bullshit.domain.options.BullshitOptions;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;

public class BullshitFactory implements GameFactory {

    public static final String GAME_TYPE = "bullshit";

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
        return 6;
    }

    @Override
    public Game create(GameId id, int nbPlayers) {
        return new Bullshit(id, nbPlayers);
    }

    @Override
    public Game create(GameId id, int nbPlayers, GameOptions options) {
        return new Bullshit(id, nbPlayers, BullshitOptions.from(options).toClaimMode());
    }
}
