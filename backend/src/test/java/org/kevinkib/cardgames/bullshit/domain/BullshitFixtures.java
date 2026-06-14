package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cardgames.bullshit.domain.player.Player;
import org.kevinkib.cardgames.bullshit.domain.player.PlayerBuilder;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

public final class BullshitFixtures {

    public static Player playerWithRanks(int id, FrenchRank... ranks) {
        return PlayerBuilder.aPlayer().withId(id).withRanks(ranks).build();
    }
}
