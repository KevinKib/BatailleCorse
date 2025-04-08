package org.kevinkib.bataillecorse.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.french.FrenchRank;
import org.kevinkib.cards.testhelpers.PileFixtures;

import java.util.Collections;
import java.util.List;

import static org.kevinkib.bataillecorse.domain.CentralPileState.NEUTRAL;

public class CentralPileFixtures {

    public static CentralPile createEmptyCentralPile() {
        return CentralPileBuilder.aCentralPile()
                .withPile(PileFixtures.createEmptyPile())
                .withState(NEUTRAL)
                .build();
    }

    public static CentralPile createCentralPileWithCardAndState(Card card, CentralPileState state) {
        return createCentralPileWithCardsAndState(Collections.singletonList(card), state);
    }

    public static CentralPile createCentralPileWithCardsAndState(List<Card> cards, CentralPileState state) {
        Card[] cardsArr = cards.toArray(new Card[0]);

        return CentralPileBuilder.aCentralPile()
                .withPile(PileFixtures.createPileWithCard(cardsArr))
                .withState(state)
                .build();
    }

    public static CentralPile createCentralPileWithNumberOfCards(int nbCards) {
        return createCentralPileWithNumberOfCardsAndState(nbCards, NEUTRAL);
    }

    public static CentralPile createCentralPileWithNumberOfCardsAndState(int nbCards, CentralPileState state) {
        return CentralPileBuilder.aCentralPile()
                .withPile(PileFixtures.createPileWithNumberOfCards(nbCards))
                .withState(state)
                .build();
    }

    public static CentralPile createCentralPileWithRankAndState(FrenchRank rank, CentralPileState state) {
        return createCentralPileWithRanksAndState(Collections.singletonList(rank), state);
    }

    public static CentralPile createCentralPileWithRanksAndState(List<FrenchRank> ranks, CentralPileState state) {
        FrenchRank[] rankArr = ranks.toArray(new FrenchRank[0]);

        return CentralPileBuilder.aCentralPile()
                .withPile(PileFixtures.createPileWithRank(rankArr))
                .withState(state)
                .build();
    }


}
