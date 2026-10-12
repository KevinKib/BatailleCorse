package org.kevinkib.cardgames.presentation.dto;

import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class CardDtoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void givenCard_whenFrom_thenExposesRankSuitName() {
        CardDto dto = CardDto.from(new Card(FrenchRank.ACE, FrenchSuit.HEART));

        assertThat(dto.rank(), is("ACE"));
        assertThat(dto.suit(), is("HEART"));
        assertThat(dto.name(), is("HEART_ACE"));
    }

    @Test
    void givenNoCard_whenFrom_thenNull() {
        assertThat(CardDto.from(null), nullValue());
    }

    @Test
    void givenCardWithoutSuit_whenFrom_thenNameIsTheRankAndSuitIsNull() {
        CardDto dto = CardDto.from(new Card(FrenchRank.ACE, null));

        assertThat(dto.suit(), nullValue());
        assertThat(dto.name(), is("ACE"));
    }

    @Test
    void givenCard_whenSerialized_thenFieldsInOrder() {
        String json = MAPPER.writeValueAsString(CardDto.from(new Card(FrenchRank.TWO, FrenchSuit.SPADE)));

        assertThat(json, is("{\"rank\":\"2\",\"suit\":\"SPADE\",\"name\":\"SPADE_2\"}"));
    }

    @Test
    void givenJson_whenDeserialized_thenRebuildsTheDto() {
        CardDto dto = MAPPER.readValue("{\"rank\":\"2\",\"suit\":\"SPADE\",\"name\":\"SPADE_2\"}", CardDto.class);

        assertThat(dto, is(new CardDto("2", "SPADE", "SPADE_2")));
    }
}
