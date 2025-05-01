package org.kevinkib.bataillecorse.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.Hand;
import org.kevinkib.cards.domain.french.FrenchRank;
import org.kevinkib.cards.testhelpers.CardFixtures;
import org.kevinkib.cards.testhelpers.HandBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import static org.kevinkib.cards.domain.french.FrenchRank.NINE;
import static org.kevinkib.cards.testhelpers.CardBuilder.aCard;
import static org.kevinkib.cards.testhelpers.CardFixtures.anyCard;

public final class PlayerBuilder {
    private Integer id;
    private Hand hand;

    private PlayerBuilder() {
    }

    public static PlayerBuilder aPlayer() {
        return new PlayerBuilder();
    }

    public PlayerBuilder withId(Integer id) {
        this.id = id;
        return this;
    }

    public PlayerBuilder withHand(Hand hand) {
        this.hand = hand;
        return this;
    }

    public PlayerBuilder withCards(Card... cards) {
        this.hand = HandBuilder.aHand()
                .withCards(cards)
                .build();

        return this;
    }

    public PlayerBuilder withCardsWithRanks(FrenchRank... ranks) {
        this.hand = HandBuilder.aHand()
                .withCards(
                        Arrays.stream(ranks)
                                .map(rank -> aCard().withRank(rank).build())
                                .toList()
                                .toArray(new Card[]{})
                )
                .build();

        return this;
    }

    public PlayerBuilder withEmptyHand() {
        this.hand = HandBuilder.aHand().withNoCards().build();
        return this;
    }

    public PlayerBuilder withNonEmptyHand() {
        this.hand = HandBuilder.aHand().withCards(Collections.singletonList(anyCard())).build();
        return this;
    }

    public Player build() {
        return new Player(id, hand);
    }
}
