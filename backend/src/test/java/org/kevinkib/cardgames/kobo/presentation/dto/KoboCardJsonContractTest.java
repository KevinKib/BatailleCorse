package org.kevinkib.cardgames.kobo.presentation.dto;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.turn.Phase;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.aKobo;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.seat;

/** Characterization: pins how a state containing cards is serialized for the frontend. */
class KoboCardJsonContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void aStateExposesTheDiscardTopAsACardAndNoDrawnCardAsNull() {
        Kobo kobo = aKobo()
                .withTableau("AH", "2H", "3H", "4H")
                .withTableau("5S", "6S", "9D", "KS")
                .withDiscard("QD").withDrawPile("8C", "9C", "10C", "JC")
                .inPhase(Phase.DRAW).build();

        String json = MAPPER.writeValueAsString(KoboDto.forViewer(kobo, seat(0), Map.of()));

        assertThat(json, containsString(
                "\"discardTop\":{\"rank\":\"QUEEN\",\"suit\":\"DIAMOND\",\"name\":\"DIAMOND_QUEEN\"}"));
        assertThat(json, containsString("\"drawnCard\":null"));
        assertThat(json, containsString("\"slot\":0,\"occupied\":true,\"revision\":0,\"card\":null"));
    }
}
