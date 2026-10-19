package org.kevinkib.cardgames.president.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;

/** The table stopped waiting for the exchange view of {@code round}: the {@code late} seats are counted as having seen it. */
public record ExchangeViewClosed(int round, List<PlayerId> late) implements PresidentEvent {
    public ExchangeViewClosed {
        late = List.copyOf(late);
    }
}
