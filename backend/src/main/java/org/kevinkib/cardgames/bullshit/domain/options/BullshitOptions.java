package org.kevinkib.cardgames.bullshit.domain.options;

import org.kevinkib.cardgames.bullshit.domain.claim.ClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimModeOption;
import org.kevinkib.cardgames.game.GameOptions;

/** Typed Bullshit creation options, parsed once from the opaque {@link GameOptions} map at the
 *  Bullshit edge. Future options extend this record and its {@link #from} parsing. */
public record BullshitOptions(ClaimModeOption claimMode) {

    static final String CLAIM_MODE_KEY = "claimMode";

    public static BullshitOptions from(GameOptions options) {
        return new BullshitOptions(ClaimModeOption.fromKey(options.get(CLAIM_MODE_KEY).orElse(null)));
    }

    public ClaimMode toClaimMode() {
        return claimMode.create();
    }
}
