package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cardgames.bullshit.domain.player.PlayerId;

public record CallBullshitOutcome(boolean claimWasTruthful, PlayerId pilePicker) {
}
