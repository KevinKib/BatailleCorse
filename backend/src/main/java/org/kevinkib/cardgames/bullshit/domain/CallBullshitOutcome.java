package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cardgames.game.PlayerId;

public record CallBullshitOutcome(boolean claimWasTruthful, PlayerId pilePicker) {
}
