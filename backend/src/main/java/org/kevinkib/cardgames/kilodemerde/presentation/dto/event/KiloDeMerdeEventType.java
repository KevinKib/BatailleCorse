package org.kevinkib.cardgames.kilodemerde.presentation.dto.event;

import org.kevinkib.cardgames.kilodemerde.domain.event.FalseSlap;
import org.kevinkib.cardgames.kilodemerde.domain.event.Flushed;
import org.kevinkib.cardgames.kilodemerde.domain.event.GameFinished;
import org.kevinkib.cardgames.kilodemerde.domain.event.KiloDeMerdeEvent;
import org.kevinkib.cardgames.kilodemerde.domain.event.KiloDrawn;
import org.kevinkib.cardgames.kilodemerde.domain.event.PassChosen;
import org.kevinkib.cardgames.kilodemerde.domain.event.RaceOpened;
import org.kevinkib.cardgames.kilodemerde.domain.event.ReadyMarked;
import org.kevinkib.cardgames.kilodemerde.domain.event.RoundDealt;
import org.kevinkib.cardgames.kilodemerde.domain.event.RoundOver;
import org.kevinkib.cardgames.kilodemerde.domain.event.Slapped;
import org.kevinkib.cardgames.kilodemerde.domain.event.StepResolved;

/** What the table is told about, one broadcast per domain event of a command, in the order they happened. */
public enum KiloDeMerdeEventType {
    START, ROUND_START, PASS_CHOSEN, STEP, RACE, SLAP, FALSE_SLAP, DRAW, FLUSH, ROUND_OVER, GAME_OVER, READY;

    /** The type announcing a domain event; null for a forfeit, which the lifecycle broadcaster announces. */
    public static KiloDeMerdeEventType of(KiloDeMerdeEvent event) {
        if (event instanceof RoundDealt) {
            return ROUND_START;
        } else if (event instanceof PassChosen) {
            return PASS_CHOSEN;
        } else if (event instanceof StepResolved) {
            return STEP;
        } else if (event instanceof RaceOpened) {
            return RACE;
        } else if (event instanceof Slapped) {
            return SLAP;
        } else if (event instanceof FalseSlap) {
            return FALSE_SLAP;
        } else if (event instanceof KiloDrawn) {
            return DRAW;
        } else if (event instanceof Flushed) {
            return FLUSH;
        } else if (event instanceof RoundOver) {
            return ROUND_OVER;
        } else if (event instanceof GameFinished) {
            return GAME_OVER;
        } else if (event instanceof ReadyMarked) {
            return READY;
        }
        return null;
    }

    @Override
    public String toString() {
        return name();
    }
}
