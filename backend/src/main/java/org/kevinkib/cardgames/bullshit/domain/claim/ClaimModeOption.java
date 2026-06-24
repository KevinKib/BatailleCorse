package org.kevinkib.cardgames.bullshit.domain.claim;

import java.util.function.Supplier;

/** The single source of truth mapping a stable claim-mode key to its {@link ClaimMode} strategy. */
public enum ClaimModeOption {

    RANK("rank", AscendingRankClaimMode::new),
    SUIT("suit", CyclingSuitClaimMode::new);

    private final String key;
    private final Supplier<ClaimMode> factory;

    ClaimModeOption(String key, Supplier<ClaimMode> factory) {
        this.key = key;
        this.factory = factory;
    }

    public String key() {
        return key;
    }

    public ClaimMode create() {
        return factory.get();
    }

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
