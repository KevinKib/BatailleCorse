package org.kevinkib.cardgames.bataillecorse.domain.slaprules;

import org.kevinkib.cardgames.bataillecorse.domain.CentralPile;

public class CanSlapSameCardAsBelow implements SlapRule {

    @Override
    public boolean applies(CentralPile pile) {
        if (pile.getSize() < 2) {
            return false;
        }

        return pile.getCardByIndex(0).isSameRankAs(pile.getCardByIndex(1));
    }
}
