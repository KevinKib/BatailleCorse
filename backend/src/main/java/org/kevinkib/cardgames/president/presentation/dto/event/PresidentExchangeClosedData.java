package org.kevinkib.cardgames.president.presentation.dto.event;

import org.kevinkib.cardgames.president.domain.event.ExchangeViewClosed;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.dto.event.EventData;

import java.util.List;

/** The exchange view of {@code round} was closed by the timeout; {@code late} are the seat numbers that had not seen it. */
public record PresidentExchangeClosedData(int round, List<Integer> late) implements EventData {

    public static PresidentExchangeClosedData from(ExchangeViewClosed event) {
        return new PresidentExchangeClosedData(event.round(), event.late().stream().map(PlayerId::id).toList());
    }
}
