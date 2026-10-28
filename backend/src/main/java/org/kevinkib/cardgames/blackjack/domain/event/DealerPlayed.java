package org.kevinkib.cardgames.blackjack.domain.event;

import org.kevinkib.cards.domain.Card;

import java.util.List;

/** The dealer showed the hole card and drew {@code drawn} (possibly nothing); {@code value} is the final total. */
public record DealerPlayed(Card holeCard, List<Card> drawn, int value) implements BlackjackEvent {

    public DealerPlayed {
        drawn = List.copyOf(drawn);
    }
}
