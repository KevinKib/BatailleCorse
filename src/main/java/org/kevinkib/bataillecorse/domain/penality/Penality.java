package org.kevinkib.bataillecorse.domain.penality;

import org.kevinkib.bataillecorse.domain.Player;
import org.kevinkib.cards.domain.Pile;

public interface Penality {

    public void apply(Player penalizedPlayer, Pile pile);

}
