package org.kevinkib.cardgames.president.domain.bot;

import org.kevinkib.cards.domain.Card;

import java.util.List;

/**
 * What a bot wants to do; each case but {@link Keep} maps to one President command (the caller applies it).
 */
public sealed interface PresidentBotDecision {

    /** Play these cards (one set), in turn. */
    record Play(List<Card> cards) implements PresidentBotDecision {
        public Play {
            cards = List.copyOf(cards);
        }
    }

    /** Play the cards that complete the four, in turn or out of turn (the same command as a play). */
    record Complete(List<Card> cards) implements PresidentBotDecision {
        public Complete {
            cards = List.copyOf(cards);
        }
    }

    /** Keep the cards of a completion that is on offer: nothing to send (completion is optional). */
    record Keep() implements PresidentBotDecision {
    }

    record Pass() implements PresidentBotDecision {
    }

    /** Give these cards back in the exchange. */
    record GiveBack(List<Card> cards) implements PresidentBotDecision {
        public GiveBack {
            cards = List.copyOf(cards);
        }
    }

    /** Ready for the next round, at the barrier. */
    record Ready() implements PresidentBotDecision {
    }

    /** The exchange has been seen, in the exchange view. */
    record SeeExchange() implements PresidentBotDecision {
    }
}
