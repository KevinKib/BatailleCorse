package org.kevinkib.cardgames.president.domain.event;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

import java.util.List;

/** {@code outOfTurn} when the seat was not the one to act: the completion of the four. */
public record Played(PlayerId seat, List<Card> cards, boolean outOfTurn) implements PresidentEvent {

    public Played {
        cards = List.copyOf(cards);
    }

    /** A play in turn. */
    public Played(PlayerId seat, List<Card> cards) {
        this(seat, cards, false);
    }
}
