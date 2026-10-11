package org.kevinkib.cardgames.kobo.presentation;

import org.kevinkib.cards.domain.Rank;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reads a rank sent by a client as the label the server publishes (see CardDto.rank). */
public final class KoboRankMapper {

    private static final Map<String, FrenchRank> RANK_BY_LABEL = Arrays.stream(FrenchRank.values())
            .collect(Collectors.toMap(FrenchRank::toString, Function.identity(), (a, b) -> a));

    private KoboRankMapper() {
    }

    public static Rank toRank(String label) {
        FrenchRank rank = label == null ? null : RANK_BY_LABEL.get(label);
        if (rank == null) {
            throw new IllegalArgumentException("Unknown rank: " + label);
        }
        return rank;
    }
}
