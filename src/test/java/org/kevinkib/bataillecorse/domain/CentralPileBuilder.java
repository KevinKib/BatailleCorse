package org.kevinkib.bataillecorse.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.Pile;
import org.kevinkib.cards.domain.french.FrenchRank;
import org.kevinkib.cards.testhelpers.PileFixtures;

public final class CentralPileBuilder {
    private Pile pile;
    private CentralPileState state;

    private CentralPileBuilder() {
    }

    public static CentralPileBuilder aCentralPile() {
        return new CentralPileBuilder();
    }

    public CentralPileBuilder withPile(Pile pile) {
        this.pile = pile;
        return this;
    }

    public CentralPileBuilder withCards(Card... cards) {
        this.pile = PileFixtures.createPileWithCard(cards);
        return this;
    }

    public CentralPileBuilder withCardsWithRanks(FrenchRank... ranks) {
        this.pile = PileFixtures.createPileWithRank(ranks);
        return this;
    }

    public CentralPileBuilder withNumberOfCards(int nbCards) {
        this.pile = PileFixtures.createPileWithNumberOfCards(nbCards);
        return this;
    }

    public CentralPileBuilder withNoCards() {
        this.pile = PileFixtures.createEmptyPile();
        return this;
    }

    public CentralPileBuilder withState(CentralPileState state) {
        this.state = state;
        return this;
    }

    public CentralPile build() {
        return new CentralPile(pile, state);
    }
}
