package org.kevinkib.cardgames.bataillecorse.presentation.dto;

import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.CentralPileState;
import org.kevinkib.cardgames.bataillecorse.domain.CentralPileFixtures;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** Characterization: pins the JSON of a card and of a pile of cards sent to the frontend. */
class CardJsonContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void aCardIsRankSuitName() {
        String json = MAPPER.writeValueAsString(CardDto.from(new Card(FrenchRank.ACE, FrenchSuit.HEART)));

        assertThat(json, is("{\"rank\":\"ACE\",\"suit\":\"HEART\",\"name\":\"HEART_ACE\"}"));
    }

    @Test
    void aPileCarriesItsCards() {
        PileDto pile = PileDto.from(CentralPileFixtures.createCentralPileWithCardsAndState(
                List.of(new Card(FrenchRank.TWO, FrenchSuit.SPADE), new Card(FrenchRank.KING, FrenchSuit.CLUB)),
                CentralPileState.NEUTRAL));

        assertThat(MAPPER.writeValueAsString(pile), is("{\"cards\":[{\"rank\":\"KING\",\"suit\":\"CLUB\",\"name\":\"CLUB_KING\"},{\"rank\":\"2\",\"suit\":\"SPADE\",\"name\":\"SPADE_2\"}],\"grabbable\":false,\"nbCardsSinceLastHonourCard\":0,\"playerThatAddedLastHonourCard\":null}"));
    }
}
