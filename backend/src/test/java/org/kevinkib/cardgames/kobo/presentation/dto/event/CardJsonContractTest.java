package org.kevinkib.cardgames.kobo.presentation.dto.event;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.event.KoboEvent;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/** Characterization: pins the JSON of a card and of an event carrying one. */
class CardJsonContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void aCardIsRankSuitName() {
        String json = MAPPER.writeValueAsString(CardDto.from(new Card(FrenchRank.ACE, FrenchSuit.HEART)));

        assertThat(json, is("{\"rank\":\"ACE\",\"suit\":\"HEART\",\"name\":\"HEART_ACE\"}"));
    }

    @Test
    void noCardIsNull() {
        assertThat(CardDto.from(null), nullValue());
    }

    @Test
    void aSwapEventCarriesTheDiscardedCardAndNulls() {
        KoboEventData data = KoboEventData.from(
                new KoboEvent.Swapped(new PlayerId(1), 2, new Card(FrenchRank.KING, FrenchSuit.CLUB)));

        assertThat(MAPPER.writeValueAsString(data), is("{\"seat\":1,\"action\":\"SWAP\",\"slot\":2,\"targetSeat\":null,\"targetSlot\":null,\"card\":{\"rank\":\"KING\",\"suit\":\"CLUB\",\"name\":\"CLUB_KING\"},\"givenSlot\":null,\"success\":null,\"barrierOpened\":null}"));
    }
}
