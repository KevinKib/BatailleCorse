package org.kevinkib.cardgames.mangola.presentation.dto.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.kevinkib.cardgames.mangola.domain.MangolaEvent;
import org.kevinkib.cardgames.mangola.presentation.dto.MangolaDto;
import org.kevinkib.cardgames.presentation.dto.event.EventData;

import java.util.List;

/**
 * Public description of what a command did. {@code action} is the command (ARRANGE, CONFIRM_LAYOUT,
 * EDIT_LAYOUT, SOW) even when the broadcast event is the GAME_OVER it caused. A layout event carries the seat
 * and nothing else: never the counts, so the other seat learns only that a draft changed. The reveal has no
 * seat. A move carries its pit, its whole trace and the seeds captured, for the clients' animation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MangolaEventData(Integer seat, String action, Integer pit, List<MangolaDto.StepDto> trace,
                               Integer captured) implements EventData {

    public static final String ARRANGE = "ARRANGE";
    public static final String CONFIRM_LAYOUT = "CONFIRM_LAYOUT";
    public static final String EDIT_LAYOUT = "EDIT_LAYOUT";
    public static final String SOW = "SOW";

    public static MangolaEventData from(MangolaEvent event) {
        if (event instanceof MangolaEvent.LayoutChanged e) {
            return new MangolaEventData(e.seat().id(), ARRANGE, null, null, null);
        }
        if (event instanceof MangolaEvent.LayoutConfirmed e) {
            return new MangolaEventData(e.seat().id(), CONFIRM_LAYOUT, null, null, null);
        }
        if (event instanceof MangolaEvent.LayoutWithdrawn e) {
            return new MangolaEventData(e.seat().id(), EDIT_LAYOUT, null, null, null);
        }
        if (event instanceof MangolaEvent.LayoutsRevealed) {
            return new MangolaEventData(null, CONFIRM_LAYOUT, null, null, null);
        }
        if (event instanceof MangolaEvent.Sown e) {
            return new MangolaEventData(e.seat().id(), SOW, e.pit(),
                    e.trace().stream().map(MangolaDto.StepDto::from).toList(), e.captured());
        }
        throw new IllegalArgumentException("Unknown event " + event);
    }

    public static MangolaEventType typeOf(MangolaEvent event) {
        if (event instanceof MangolaEvent.LayoutChanged) {
            return MangolaEventType.LAYOUT_CHANGED;
        }
        if (event instanceof MangolaEvent.LayoutConfirmed) {
            return MangolaEventType.LAYOUT_CONFIRMED;
        }
        if (event instanceof MangolaEvent.LayoutWithdrawn) {
            return MangolaEventType.LAYOUT_WITHDRAWN;
        }
        if (event instanceof MangolaEvent.LayoutsRevealed) {
            return MangolaEventType.LAYOUTS_REVEALED;
        }
        if (event instanceof MangolaEvent.Sown) {
            return MangolaEventType.SOWN;
        }
        throw new IllegalArgumentException("Unknown event " + event);
    }
}
