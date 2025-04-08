package org.kevinkib.bataillecorse.domain.hitrules;

import org.kevinkib.bataillecorse.domain.CentralPile;
import org.kevinkib.cards.domain.Pile;

public class CanHitSandwich implements HitRule {

    @Override
    public boolean applies(CentralPile pile) {
        if (pile.getSize() < 3) {
            return false;
        }

        return pile.getCard(0).equals(pile.getCard(2));
    }
}
