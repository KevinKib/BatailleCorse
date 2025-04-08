package org.kevinkib.bataillecorse.domain;

import org.kevinkib.cards.domain.Pile;

public final class CentralPileBuilder {
    private Pile pile;
    private CentralPileState state;

    private CentralPileBuilder() {
    }

    public static CentralPileBuilder aCentralPile() {
        return new CentralPileBuilder();
    }

    public CentralPileBuilder withPile(Pile pile) {
        this.pile = pile;
        return this;
    }

    public CentralPileBuilder withState(CentralPileState state) {
        this.state = state;
        return this;
    }

    public CentralPile build() {
        return new CentralPile(pile, state);
    }
}
