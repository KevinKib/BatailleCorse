package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cards.domain.Card;

import java.util.List;

public record Discard(PlayerId claimant, ClaimTarget claimedTarget, List<Card> actualCards) {
}
