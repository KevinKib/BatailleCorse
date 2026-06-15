package org.kevinkib.cardgames.bullshit.presentation.dto;

import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class CardDtoTest {

    @Test
    void givenCard_whenFrom_thenExposesRankSuitName() {
        CardDto dto = CardDto.from(new Card(FrenchRank.ACE, FrenchSuit.HEART));

        assertThat(dto.rank(), is("ACE"));
        assertThat(dto.suit(), is("HEART"));
        assertThat(dto.name(), is("HEART_ACE"));
    }
}
