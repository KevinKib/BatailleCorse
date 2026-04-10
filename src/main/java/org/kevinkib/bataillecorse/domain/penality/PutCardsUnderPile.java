package org.kevinkib.bataillecorse.domain.penality;

import org.kevinkib.bataillecorse.domain.CentralPile;
import org.kevinkib.bataillecorse.domain.Player;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.hand.NoCardsException;

public class PutCardsUnderPile implements Penality {

    private final int givenCards;

    public PutCardsUnderPile(int givenCards) {
        this.givenCards = givenCards;
    }

    @Override
    public void apply(Player penalizedPlayer, CentralPile pile) {

        int actualAmountOfGivenCards = Math.min(givenCards, penalizedPlayer.getHandSize());

        try {
            for (int i = 0; i < actualAmountOfGivenCards; ++i) {
                Card card = penalizedPlayer.removeCardOnTop();
                pile.addBelowForPenality(card);
            }
        } catch (NoCardsException e) {
            throw new IllegalStateException("Should never reach no cards exception during PutCardsUnderPile penality.");
        }
    }

}
