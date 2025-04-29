package org.kevinkib.bataillecorse.domain;

public final class ResultBuilder {
    private Player winningPlayer;

    private ResultBuilder() {
    }

    public static ResultBuilder aResult() {
        return new ResultBuilder();
    }

    public ResultBuilder withWinningPlayer(Player winningPlayer) {
        this.winningPlayer = winningPlayer;
        return this;
    }

    public Result build() {
        return new Result(winningPlayer);
    }
}
