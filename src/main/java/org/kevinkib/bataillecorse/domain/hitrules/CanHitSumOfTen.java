package org.kevinkib.bataillecorse.domain.hitrules;

import org.kevinkib.cards.domain.Pile;
import org.kevinkib.cards.domain.french.FrenchRank;

public class CanHitSumOfTen implements HitRule {

    @Override
    public boolean applies(Pile pile) {
        if (pile.getSize() < 2) {
            return false;
        }

        FrenchRank firstRank = (FrenchRank) pile.getCard(0).getRank();
        FrenchRank secondRank = (FrenchRank) pile.getCard(1).getRank();

        return firstRank.sum(secondRank).equals(10);
    }
}
