package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cardgames.bullshit.domain.claim.AscendingRankClaimMode;
import org.kevinkib.cardgames.bullshit.domain.options.BullshitOptions;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimModeOption;
import org.kevinkib.cardgames.bullshit.domain.deck.DeckSize;

import java.util.Arrays;
import java.util.List;
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
    public boolean supportsBots() {
        return true;
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

    @Override
    public List<OptionDescriptor> optionDescriptors() {
        return List.of(
                new OptionDescriptor(BullshitOptions.CLAIM_MODE_KEY, OptionType.CHOICE,
                        Arrays.stream(ClaimModeOption.values()).map(ClaimModeOption::key).toList(),
                        ClaimModeOption.DEFAULT.key(), "options.bullshit.claimMode"),
                new OptionDescriptor(BullshitOptions.DECK_SIZE_KEY, OptionType.CHOICE,
                        Arrays.stream(DeckSize.values()).map(DeckSize::key).toList(),
                        DeckSize.DEFAULT.key(), "options.bullshit.deckSize"));
    }

    @Override
    public GameOptions validate(GameOptions requested) {
        return BullshitOptions.parseStrict(requested).toGameOptions();
    }
}
