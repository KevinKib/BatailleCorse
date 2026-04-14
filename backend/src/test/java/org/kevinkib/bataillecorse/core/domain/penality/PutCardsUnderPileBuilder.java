package org.kevinkib.bataillecorse.core.domain.penality;

public final class PutCardsUnderPileBuilder {
    private int givenCards;

    private PutCardsUnderPileBuilder() {
    }

    public static PutCardsUnderPileBuilder aPutCardsUnderPilePenality() {
        return new PutCardsUnderPileBuilder();
    }

    public PutCardsUnderPileBuilder withGivenCards(int givenCards) {
        this.givenCards = givenCards;
        return this;
    }

    public PutCardsUnderPile build() {
        return new PutCardsUnderPile(givenCards);
    }
}
