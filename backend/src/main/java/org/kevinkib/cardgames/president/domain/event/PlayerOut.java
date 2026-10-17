package org.kevinkib.cardgames.president.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/**
 * A player played their last card and takes finishing {@code place} (1 is the best). A {@code forbidden} finish (the
 * last play held a 2) places them last, below the others: the place is then unknown until the round ends and is 0.
 */
public record PlayerOut(PlayerId seat, int place, boolean forbidden) implements PresidentEvent {

    /** An ordinary going out. */
    public PlayerOut(PlayerId seat, int place) {
        this(seat, place, false);
    }
}
