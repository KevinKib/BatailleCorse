package org.kevinkib.bataillecorse.domain.hitrules;

import org.kevinkib.cards.domain.Pile;
import org.kevinkib.cards.domain.french.FrenchRank;

public class CanHitTens implements HitRule {

    @Override
    public boolean applies(Pile pile) {
        if (pile.isEmpty()) {
            return false;
        }

        return FrenchRank.TEN == pile.seeCardOnTop().getRank();
    }
}
