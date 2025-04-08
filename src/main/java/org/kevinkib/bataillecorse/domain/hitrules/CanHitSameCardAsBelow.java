package org.kevinkib.bataillecorse.domain.hitrules;

import org.kevinkib.bataillecorse.domain.CentralPile;

public class CanHitSameCardAsBelow implements HitRule {

    @Override
    public boolean applies(CentralPile pile) {
        if (pile.getSize() < 2) {
            return false;
        }

        return pile.getCardByIndex(0).equals(pile.getCardByIndex(1));
    }
}
