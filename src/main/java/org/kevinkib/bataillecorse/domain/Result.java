package org.kevinkib.bataillecorse.domain;

import java.util.List;

public class Result {

    private final Player winningPlayer;

    public Result(Player winningPlayer) {
        this.winningPlayer = winningPlayer;
    }

    public Player getWinningPlayer() {
        return winningPlayer;
    }

    public static Result ONGOING = new Result(null);

    public static Result update(List<Player> players, CentralPile pile) {
        if (!pile.isEmpty()) {
            return Result.ONGOING;
        }

        List<Player> playersWithCards = players.stream().filter(Player::hasAnyCards).toList();

        if (playersWithCards.isEmpty()) {
            throw new IllegalStateException("Cannot have no players with no cards when pile is empty");
        }

        if (playersWithCards.size() == 1) {
            Player winningPlayer = playersWithCards.get(0);
            return new Result(winningPlayer);
        }
        else {
            return Result.ONGOING;
        }
    }

    public boolean isFinished() {
        return winningPlayer != null;
    }
}
