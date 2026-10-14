package org.kevinkib.cardgames.threemenmorris.presentation.dto.event;

import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.threemenmorris.domain.Action;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisEvent;

/**
 * Public description of what a command did. {@code action} is the command
 * ({@link ThreeMenMorrisEventType}) even when the broadcast event is the GAME_OVER it caused.
 */
public record ThreeMenMorrisEventData(int seat, String action, Integer point, Integer from, Integer to)
        implements EventData {

    public static ThreeMenMorrisEventData from(MorrisEvent event) {
        if (event instanceof MorrisEvent.Placed e) {
            return new ThreeMenMorrisEventData(e.seat().id(), ThreeMenMorrisEventType.PLACE.name(),
                    e.point(), null, null);
        }
        if (event instanceof MorrisEvent.Moved e) {
            return new ThreeMenMorrisEventData(e.seat().id(), ThreeMenMorrisEventType.MOVE.name(),
                    null, e.from(), e.to());
        }
        if (event instanceof MorrisEvent.DrawOffered e) {
            return new ThreeMenMorrisEventData(e.seat().id(), Action.OFFER_DRAW.name(), null, null, null);
        }
        if (event instanceof MorrisEvent.DrawDeclined e) {
            return new ThreeMenMorrisEventData(e.seat().id(), Action.DECLINE_DRAW.name(), null, null, null);
        }
        if (event instanceof MorrisEvent.DrawAccepted e) {
            return new ThreeMenMorrisEventData(e.seat().id(), Action.ACCEPT_DRAW.name(), null, null, null);
        }
        throw new IllegalArgumentException("Unknown event " + event);
    }

    public static ThreeMenMorrisEventType typeOf(MorrisEvent event) {
        if (event instanceof MorrisEvent.Placed) {
            return ThreeMenMorrisEventType.PLACE;
        }
        if (event instanceof MorrisEvent.Moved) {
            return ThreeMenMorrisEventType.MOVE;
        }
        if (event instanceof MorrisEvent.DrawOffered) {
            return ThreeMenMorrisEventType.DRAW_OFFERED;
        }
        if (event instanceof MorrisEvent.DrawDeclined) {
            return ThreeMenMorrisEventType.DRAW_DECLINED;
        }
        if (event instanceof MorrisEvent.DrawAccepted) {
            return ThreeMenMorrisEventType.GAME_OVER;
        }
        throw new IllegalArgumentException("Unknown event " + event);
    }
}
