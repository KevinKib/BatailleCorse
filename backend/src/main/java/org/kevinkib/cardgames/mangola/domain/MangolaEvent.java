package org.kevinkib.cardgames.mangola.domain;

import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;

/** What an accepted command did, returned by the aggregate and mapped to a WebSocket event by the presentation layer. */
public sealed interface MangolaEvent {

    /** A move: the pit sown, the whole trace of the sowing and the seeds captured from the opponent. */
    record Sown(PlayerId seat, int pit, List<SowingStep> trace, int captured) implements MangolaEvent {

        public Sown {
            trace = List.copyOf(trace);
        }
    }
}
