package org.kevinkib.cardgames.bullshit.domain.pile;

import org.kevinkib.cardgames.bullshit.domain.claim.ClaimTarget;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

import java.util.List;

public record Discard(PlayerId claimant, ClaimTarget claimedTarget, List<Card> actualCards) {
}
