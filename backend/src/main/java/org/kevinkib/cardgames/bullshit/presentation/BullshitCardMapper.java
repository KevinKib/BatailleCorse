package org.kevinkib.cardgames.bullshit.presentation;

import org.kevinkib.cardgames.bullshit.presentation.dto.CardDto;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class BullshitCardMapper {

    private static final Map<String, FrenchRank> RANK_BY_LABEL = Arrays.stream(FrenchRank.values())
            .collect(Collectors.toMap(FrenchRank::toString, Function.identity(), (a, b) -> a));
    private static final Map<String, FrenchSuit> SUIT_BY_LABEL = Arrays.stream(FrenchSuit.values())
            .collect(Collectors.toMap(FrenchSuit::toString, Function.identity(), (a, b) -> a));

    private BullshitCardMapper() {
    }

    public static Card toCard(CardDto dto) {
        FrenchRank rank = RANK_BY_LABEL.get(dto.rank());
        FrenchSuit suit = SUIT_BY_LABEL.get(dto.suit());
        if (rank == null || suit == null) {
            throw new IllegalArgumentException("Unknown card: rank=" + dto.rank() + " suit=" + dto.suit());
        }
        return new Card(rank, suit);
    }

    public static List<Card> toCards(List<CardDto> dtos) {
        return dtos.stream().map(BullshitCardMapper::toCard).toList();
    }
}
