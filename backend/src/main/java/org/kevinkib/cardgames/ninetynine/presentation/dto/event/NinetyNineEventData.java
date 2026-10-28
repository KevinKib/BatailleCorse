package org.kevinkib.cardgames.ninetynine.presentation.dto.event;

import org.kevinkib.cardgames.ninetynine.domain.event.Busted;
import org.kevinkib.cardgames.ninetynine.domain.event.Eliminated;
import org.kevinkib.cardgames.ninetynine.domain.event.NinetyNineEvent;
import org.kevinkib.cardgames.ninetynine.domain.event.Played;
import org.kevinkib.cardgames.ninetynine.domain.event.RoundDealt;
import org.kevinkib.cardgames.ninetynine.domain.event.StockReshuffled;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cardgames.presentation.dto.event.EventData;

import java.util.List;

/**
 * Public description of one thing that happened at the table. Only the fields of the event type are set, the others
 * are null: a PLAY has {@code seat}, {@code card}, {@code aceValue}, {@code totalAfter} and {@code reversed}; a BUST
 * {@code seat}, the stuck {@code hand} (public: it proves the bust) and {@code livesLeft}; an ELIMINATED {@code seat}
 * and {@code place}; a RESHUFFLE the new {@code stockSize}; a ROUND_START the {@code seat} that starts.
 */
public record NinetyNineEventData(
        Integer seat,
        CardDto card,
        Integer aceValue,
        Integer totalAfter,
        Boolean reversed,
        List<CardDto> hand,
        Integer livesLeft,
        Integer place,
        Integer stockSize) implements EventData {

    public static NinetyNineEventData from(NinetyNineEvent event) {
        if (event instanceof Played e) {
            return new NinetyNineEventData(e.seat().id(), CardDto.from(e.card()), e.aceValue(), e.totalAfter(),
                    e.reversed(), null, null, null, null);
        } else if (event instanceof StockReshuffled e) {
            return new NinetyNineEventData(null, null, null, null, null, null, null, null, e.size());
        } else if (event instanceof Busted e) {
            return new NinetyNineEventData(e.seat().id(), null, null, null, null,
                    e.hand().stream().map(CardDto::from).toList(), e.livesLeft(), null, null);
        } else if (event instanceof Eliminated e) {
            return new NinetyNineEventData(e.seat().id(), null, null, null, null, null, null, e.place(), null);
        } else if (event instanceof RoundDealt e) {
            return new NinetyNineEventData(e.starter().id(), null, null, null, null, null, null, null, null);
        }
        return new NinetyNineEventData(null, null, null, null, null, null, null, null, null);
    }
}
