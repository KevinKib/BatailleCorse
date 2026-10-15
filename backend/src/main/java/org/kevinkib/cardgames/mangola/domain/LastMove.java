package org.kevinkib.cardgames.mangola.domain;

import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;

/** The last move played, kept so that a client reconnecting mid-animation can still show it. */
public record LastMove(PlayerId seat, int pit, List<SowingStep> trace) {

    public LastMove {
        trace = List.copyOf(trace);
    }
}
