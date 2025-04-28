package org.kevinkib.bataillecorse.domain;

public class IndexHandler {

    private Integer index;
    private final Integer nbPlayers;
    private final CentralPile pile;

    public IndexHandler(Integer defaultIndex, Integer nbPlayers, CentralPile pile) {
        this.index = defaultIndex;
        this.nbPlayers = nbPlayers;
        this.pile = pile;
    }

    public Integer update() {
        if (pile.isHonourState() && !pile.isLastCardHonourCard()) {
            return index;
        }

        index += 1;
        if (index.equals(nbPlayers)) {
            index = 0;
        }

        return index;
    }

    public void setCurrentPlayer(int currentPlayer) {
        index = currentPlayer;
    }

    public Integer getCurrentPlayer() {
        return index;
    }

}
