package org.kevinkib.cardgames.president.domain.bot;

import org.kevinkib.cards.domain.Card;

import java.util.List;

/**
 * What a bot wants to do; each case maps to one President command (the caller applies it). The completion of
 * the four out of turn joins them with the equal-play PR.
 */
public sealed interface PresidentBotDecision {

    /** Play these cards (one set), in turn. */
    record Play(List<Card> cards) implements PresidentBotDecision {
        public Play {
            cards = List.copyOf(cards);
        }
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
}
