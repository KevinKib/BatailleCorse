package org.kevinkib.cardgames.presentation.dto;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseId;
import org.kevinkib.cardgames.bataillecorse.domain.Player;
import org.kevinkib.cardgames.presentation.ForfeitReason;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class PlayerDtoTest {

    private Player aPlayer() {
        BatailleCorse game = new BatailleCorse(BatailleCorseId.generate(), 2);
        return game.getPlayerByIndex(0);
    }

    @Test
    void givenNoReason_thenForfeitReasonIsNull() {
        PlayerDto dto = PlayerDto.from(aPlayer(), List.of());
        assertThat(dto.getForfeitReason(), is(nullValue()));
    }

    @Test
    void givenReason_thenForfeitReasonIsItsName() {
        PlayerDto dto = PlayerDto.from(aPlayer(), List.of(), ForfeitReason.DISCONNECTED);
        assertThat(dto.getForfeitReason(), is("DISCONNECTED"));
    }
}
