package org.kevinkib.cardgames.president.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.Arrays;
import java.util.List;

/** Card codes for tests: {@code cards("3C 3H 2S")}, rank then suit letter (H, D, C, S). */
public final class PresidentFixtures {

    private PresidentFixtures() {
    }

    public static Card card(String code) {
        String rank = code.substring(0, code.length() - 1);
        FrenchSuit suit = switch (code.charAt(code.length() - 1)) {
            case 'H' -> FrenchSuit.HEART;
            case 'D' -> FrenchSuit.DIAMOND;
            case 'C' -> FrenchSuit.CLUB;
            case 'S' -> FrenchSuit.SPADE;
            default -> throw new IllegalArgumentException(code);
        };
        return new Card(rank(rank), suit);
    }

    public static FrenchRank rank(String code) {
        return switch (code) {
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
    }

    /** "3C 3H 2S" gives three cards; a blank string gives none. */
    public static List<Card> cards(String codes) {
        if (codes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(codes.trim().split("\\s+")).map(PresidentFixtures::card).toList();
    }

    /** The code of a card, the inverse of {@link #card(String)}. */
    public static String code(Card card) {
        FrenchRank rank = (FrenchRank) card.getRank();
        String r = switch (rank) {
            case ACE -> "A";
            case TWO -> "2";
            case THREE -> "3";
            case FOUR -> "4";
            case FIVE -> "5";
            case SIX -> "6";
            case SEVEN -> "7";
            case EIGHT -> "8";
            case NINE -> "9";
            case TEN -> "10";
            case JACK -> "J";
            case QUEEN -> "Q";
            case KING -> "K";
            default -> "?";
        };
        FrenchSuit suit = (FrenchSuit) card.getSuit();
        String s = switch (suit) {
            case HEART -> "H";
            case DIAMOND -> "D";
            case CLUB -> "C";
            case SPADE -> "S";
            default -> "?";
        };
        return r + s;
    }

    /** The codes of several cards, space separated, in order. */
    public static String codes(List<Card> cards) {
        return String.join(" ", cards.stream().map(PresidentFixtures::code).toList());
    }
}
