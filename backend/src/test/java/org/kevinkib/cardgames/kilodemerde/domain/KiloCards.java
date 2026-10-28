package org.kevinkib.cardgames.kilodemerde.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.Arrays;
import java.util.List;

/** Test helper: cards from short codes. "KS" is the king of spades, "10H" the ten of hearts, "AD" the ace of diamonds. */
final class KiloCards {

    private KiloCards() {
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
            case "7" -> FrenchRank.SEVEN;
            case "8" -> FrenchRank.EIGHT;
            case "9" -> FrenchRank.NINE;
            case "10" -> FrenchRank.TEN;
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
        return codes.isBlank() ? List.of() : Arrays.stream(codes.trim().split("\\s+")).map(KiloCards::c).toList();
    }
}
