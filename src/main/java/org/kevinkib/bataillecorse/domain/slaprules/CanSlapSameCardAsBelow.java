package org.kevinkib.bataillecorse.domain.slaprules;

import org.kevinkib.bataillecorse.domain.CentralPile;

public class CanSlapSameCardAsBelow implements SlapRule {

    @Override
    public boolean applies(CentralPile pile) {
        if (pile.getSize() < 2) {
            return false;
        }

        return pile.getCardByIndex(0).equals(pile.getCardByIndex(1));
    }
}
