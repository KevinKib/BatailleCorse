package org.kevinkib.cardgames.bullshit.domain.player;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;
import org.kevinkib.cards.domain.hand.Hand;
import org.kevinkib.cards.testhelpers.CardBuilder;
import org.kevinkib.cards.testhelpers.HandBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PlayerBuilder {

    private int id = 0;
    private Hand hand = HandBuilder.aHand().withNoCards().build();

    public static PlayerBuilder aPlayer() {
        return new PlayerBuilder();
    }

    public PlayerBuilder withId(int id) {
        this.id = id;
        return this;
    }

    public PlayerBuilder withHand(Hand hand) {
        this.hand = hand;
        return this;
    }

    public PlayerBuilder withEmptyHand() {
        this.hand = HandBuilder.aHand().withNoCards().build();
        return this;
    }

    /** Builds a hand from ranks, assigning distinct suits per repeated rank (up to 4). */
    public PlayerBuilder withRanks(FrenchRank... ranks) {
        List<FrenchSuit> suits = FrenchSuit.getSuits();
        Map<FrenchRank, Integer> seen = new HashMap<>();
        List<Card> cards = new ArrayList<>();
        for (FrenchRank rank : ranks) {
            int n = seen.merge(rank, 1, Integer::sum) - 1;
            cards.add(CardBuilder.aCard().withRank(rank).withSuit(suits.get(n % suits.size())).build());
        }
        this.hand = HandBuilder.aHand().withCards(cards).build();
        return this;
    }

    public Player build() {
        return new Player(id, hand);
    }
}
