package org.kevinkib.bataillecorse.core.domain.penality;

import org.kevinkib.bataillecorse.core.domain.CentralPile;
import org.kevinkib.bataillecorse.core.domain.Player;

public interface Penality {

    public void apply(Player penalizedPlayer, CentralPile pile);

}
