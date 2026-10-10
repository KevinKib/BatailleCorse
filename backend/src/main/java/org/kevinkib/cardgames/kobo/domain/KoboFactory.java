package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;

public class KoboFactory implements GameFactory {

    public static final String GAME_TYPE = "kobo";

    @Override
    public String gameType() {
        return GAME_TYPE;
    }

    @Override
    public int minPlayers() {
        return KoboRules.MIN_PLAYERS;
    }

    @Override
    public int maxPlayers() {
        return KoboRules.MAX_PLAYERS;
    }

    @Override
    public Game create(GameId id, int nbPlayers) {
        return new Kobo(id, nbPlayers);
    }
}
