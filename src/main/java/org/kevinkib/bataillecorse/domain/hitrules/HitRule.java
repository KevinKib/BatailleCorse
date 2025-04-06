package org.kevinkib.bataillecorse.domain.hitrules;

import org.kevinkib.cards.domain.Pile;

public interface HitRule {

    public boolean applies(Pile pile);

}
