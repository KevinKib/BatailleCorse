package org.kevinkib.cardgames.bullshit.domain.claim;

import org.kevinkib.cardgames.bullshit.domain.deck.DeckSize;

import java.util.random.RandomGenerator;

/** The single source of truth mapping a stable claim-mode key to its {@link ClaimMode} strategy. */
public enum ClaimModeOption {

    RANK("rank") {
        @Override
        public ClaimMode create(DeckSize deckSize, RandomGenerator random) {
            return new AscendingRankClaimMode(deckSize);
        }
    },
    SUIT("suit") {
        @Override
        public ClaimMode create(DeckSize deckSize, RandomGenerator random) {
            return new CyclingSuitClaimMode(random);
        }
    };

    private final String key;

    ClaimModeOption(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public abstract ClaimMode create(DeckSize deckSize, RandomGenerator random);

    /** Resolves a key to its option; unknown or {@code null} keys fall back to {@link #RANK}. */
    public static ClaimModeOption fromKey(String key) {
        for (ClaimModeOption option : values()) {
            if (option.key.equals(key)) {
                return option;
            }
        }
        return RANK;
    }
}
