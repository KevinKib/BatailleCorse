package org.kevinkib.cardgames.presentation.dto;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reads the rank and suit labels the server publishes in {@link CardDto} back into cards. */
public final class CardLabels {

    private static final Map<String, FrenchRank> RANK_BY_LABEL = Arrays.stream(FrenchRank.values())
            .collect(Collectors.toMap(FrenchRank::toString, Function.identity(), (a, b) -> a));
    private static final Map<String, FrenchSuit> SUIT_BY_LABEL = Arrays.stream(FrenchSuit.values())
            .collect(Collectors.toMap(FrenchSuit::toString, Function.identity(), (a, b) -> a));

    private CardLabels() {
    }

    public static FrenchRank rank(String label) {
        FrenchRank rank = label == null ? null : RANK_BY_LABEL.get(label);
        if (rank == null) {
            throw new IllegalArgumentException("Unknown rank: " + label);
        }
        return rank;
    }

    public static FrenchSuit suit(String label) {
        FrenchSuit suit = label == null ? null : SUIT_BY_LABEL.get(label);
        if (suit == null) {
            throw new IllegalArgumentException("Unknown suit: " + label);
        }
        return suit;
    }

    public static Card card(CardDto dto) {
        FrenchRank rank = dto.rank() == null ? null : RANK_BY_LABEL.get(dto.rank());
        FrenchSuit suit = dto.suit() == null ? null : SUIT_BY_LABEL.get(dto.suit());
        if (rank == null || suit == null) {
            throw new IllegalArgumentException("Unknown card: rank=" + dto.rank() + " suit=" + dto.suit());
        }
        return new Card(rank, suit);
    }
}
