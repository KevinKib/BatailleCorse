package org.kevinkib.cardgames.ninetynine.domain.bot;

import org.kevinkib.cards.domain.Card;

/** What a bot wants to do: play one card, with a value for an Ace (null otherwise). */
public sealed interface NinetyNineDecision {

    record Play(Card card, Integer aceValue) implements NinetyNineDecision {
    }
}
