package org.kevinkib.bataillecorse.domain;

public class Result {

    private final Player winningPlayer;

    public Result(Player winningPlayer) {
        this.winningPlayer = winningPlayer;
    }

    public Player getWinningPlayer() {
        return winningPlayer;
    }

    public static Result ONGOING = new Result(null);

    public boolean isFinished() {
        return winningPlayer != null;
    }
}
