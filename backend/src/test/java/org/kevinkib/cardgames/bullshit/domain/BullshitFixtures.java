package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cards.domain.deck.french.FrenchRank;

public final class BullshitFixtures {

    public static Player playerWithRanks(int id, FrenchRank... ranks) {
        return PlayerBuilder.aPlayer().withId(id).withRanks(ranks).build();
    }

    public static Player emptyPlayer(int id) {
        return PlayerBuilder.aPlayer().withId(id).withEmptyHand().build();
    }
}
