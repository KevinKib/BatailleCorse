package org.kevinkib.cardgames.threemenmorris.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.ThreeMenMorrisDto;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/** The additive fields of the view: the variant and the placements the viewer may make. */
class ThreeMenMorrisVariantDtoTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    @Test
    void anAchiGameShowsItsVariantFourPiecesAndEveryFreePointToTheSeatToPlay() {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate(), MorrisRules.ACHI, WHITE);

        ThreeMenMorrisDto white = ThreeMenMorrisDto.forViewer(game, WHITE);
        ThreeMenMorrisDto black = ThreeMenMorrisDto.forViewer(game, BLACK);

        assertThat(white.variant(), is("achi"));
        assertThat(white.players().get(0).piecesToPlace(), is(4));
        assertThat(white.legalPlacements(), is(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8)));
        assertThat(black.variant(), is("achi"));
        assertThat(black.legalPlacements(), is(List.of()));
    }

    @Test
    void legalPlacementsAreEmptyInMovementAndAfterTheEnd() throws Exception {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate(), MorrisRules.ACHI, WHITE);
        for (int[] placement : new int[][]{{0, 0}, {1, 1}, {0, 2}, {1, 4}, {0, 7}, {1, 5}, {0, 3}, {1, 6}}) {
            game.place(new PlayerId(placement[0]), placement[1]);
        }

        assertThat(ThreeMenMorrisDto.forViewer(game, WHITE).legalPlacements(), is(List.of()));
    }

    @Test
    void tapatanSerialisesWithoutTheNewFieldsSoTheV1WireIsUnchanged() {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate());

        ThreeMenMorrisDto dto = ThreeMenMorrisDto.forViewer(game, WHITE);
        String json = JsonMapper.builder().build().writeValueAsString(dto);

        assertThat(dto.variant(), is((String) null));
        assertThat(dto.legalPlacements(), is((List<Integer>) null));
        assertThat(json, not(containsString("variant")));
        assertThat(json, not(containsString("legalPlacements")));
    }

    @Test
    void otherVariantsSerialiseTheNewFields() {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate(), MorrisRules.ACHI, WHITE);

        String json = JsonMapper.builder().build().writeValueAsString(ThreeMenMorrisDto.forViewer(game, WHITE));

        assertThat(json, containsString("\"variant\":\"achi\""));
        assertThat(json, containsString("\"legalPlacements\":[0,1,2,3,4,5,6,7,8]"));
    }
}
