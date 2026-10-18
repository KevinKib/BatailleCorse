package org.kevinkib.cardgames.threemenmorris.domain;

import org.kevinkib.cardgames.game.PlayerId;

/** Outcome of the game: ongoing, or finished with its winner (null for a draw) and the reason. */
public record MorrisResult(PlayerId winner, Reason reason) {

    public enum Reason { LINE, BLOCKED, FORFEIT, DRAW_AGREED, DRAW_REPETITION, REDUCED, DRAW_MOVE_LIMIT }

    public static final MorrisResult ONGOING = new MorrisResult(null, null);

    public boolean isFinished() {
        return reason != null;
    }

    public boolean isDraw() {
        return reason == Reason.DRAW_AGREED || reason == Reason.DRAW_REPETITION
                || reason == Reason.DRAW_MOVE_LIMIT;
    }
}
