package org.kevinkib.cardgames.draughts.domain;

import org.kevinkib.cardgames.game.PlayerId;

/** What an accepted command did, returned by the aggregate and mapped to a WebSocket event by the presentation layer. */
public sealed interface DraughtsEvent {

    record Moved(PlayerId seat, DraughtsMove move) implements DraughtsEvent {
    }

    record DrawOffered(PlayerId seat) implements DraughtsEvent {
    }

    record DrawDeclined(PlayerId seat) implements DraughtsEvent {
    }

    record DrawAccepted(PlayerId seat) implements DraughtsEvent {
    }
}
