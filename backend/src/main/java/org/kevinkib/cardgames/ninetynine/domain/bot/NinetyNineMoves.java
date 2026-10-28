package org.kevinkib.cardgames.ninetynine.domain.bot;

import org.kevinkib.cardgames.ninetynine.domain.CardEffect;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.ArrayList;
import java.util.List;

/** The legal moves of a hand at a total, built only from {@link CardEffect}, so a bot never plays an illegal card. */
final class NinetyNineMoves {

    /** One card with its Ace value (null for other cards) and the total it leads to. */
    record Move(Card card, Integer aceValue, int totalAfter) {

        NinetyNineDecision.Play toDecision() {
            return new NinetyNineDecision.Play(card, aceValue);
        }
    }

    private NinetyNineMoves() {
    }

    /** Every move of the safe cards of the hand, in hand order; an Ace gives one move per allowed value. */
    static List<Move> of(int total, List<Card> hand) {
        List<Move> moves = new ArrayList<>();
        for (Card card : CardEffect.safeCards(total, hand)) {
            if (isAce(card)) {
                for (int value : CardEffect.aceValues(total)) {
                    moves.add(new Move(card, value, CardEffect.apply(total, card, value)));
                }
            } else {
                moves.add(new Move(card, null, CardEffect.apply(total, card, null)));
            }
        }
        return moves;
    }

    /** The escape cards: always playable, kept for the moments nothing else is safe (K, 10, 4, 9). */
    static boolean isEscape(Card card) {
        FrenchRank rank = (FrenchRank) card.getRank();
        return rank == FrenchRank.KING || rank == FrenchRank.TEN || rank == FrenchRank.FOUR
                || rank == FrenchRank.NINE;
    }

    static boolean isAce(Card card) {
        return card.getRank() == FrenchRank.ACE;
    }
}
