package org.kevinkib.cardgames.ninetynine.domain.bot;

import org.kevinkib.cardgames.ninetynine.domain.bot.NinetyNineMoves.Move;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.List;

/**
 * The {@code medium} level, deterministic: keep the escape cards (K, 10, 4, 9) while a plain card is safe and play
 * the plain card that brings the total highest (it pushes the next seat towards 99); when no plain card is safe,
 * spend a 9, then a 4, then a 10, and the K last.
 */
public class GreedyNinetyNineBot implements NinetyNineBotStrategy {

    private static final List<FrenchRank> ESCAPE_ORDER =
            List.of(FrenchRank.NINE, FrenchRank.FOUR, FrenchRank.TEN, FrenchRank.KING);

    @Override
    public NinetyNineDecision decide(NinetyNineBotView view) {
        Move move = pick(view.total(), view.hand());
        if (move == null) {
            throw new IllegalStateException("No safe card to play at " + view.total());
        }
        return move.toDecision();
    }

    /** The greedy move of a hand at a total, or null when nothing is safe. Also the model of the opponents. */
    static Move pick(int total, List<Card> hand) {
        List<Move> moves = NinetyNineMoves.of(total, hand);
        Move best = null;
        for (Move move : moves) {
            if (!NinetyNineMoves.isEscape(move.card()) && (best == null || move.totalAfter() > best.totalAfter())) {
                best = move;
            }
        }
        if (best != null) {
            return best;
        }
        for (FrenchRank rank : ESCAPE_ORDER) {
            for (Move move : moves) {
                if (move.card().getRank() == rank) {
                    return move;
                }
            }
        }
        return null;
    }
}
