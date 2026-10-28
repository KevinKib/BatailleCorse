package org.kevinkib.cardgames.draughts.domain;

import org.kevinkib.cardgames.game.PlayerId;

/** Outcome of the game: ongoing, or finished with its winner (null for a draw) and the reason. */
public record DraughtsResult(PlayerId winner, Reason reason) {

    public enum Reason {
        NO_PIECES, BLOCKED, FORFEIT, DRAW_AGREED, DRAW_REPETITION, DRAW_KINGS_ONLY, DRAW_ENDGAME
    }

    public static final DraughtsResult ONGOING = new DraughtsResult(null, null);

    public boolean isFinished() {
        return reason != null;
    }

    public boolean isDraw() {
        return reason == Reason.DRAW_AGREED || reason == Reason.DRAW_REPETITION
                || reason == Reason.DRAW_KINGS_ONLY || reason == Reason.DRAW_ENDGAME;
    }
}
