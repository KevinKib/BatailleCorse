package org.kevinkib.bataillecorse.domain.slaprules;

import org.kevinkib.bataillecorse.domain.CentralPile;

public class CanSlapSandwich implements SlapRule {

    @Override
    public boolean applies(CentralPile pile) {
        if (pile.getSize() < 3) {
            return false;
        }

        return pile.getCard(0).isSameRankAs(pile.getCard(2));
    }
}
