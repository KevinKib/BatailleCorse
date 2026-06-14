package org.kevinkib.cardgames.bataillecorse.domain;

import java.util.List;
import java.util.stream.IntStream;

public class IndexHandler {

    private Integer index;
    private final List<Player> players;
    private final CentralPile pile;

    public IndexHandler(Integer defaultIndex, List<Player> players, CentralPile pile) {
        this.index = defaultIndex;
        this.players = players;
        this.pile = pile;
    }

    public Integer update() {
        if (pile.isHonourState() && !pile.isLastCardHonourCard()) {
            if (!players.get(index).hasAnyCards()) {
                index = getIndexOfNextPlayerWithCards(index);
            }

            return index;
        }

        index = getIndexOfNextPlayerWithCards(index);

        return index;
    }

    public void setCurrentPlayer(int currentPlayer) {
        index = currentPlayer;
    }

    public Integer getCurrentPlayer() {
        return index;
    }

    private Integer getIndexOfNextPlayerWithCards(Integer index) {
        List<Integer> nextIndexes = IntStream.range(0, getNbPlayers())
                .boxed()
                .map(playerIndex -> (playerIndex + index + 1) % getNbPlayers())
                .toList();

        for (Integer currentIndex : nextIndexes) {
            if (players.get(currentIndex).hasAnyCards()) {
                return currentIndex;
            }
        }

        return null;
    }

    private Integer getNbPlayers() {
        return players.size();
    }

}
