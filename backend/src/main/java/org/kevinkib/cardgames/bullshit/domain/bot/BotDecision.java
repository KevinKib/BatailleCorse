package org.kevinkib.cardgames.bullshit.domain.bot;

import org.kevinkib.cards.domain.Card;

import java.util.List;

/** What a bot decides to do for the state it observed. */
public sealed interface BotDecision {

    /** Play these cards (1 to 4, all in the bot's hand) as the current claim target. */
    record Discard(List<Card> cards) implements BotDecision {
        public Discard {
            cards = List.copyOf(cards);
        }
    }

    /** Challenge the claim on the table. */
    record CallBullshit() implements BotDecision {
    }

    /** Do nothing for now; the bot is re-evaluated at the next state change. */
    record Pass() implements BotDecision {
    }
}
