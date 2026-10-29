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

    @Test
    void theIdIsTheBareUuidLikeEveryOtherGame() {
        GameId id = new GameId("00000000-0000-0000-0000-000000000001");

        assertThat(BatailleCorseDto.from(new BatailleCorse(id, 2)).getId(), is("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    void givenFreshGame_thenVersionIsZero() {
        assertThat(BatailleCorseDto.from(twoPlayerGame()).getVersion(), is(0));
    }

    @Test
    void givenStateChanges_thenVersionFollowsTheGame() throws Exception {
        BatailleCorse game = twoPlayerGame();
        game.send(game.getCurrentPlayer());
        game.send(game.getCurrentPlayer());

        assertThat(BatailleCorseDto.from(game).getVersion(), is(2));
    }
}
