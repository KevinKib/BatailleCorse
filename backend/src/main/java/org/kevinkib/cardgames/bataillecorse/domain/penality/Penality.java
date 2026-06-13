package org.kevinkib.cardgames.bataillecorse.domain.penality;

import org.kevinkib.cardgames.bataillecorse.domain.CentralPile;
import org.kevinkib.cardgames.bataillecorse.domain.Player;

public interface Penality {

    public void apply(Player penalizedPlayer, CentralPile pile);

}
