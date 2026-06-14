package org.kevinkib.cardgames.bataillecorse.domain.slaprules;

import org.kevinkib.cardgames.bataillecorse.domain.CentralPile;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

public class CanSlapSumOfTen implements SlapRule {

    @Override
    public boolean applies(CentralPile pile) {
        if (pile.getSize() < 2) {
            return false;
        }

        FrenchRank firstRank = (FrenchRank) pile.getCard(0).getRank();
        FrenchRank secondRank = (FrenchRank) pile.getCard(1).getRank();

        return firstRank.sum(secondRank).equals(10);
    }
}
