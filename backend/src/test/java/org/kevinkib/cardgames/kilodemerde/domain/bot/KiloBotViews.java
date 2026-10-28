package org.kevinkib.cardgames.kilodemerde.domain.bot;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeOptions;
import org.kevinkib.cardgames.kilodemerde.domain.Phase;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Test helper: bot views built by hand. "KS" is the king of spades, "10H" the ten of hearts. */
final class KiloBotViews {

    static final PlayerId ME = new PlayerId(0);

    private KiloBotViews() {
    }

    static Card c(String code) {
        String rank = code.substring(0, code.length() - 1);
        FrenchRank frenchRank = switch (rank) {
            case "A" -> FrenchRank.ACE;
            case "2" -> FrenchRank.TWO;
            case "3" -> FrenchRank.THREE;
            case "4" -> FrenchRank.FOUR;
            case "5" -> FrenchRank.FIVE;
            case "6" -> FrenchRank.SIX;
            case "8" -> FrenchRank.EIGHT;
            case "9" -> FrenchRank.NINE;
            case "J" -> FrenchRank.JACK;
            case "Q" -> FrenchRank.QUEEN;
            case "K" -> FrenchRank.KING;
            default -> throw new IllegalArgumentException(code);
        };
        FrenchSuit suit = switch (code.charAt(code.length() - 1)) {
            case 'H' -> FrenchSuit.HEART;
            case 'D' -> FrenchSuit.DIAMOND;
            case 'C' -> FrenchSuit.CLUB;
            case 'S' -> FrenchSuit.SPADE;
            default -> throw new IllegalArgumentException(code);
        };
        return new Card(frenchRank, suit);
    }

    static List<Card> cards(String codes) {
        return codes.isBlank() ? List.of() : Arrays.stream(codes.trim().split("\\s+")).map(KiloBotViews::c).toList();
    }

    /** A passing view of a 4-seat table for the given hand, with no history. */
    static KiloDeMerdeBotView view(String hand) {
        return view(hand, List.of());
    }

    static KiloDeMerdeBotView view(List<Card> hand) {
        return view(hand, List.of());
    }

    static KiloDeMerdeBotView view(String hand, List<KiloDeMerdeBotView.Pass> history) {
        return view(cards(hand), history);
    }

    static KiloDeMerdeBotView view(List<Card> hand, List<KiloDeMerdeBotView.Pass> history) {
        List<PlayerId> seats = List.of(ME, new PlayerId(1), new PlayerId(2), new PlayerId(3));
        return new KiloDeMerdeBotView(ME, 1, 1, history.size() + 1, Phase.PASSING, hand, history,
                Map.of(ME, 0, seats.get(1), 0, seats.get(2), 0, seats.get(3), 0), seats, List.of(), 12,
                KiloDeMerdeOptions.defaults());
    }

    static KiloDeMerdeBotView.Pass pass(int step, String given, String received) {
        return new KiloDeMerdeBotView.Pass(step, c(given), c(received));
    }
}
