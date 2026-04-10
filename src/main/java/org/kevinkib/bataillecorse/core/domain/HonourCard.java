package org.kevinkib.bataillecorse.core.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.Arrays;

public enum HonourCard {

    JACK(FrenchRank.JACK, 1),
    QUEEN(FrenchRank.QUEEN, 2),
    KING(FrenchRank.KING, 3),
    ACE(FrenchRank.ACE, 4);

    private final FrenchRank rank;
    private final Integer nbChances;

    HonourCard(FrenchRank rank, Integer nbChances) {
        this.rank = rank;
        this.nbChances = nbChances;
    }

    public Integer getNbChances() {
        return nbChances;
    }

    public static HonourCard from(Card card) {
        for (HonourCard honourCard : values()) {
            if (honourCard.rank.equals(card.getRank())) {
                return honourCard;
            }
        }

        throw new IllegalArgumentException("Cannot pass a card without a honour symbol.");
    }

    public static boolean isHonour(Card card) {
        return Arrays.stream(values()).anyMatch(hounourCard -> hounourCard.rank == card.getRank());
    }
}
