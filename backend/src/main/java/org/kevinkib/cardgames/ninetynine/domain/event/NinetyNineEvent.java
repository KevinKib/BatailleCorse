package org.kevinkib.cardgames.ninetynine.domain.event;

/** What a successful command changed, in order. The only hand an event reveals is a stuck hand (a bust). */
public sealed interface NinetyNineEvent
        permits RoundDealt, Played, Busted, Eliminated, StockReshuffled, GameFinished, Forfeited {
}
