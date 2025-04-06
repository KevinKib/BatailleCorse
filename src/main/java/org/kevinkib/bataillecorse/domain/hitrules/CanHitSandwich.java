package org.kevinkib.bataillecorse.domain.hitrules;

import org.kevinkib.cards.domain.Pile;

public class CanHitSandwich implements HitRule {

    @Override
    public boolean applies(Pile pile) {
        if (pile.getSize() < 3) {
            return false;
        }

        return pile.getCard(0).equals(pile.getCard(2));
    }
}
