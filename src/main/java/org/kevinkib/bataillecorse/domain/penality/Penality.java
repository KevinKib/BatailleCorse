package org.kevinkib.bataillecorse.domain.penality;

import org.kevinkib.bataillecorse.domain.CentralPile;
import org.kevinkib.bataillecorse.domain.Player;

public interface Penality {

    public void apply(Player penalizedPlayer, CentralPile pile);

}
