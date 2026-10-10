package org.kevinkib.cardgames.bullshit.domain.options;

import org.kevinkib.cardgames.bullshit.domain.claim.ClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimModeOption;
import org.kevinkib.cardgames.bullshit.domain.deck.DeckSize;
import org.kevinkib.cardgames.game.GameOptions;

import java.util.random.RandomGenerator;

/** Typed Bullshit creation options, parsed once from the opaque {@link GameOptions} map at the
 *  Bullshit edge. Future options extend this record and its {@link #from} parsing. */
public record BullshitOptions(ClaimModeOption claimMode, DeckSize deckSize) {

    public static final String CLAIM_MODE_KEY = "claimMode";
    public static final String DECK_SIZE_KEY = "deckSize";

    public BullshitOptions(ClaimModeOption claimMode) {
        this(claimMode, DeckSize.FULL);
    }

    /** An absent deck size follows {@code nbPlayers} (see {@link DeckSize#defaultFor}); an explicit one wins. */
    public static BullshitOptions from(GameOptions options, int nbPlayers) {
        DeckSize deckSize = options.get(DECK_SIZE_KEY).map(DeckSize::fromKey).orElseGet(() -> DeckSize.defaultFor(nbPlayers));
        return new BullshitOptions(ClaimModeOption.fromKey(options.get(CLAIM_MODE_KEY).orElse(null)), deckSize);
    }

    public ClaimMode toClaimMode(RandomGenerator random) {
        return claimMode.create(deckSize, random);
    }
}
