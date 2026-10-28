package org.kevinkib.cardgames.blackjack.domain.event;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

import java.util.List;

/** The seat split a pair into two hands; {@code cards} are the cards the hands hold right after the split. */
public record SplitHand(PlayerId seat, List<Card> cards) implements BlackjackEvent {

    public SplitHand {
        cards = List.copyOf(cards);
    }
}
