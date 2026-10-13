package org.kevinkib.cardgames.threemenmorris.domain;

import org.kevinkib.cardgames.game.PlayerId;

/** What an accepted command did, returned by the aggregate and mapped to a WebSocket event by the presentation layer. */
public sealed interface MorrisEvent {

    record Placed(PlayerId seat, int point) implements MorrisEvent {

        public Placed {
            Move.requireNotNegative(point);
        }
    }

    record Moved(PlayerId seat, int from, int to) implements MorrisEvent {

        public Moved {
            Move.requireNotNegative(from);
            Move.requireNotNegative(to);
        }
    }
}
