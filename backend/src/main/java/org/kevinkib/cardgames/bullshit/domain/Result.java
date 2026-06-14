package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cardgames.bullshit.domain.player.Player;

public record Result(Player winningPlayer) {

    public static final Result ONGOING = new Result(null);

    public boolean isFinished() {
        return winningPlayer != null;
    }
}
