package org.kevinkib.bataillecorse.domain.hitrules;

import org.kevinkib.bataillecorse.domain.CentralPile;
import org.kevinkib.cards.domain.Pile;

public interface HitRule {

    public boolean applies(CentralPile pile);

}
