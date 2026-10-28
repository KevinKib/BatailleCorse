package org.kevinkib.cardgames.kilodemerde.presentation;

import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;

/**
 * Identifies one pass step of one deal: the round, the step and the number of seats still playing. A voided round
 * (a forfeit) keeps its round number and restarts at step 1, but it re-forms the table with one seat fewer, so the
 * number of seats tells the old deal from the new one. These domain counters, never {@code version()}, key the pass
 * timer and the bots' pass and own-set slap tasks: every choice and every slap bumps the version, which would drop
 * a task that is still valid.
 */
public record KiloDeMerdeStepKey(int round, int step, int seats) {

    /** The key of the step being played; read it under the monitor of the game. */
    public static KiloDeMerdeStepKey of(KiloDeMerde game) {
        synchronized (game) {
            return new KiloDeMerdeStepKey(game.round(), game.step(), game.activeSeats().size());
        }
    }
}
