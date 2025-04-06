package org.kevinkib.bataillecorse.domain;

import org.kevinkib.cards.domain.Hand;

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
        this.hand = new Hand();
        return this;
    }

    public Player build() {
        return new Player(id, hand);
    }
}
