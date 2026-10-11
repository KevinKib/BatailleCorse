package org.kevinkib.cardgames.threemenmorris.domain;

import org.kevinkib.cardgames.game.PlayerId;

/** Outcome of the game: ongoing, or finished with its winner and the reason. */
public record MorrisResult(PlayerId winner, Reason reason) {

    public enum Reason { LINE, BLOCKED, FORFEIT }

    public static final MorrisResult ONGOING = new MorrisResult(null, null);

    public boolean isFinished() {
        return reason != null;
    }
}
