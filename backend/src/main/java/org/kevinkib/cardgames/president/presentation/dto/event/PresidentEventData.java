package org.kevinkib.cardgames.president.presentation.dto.event;

import org.kevinkib.cardgames.president.domain.event.CardsGivenBack;
import org.kevinkib.cardgames.president.domain.event.ExchangeSeen;
import org.kevinkib.cardgames.president.domain.event.Played;
import org.kevinkib.cardgames.president.domain.event.PlayerOut;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.event.Ready;
import org.kevinkib.cardgames.president.domain.event.TrickWon;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cardgames.presentation.dto.event.EventData;

import java.util.List;

/**
 * Public description of what a command did. {@code outForbidden} is set when the command made the seat go out. Only
 * the cards played are named; a give back carries a count and
 * never its cards. {@code action} is the command even when the broadcast event is a ROUND_END or GAME_OVER
 * caused by it.
 */
public record PresidentEventData(
        int seat,
        String action,
        List<CardDto> cards,
        Integer count,
        Integer outPlace,
        Boolean outForbidden,
        Integer trickWinner,
        String trickEndReason,
        Boolean barrierOpened) implements EventData {

    /** What the events of one command say: who did what, and the going out and trick end it caused. */
    public static PresidentEventData from(PresidentEventType action, int seat, List<PresidentEvent> events) {
        List<CardDto> cards = null;
        Integer count = null;
        Integer outPlace = null;
        Boolean outForbidden = null;
        Integer trickWinner = null;
        String trickEndReason = null;
        Boolean barrierOpened = null;
        for (PresidentEvent event : events) {
            if (event instanceof Played e) {
                cards = e.cards().stream().map(CardDto::from).toList();
            } else if (event instanceof PlayerOut e) {
                // a forbidden finisher has no place yet: they are placed last when the round ends
                outPlace = e.forbidden() ? null : e.place();
                outForbidden = e.forbidden();
            } else if (event instanceof TrickWon e) {
                trickWinner = e.seat().id();
                trickEndReason = e.reason().name();
            } else if (event instanceof CardsGivenBack e) {
                count = e.count();
            } else if (event instanceof Ready e) {
                barrierOpened = e.opened();
            } else if (event instanceof ExchangeSeen e) {
                barrierOpened = e.playBegins();
            }
        }
        return new PresidentEventData(seat, action.name(), cards, count, outPlace, outForbidden, trickWinner,
                trickEndReason, barrierOpened);
    }

    /** Whether the events of a ready opened the barrier, so the next round started. */
    public static boolean opened(List<PresidentEvent> events) {
        return events.stream().anyMatch(e -> e instanceof Ready r && r.opened());
    }
}
