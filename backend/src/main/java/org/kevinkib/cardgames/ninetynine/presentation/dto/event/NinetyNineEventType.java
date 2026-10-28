package org.kevinkib.cardgames.ninetynine.presentation.dto.event;

import org.kevinkib.cardgames.ninetynine.domain.event.Busted;
import org.kevinkib.cardgames.ninetynine.domain.event.Eliminated;
import org.kevinkib.cardgames.ninetynine.domain.event.GameFinished;
import org.kevinkib.cardgames.ninetynine.domain.event.NinetyNineEvent;
import org.kevinkib.cardgames.ninetynine.domain.event.Played;
import org.kevinkib.cardgames.ninetynine.domain.event.RoundDealt;
import org.kevinkib.cardgames.ninetynine.domain.event.StockReshuffled;

/** What the table is told about, one broadcast per domain event of a command, in the order they happened. */
public enum NinetyNineEventType {
    START, ROUND_START, PLAY, RESHUFFLE, BUST, ELIMINATED, GAME_OVER;

    /** The type announcing a domain event; null for a forfeit, which the lifecycle broadcaster announces. */
    public static NinetyNineEventType of(NinetyNineEvent event) {
        if (event instanceof Played) {
            return PLAY;
        } else if (event instanceof StockReshuffled) {
            return RESHUFFLE;
        } else if (event instanceof Busted) {
            return BUST;
        } else if (event instanceof Eliminated) {
            return ELIMINATED;
        } else if (event instanceof GameFinished) {
            return GAME_OVER;
        } else if (event instanceof RoundDealt) {
            return ROUND_START;
        }
        return null;
    }

    @Override
    public String toString() {
        return name();
    }
}
