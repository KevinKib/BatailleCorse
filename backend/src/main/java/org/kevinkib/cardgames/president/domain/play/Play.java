package org.kevinkib.cardgames.president.domain.play;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.List;

/** One or more cards of the same rank played together (a set). Runs and wild sets come with later PRs. */
public record Play(List<Card> cards, FrenchRank rank) {

    public Play {
        cards = List.copyOf(cards);
    }

    public int count() {
        return cards.size();
    }
}
