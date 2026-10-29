package org.kevinkib.cardgames.kilodemerde.presentation;

import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.domain.Phase;

/**
 * Identifies one wait for the seats to be ready: before the first round or at the end of a given round (a forfeit
 * re-forms the table but keeps the wait, and its countdown). Keys the ready timeout and the
 * bots' ready tasks; never {@code version()}, which every confirmation bumps.
 */
public record KiloDeMerdeReadyKey(Phase phase, int round) {

    /** The key of the wait the game is in, null when it is not waiting; read it under the monitor of the game. */
    public static KiloDeMerdeReadyKey of(KiloDeMerde game) {
        synchronized (game) {
            Phase phase = game.phase();
            if (phase != Phase.AWAITING_START && phase != Phase.ROUND_OVER) {
                return null;
            }
            return new KiloDeMerdeReadyKey(phase, game.round());
        }
    }
}
