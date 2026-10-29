package org.kevinkib.cardgames.mangola.domain;

import org.kevinkib.cardgames.game.PlayerId;

/**
 * Outcome of the game: ongoing, or finished with its winner (null for a draw) and the reason. NO_MOVE: the
 * player to move has none; CANNOT_MOVE_AGAIN: the winner's opponent has just moved and can never move again.
 */
public record MangolaResult(PlayerId winner, Reason reason) {

    public enum Reason { NO_MOVE, CANNOT_MOVE_AGAIN, FORFEIT, REPETITION, ENDLESS_SOWING }

    public static final MangolaResult ONGOING = new MangolaResult(null, null);

    public boolean isFinished() {
        return reason != null;
    }

    public boolean isDraw() {
        return reason == Reason.REPETITION || reason == Reason.ENDLESS_SOWING;
    }
}
