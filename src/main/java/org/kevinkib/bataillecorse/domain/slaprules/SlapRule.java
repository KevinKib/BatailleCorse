package org.kevinkib.bataillecorse.domain.slaprules;

import org.kevinkib.bataillecorse.domain.CentralPile;

public interface SlapRule {

    public boolean applies(CentralPile pile);

}
