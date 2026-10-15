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

    /** A seat's draft changed. It deliberately carries no counts: the other seat must not learn them. */
    record LayoutChanged(PlayerId seat) implements MangolaEvent {
    }

    /** A seat is ready. */
    record LayoutConfirmed(PlayerId seat) implements MangolaEvent {
    }

    /** A seat took its confirmation back and is arranging again. */
    record LayoutWithdrawn(PlayerId seat) implements MangolaEvent {
    }

    /** Both seats were ready: the layouts are on the board and the play starts. */
    record LayoutsRevealed() implements MangolaEvent {
    }
}
