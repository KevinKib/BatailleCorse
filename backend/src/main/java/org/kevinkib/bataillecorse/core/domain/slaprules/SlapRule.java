package org.kevinkib.bataillecorse.core.domain.slaprules;

import org.kevinkib.bataillecorse.core.domain.CentralPile;

public interface SlapRule {

    public boolean applies(CentralPile pile);

}
