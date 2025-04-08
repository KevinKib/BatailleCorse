package org.kevinkib.bataillecorse.domain.hitrules;

import org.kevinkib.bataillecorse.domain.CentralPile;
import org.kevinkib.cards.domain.french.FrenchRank;

public class CanHitTens implements HitRule {

    @Override
    public boolean applies(CentralPile pile) {
        if (pile.isEmpty()) {
            return false;
        }

        return FrenchRank.TEN == pile.getCardOnTop().getRank();
    }
}
