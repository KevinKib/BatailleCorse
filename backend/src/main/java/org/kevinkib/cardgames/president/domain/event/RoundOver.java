package org.kevinkib.cardgames.president.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;

/** Only one player still holds cards: they take the last place and the round is over. */
public record RoundOver(List<PlayerId> finishingOrder) implements PresidentEvent {

    public RoundOver {
        finishingOrder = List.copyOf(finishingOrder);
    }
}
