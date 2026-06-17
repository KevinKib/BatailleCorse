package org.kevinkib.cardgames.bataillecorse.presentation.dto;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class BatailleCorseDtoTest {

    private BatailleCorse twoPlayerGame() {
        return new BatailleCorse(GameId.generate(), 2);
    }

    @Test
    void givenNoReasons_thenEveryPlayerForfeitReasonIsNull() {
        BatailleCorseDto dto = BatailleCorseDto.from(twoPlayerGame());

        assertThat(dto.getPlayers().get(0).getForfeitReason(), is(nullValue()));
        assertThat(dto.getPlayers().get(1).getForfeitReason(), is(nullValue()));
    }

    @Test
    void givenReasonForSeatOne_thenOnlyThatPlayerCarriesIt() {
        BatailleCorseDto dto = BatailleCorseDto.from(
                twoPlayerGame(), Map.of(1, ForfeitReason.RESIGNED));

        assertThat(dto.getPlayers().get(0).getForfeitReason(), is(nullValue()));
        assertThat(dto.getPlayers().get(1).getForfeitReason(), is("RESIGNED"));
    }
}
