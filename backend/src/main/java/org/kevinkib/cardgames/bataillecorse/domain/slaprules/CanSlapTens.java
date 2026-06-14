package org.kevinkib.cardgames.bataillecorse.domain.slaprules;

import org.kevinkib.cardgames.bataillecorse.domain.CentralPile;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

public class CanSlapTens implements SlapRule {

    @Override
    public boolean applies(CentralPile pile) {
        if (pile.isEmpty()) {
            return false;
        }

        return FrenchRank.TEN == pile.getCardOnTop().getRank();
    }
}
