package org.kevinkib.cardgames.president.domain.event;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

import java.util.List;

public record Played(PlayerId seat, List<Card> cards) implements PresidentEvent {

    public Played {
        cards = List.copyOf(cards);
    }
}
