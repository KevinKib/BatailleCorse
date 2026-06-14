package org.kevinkib.cardgames.bataillecorse.domain.slaprules;

import org.kevinkib.cardgames.bataillecorse.domain.CentralPile;

public interface SlapRule {

    public boolean applies(CentralPile pile);

}
