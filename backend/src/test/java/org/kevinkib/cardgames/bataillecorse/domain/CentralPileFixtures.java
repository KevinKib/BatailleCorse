package org.kevinkib.cardgames.bataillecorse.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.testhelpers.CardBuilder;
import org.kevinkib.cards.testhelpers.PileFixtures;

import java.util.Collections;
import java.util.List;

import static org.kevinkib.cardgames.bataillecorse.domain.CentralPileState.NEUTRAL;

public class CentralPileFixtures {

    public static CentralPile createEmptyCentralPile() {
        return CentralPileBuilder.aCentralPile()
                .withPile(PileFixtures.createEmptyPile())
                .withState(NEUTRAL)
                .build();
    }

    public static CentralPile createWithState(CentralPileState state) {
        return CentralPileBuilder.aCentralPile()
                .withPile(PileFixtures.createEmptyPile())
                .withState(state)
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

    public static CentralPile createCentralPileGrabbableByPlayer(Player player) {
        CentralPile pile = CentralPileFixtures.createEmptyCentralPile();
        Player otherPlayer = PlayerBuilder.aPlayer().build();
        Card jackCard = CardBuilder.aCard().withRank(FrenchRank.JACK).build();
        Card otherCard = CardBuilder.aCard().build();

        try {
            pile.add(jackCard, player);
            pile.add(otherCard, otherPlayer);
        } catch (FullCentralPileException e) {
            throw new IllegalStateException("Should not have central pile be full during fixture instanciation");
        }

        return pile;
    }

    public static CentralPile createCentralPileThenAddCards(Card... cards) {
        CentralPile pile = createEmptyCentralPile();

        try {
            for (int index = cards.length - 1; index >= 0; --index) {
                pile.add(cards[index], null);
            }
        } catch (FullCentralPileException e) {
            throw new IllegalStateException("Should not reach full central pile exception in fixture building");
        }

        return pile;
    }

}
