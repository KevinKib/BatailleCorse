package org.kevinkib.cardgames.threemenmorris.domain;

import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;

/** What an accepted command did, returned by the aggregate and mapped to a WebSocket event by the presentation layer. */
public sealed interface MorrisEvent {

    /** {@code mill} holds the three points of the mill the placement closed, empty when it closed none. */
    record Placed(PlayerId seat, int point, List<Integer> mill) implements MorrisEvent {

        public Placed {
            Move.requireNotNegative(point);
            mill = mill == null ? List.of() : List.copyOf(mill);
        }

        public Placed(PlayerId seat, int point) {
            this(seat, point, List.of());
        }
    }

    record DrawOffered(PlayerId seat) implements MorrisEvent {
    }

    record DrawDeclined(PlayerId seat) implements MorrisEvent {
    }

    record DrawAccepted(PlayerId seat) implements MorrisEvent {
    }

    /** {@code mill} holds the three points of the mill the move closed, empty when it closed none. */
    record Moved(PlayerId seat, int from, int to, List<Integer> mill) implements MorrisEvent {

        public Moved {
            Move.requireNotNegative(from);
            Move.requireNotNegative(to);
            mill = mill == null ? List.of() : List.copyOf(mill);
        }

        public Moved(PlayerId seat, int from, int to) {
            this(seat, from, to, List.of());
        }
    }

    /** The seat took the opponent piece standing on {@code point}. */
    record Removed(PlayerId seat, int point) implements MorrisEvent {

        public Removed {
            Move.requireNotNegative(point);
        }
    }
}
