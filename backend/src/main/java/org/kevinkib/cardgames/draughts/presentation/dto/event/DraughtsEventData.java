package org.kevinkib.cardgames.draughts.presentation.dto.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.kevinkib.cardgames.draughts.domain.DraughtsEvent;
import org.kevinkib.cardgames.draughts.domain.DraughtsMove;
import org.kevinkib.cardgames.presentation.dto.event.EventData;

/**
 * Public description of what a command did. A move carries {@code from}, {@code to}, {@code path} (the landing
 * squares, ending with {@code to}), the {@code captures} in capture order and {@code promotes}; the draw events carry
 * the {@code seat} alone, so the other fields are left out of their JSON.
 */
public record DraughtsEventData(
        int seat,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer from,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer to,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<Integer> path,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<Integer> captures,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean promotes) implements EventData {

    public static DraughtsEventData from(DraughtsEvent event) {
        if (event instanceof DraughtsEvent.Moved e) {
            DraughtsMove move = e.move();
            return new DraughtsEventData(e.seat().id(), move.from(), move.to(), move.path(), move.captures(),
                    move.promotes());
        }
        if (event instanceof DraughtsEvent.DrawOffered e) {
            return seatOnly(e.seat().id());
        }
        if (event instanceof DraughtsEvent.DrawDeclined e) {
            return seatOnly(e.seat().id());
        }
        if (event instanceof DraughtsEvent.DrawAccepted e) {
            return seatOnly(e.seat().id());
        }
        throw new IllegalArgumentException("Unknown event " + event);
    }

    public static DraughtsEventType typeOf(DraughtsEvent event) {
        if (event instanceof DraughtsEvent.Moved) {
            return DraughtsEventType.MOVE;
        }
        if (event instanceof DraughtsEvent.DrawOffered) {
            return DraughtsEventType.DRAW_OFFERED;
        }
        if (event instanceof DraughtsEvent.DrawDeclined) {
            return DraughtsEventType.DRAW_DECLINED;
        }
        if (event instanceof DraughtsEvent.DrawAccepted) {
            return DraughtsEventType.GAME_OVER;
        }
        throw new IllegalArgumentException("Unknown event " + event);
    }

    private static DraughtsEventData seatOnly(int seat) {
        return new DraughtsEventData(seat, null, null, null, null, null);
    }
}
