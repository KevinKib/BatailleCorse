package org.kevinkib.cardgames.threemenmorris.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.ThreeMenMorrisDto;
import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/** The additive colour fields of the view: absent while seat 0 plays White, so the v1 wire is unchanged. */
class ThreeMenMorrisHostColourDtoTest {

    private static final PlayerId SEAT_0 = new PlayerId(0);
    private static final PlayerId SEAT_1 = new PlayerId(1);

    private static String json(ThreeMenMorris game) {
        return JsonMapper.builder().build().writeValueAsString(ThreeMenMorrisDto.forViewer(game, SEAT_0));
    }

    @Test
    void whenSeatZeroIsWhiteTheColourFieldsAreLeftOut() {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate(), MorrisRules.ACHI, SEAT_0);

        ThreeMenMorrisDto dto = ThreeMenMorrisDto.forViewer(game, SEAT_0);

        assertThat(dto.whiteSeat(), is((Integer) null));
        assertThat(dto.players().get(0).colour(), is((String) null));
        assertThat(json(game), not(containsString("whiteSeat")));
        assertThat(json(game), not(containsString("colour")));
    }

    @Test
    void whenSeatOneIsWhiteTheViewNamesTheWhiteSeatAndEachColourAndTheWhiteSeatPlaysFirst() {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate(), MorrisRules.TAPATAN, SEAT_1);

        ThreeMenMorrisDto dto = ThreeMenMorrisDto.forViewer(game, SEAT_0);

        assertThat(dto.whiteSeat(), is(1));
        assertThat(dto.currentSeat(), is(1));
        assertThat(dto.players().get(0).colour(), is("BLACK"));
        assertThat(dto.players().get(1).colour(), is("WHITE"));
        assertThat(json(game), containsString("\"whiteSeat\":1"));
        assertThat(json(game), containsString("\"colour\":\"WHITE\""));
    }
}
