package org.kevinkib.bataillecorse.domain.hitrules;

import org.kevinkib.cards.domain.Pile;

public class CanHitSameCardAsBelow implements HitRule {

    @Override
    public boolean applies(Pile pile) {
        if (pile.getSize() < 2) {
            return false;
        }

        return pile.seeCardByIndex(0).equals(pile.seeCardByIndex(1));
    }
}
