package org.kevinkib.cardgames.bataillecorse.domain;

public enum CentralPileState {

    NEUTRAL(true),
    HONOUR_STATE(true),
    FULL(false);

    private final boolean canAddCards;

    CentralPileState(boolean canAddCards) {
        this.canAddCards = canAddCards;
    }

    public boolean isFull() {
        return !canAddCards;
    }

}
