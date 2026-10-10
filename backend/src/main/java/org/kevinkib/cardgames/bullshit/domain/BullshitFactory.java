package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cardgames.bullshit.domain.claim.AscendingRankClaimMode;
import org.kevinkib.cardgames.bullshit.domain.options.BullshitOptions;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;

import java.util.random.RandomGenerator;

public class BullshitFactory implements GameFactory {

    public static final String GAME_TYPE = "bullshit";

    private final RandomGenerator random;

    public BullshitFactory() {
        this(RandomGenerator.getDefault());
    }

    /** @param random the randomness port handed to claim modes that draw targets at random */
    public BullshitFactory(RandomGenerator random) {
        this.random = random;
    }

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
        return new Bullshit(id, nbPlayers, new AscendingRankClaimMode());
    }

    @Override
    public Game create(GameId id, int nbPlayers, GameOptions options) {
        BullshitOptions parsed = BullshitOptions.from(options);
        return new Bullshit(id, nbPlayers, parsed.toClaimMode(random), parsed.deckSize());
    }
}
