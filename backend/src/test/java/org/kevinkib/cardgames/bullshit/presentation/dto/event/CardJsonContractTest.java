package org.kevinkib.cardgames.bullshit.presentation.dto.event;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** Characterization: pins the JSON of a card and of an event carrying cards. */
class CardJsonContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void aCardIsRankSuitName() {
        String json = MAPPER.writeValueAsString(CardDto.from(new Card(FrenchRank.ACE, FrenchSuit.HEART)));

        assertThat(json, is("{\"rank\":\"ACE\",\"suit\":\"HEART\",\"name\":\"HEART_ACE\"}"));
    }

    @Test
    void aCallBullshitEventCarriesTheRevealedCards() {
        var data = new CallBullshitEventData(0, 1, false, 0, List.of(
                CardDto.from(new Card(FrenchRank.TWO, FrenchSuit.SPADE)),
                CardDto.from(new Card(FrenchRank.KING, FrenchSuit.CLUB))));

        assertThat(MAPPER.writeValueAsString(data), is("{\"callerSeat\":0,\"claimantSeat\":1,\"truthful\":false,\"pickerSeat\":0,\"revealedCards\":[{\"rank\":\"2\",\"suit\":\"SPADE\",\"name\":\"SPADE_2\"},{\"rank\":\"KING\",\"suit\":\"CLUB\",\"name\":\"CLUB_KING\"}]}"));
    }
}
