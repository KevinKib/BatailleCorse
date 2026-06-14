package org.kevinkib.cardgames.bullshit.domain;

public record Result(Player winningPlayer) {

    public static final Result ONGOING = new Result(null);

    public boolean isFinished() {
        return winningPlayer != null;
    }
}
