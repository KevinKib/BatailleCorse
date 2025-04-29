package org.kevinkib.bataillecorse.domain;

import org.kevinkib.cards.domain.Hand;
import org.kevinkib.cards.testhelpers.CardFixtures;
import org.kevinkib.cards.testhelpers.HandBuilder;

import java.util.Arrays;
import java.util.Collections;

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
