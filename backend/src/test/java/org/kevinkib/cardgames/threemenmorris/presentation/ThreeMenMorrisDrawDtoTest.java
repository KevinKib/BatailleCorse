package org.kevinkib.cardgames.threemenmorris.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.ThreeMenMorrisDto;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * The additive draw fields of the view. They are left out while they hold their default, so the wire of
 * every game without a draw situation is unchanged: {@code drawOffer} (absent: none pending),
 * {@code repetitions} (absent: the position is new or seen once) and {@code drawActions} (absent: the
 * default of the phase, OFFER_DRAW in the movement phase and nothing otherwise).
 */
class ThreeMenMorrisDrawDtoTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    /** Movement phase, white to move: white 0, 2, 7 and black 1, 5, 8, no line, nobody blocked. */
    private ThreeMenMorris movement() {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate());
        try {
            for (int[] p : new int[][]{{0, 0}, {1, 1}, {0, 2}, {1, 5}, {0, 7}, {1, 8}}) {
                game.place(new PlayerId(p[0]), p[1]);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return game;
    }

    private static String json(ThreeMenMorris game, PlayerId viewer) {
        return JsonMapper.builder().build().writeValueAsString(ThreeMenMorrisDto.forViewer(game, viewer));
    }

    @Test
    void aMovementGameWithoutDrawActivityLeavesTheDrawFieldsOut() {
        ThreeMenMorris game = movement();

        ThreeMenMorrisDto dto = ThreeMenMorrisDto.forViewer(game, WHITE);

        assertThat(dto.drawOffer(), is(nullValue()));
        assertThat(dto.repetitions(), is(nullValue()));
        assertThat(dto.drawActions(), is(nullValue()));
        assertThat(json(game, WHITE), not(containsString("drawOffer")));
        assertThat(json(game, WHITE), not(containsString("repetitions")));
        assertThat(json(game, WHITE), not(containsString("drawActions")));
    }

    @Test
    void aPendingOfferIsPublicAndTheOffereeSeesTheAnswers() throws Exception {
        ThreeMenMorris game = movement();
        game.offerDraw(BLACK);

        assertThat(ThreeMenMorrisDto.forViewer(game, WHITE).drawOffer(), is(new ThreeMenMorrisDto.DrawOfferDto(1)));
        assertThat(json(game, WHITE), containsString("\"drawOffer\":{\"seat\":1}"));
        assertThat(json(game, BLACK), containsString("\"drawOffer\":{\"seat\":1}"));
        assertThat(ThreeMenMorrisDto.forViewer(game, WHITE).drawActions(), is(List.of("ACCEPT_DRAW", "DECLINE_DRAW")));
        assertThat(json(game, WHITE), containsString("\"drawActions\":[\"ACCEPT_DRAW\",\"DECLINE_DRAW\"]"));
        assertThat(ThreeMenMorrisDto.forViewer(game, BLACK).drawActions(), is(List.of()));
        assertThat(json(game, BLACK), containsString("\"drawActions\":[]"));
    }

    @Test
    void aSeatThatAlreadyOfferedSeesNoOfferActionAfterTheDecline() throws Exception {
        ThreeMenMorris game = movement();
        game.offerDraw(WHITE);
        game.declineDraw(BLACK);

        assertThat(ThreeMenMorrisDto.forViewer(game, WHITE).drawOffer(), is(nullValue()));
        assertThat(ThreeMenMorrisDto.forViewer(game, WHITE).drawActions(), is(List.of()));
        assertThat(ThreeMenMorrisDto.forViewer(game, BLACK).drawActions(), is(nullValue()));
    }

    @Test
    void repetitionsAppearWhenThePositionHasBeenSeenTwice() throws Exception {
        ThreeMenMorris game = movement();
        game.move(WHITE, 7, 6);
        game.move(BLACK, 8, 4);
        game.move(WHITE, 6, 7);
        game.move(BLACK, 4, 8);

        assertThat(ThreeMenMorrisDto.forViewer(game, WHITE).repetitions(), is(2));
        assertThat(json(game, WHITE), containsString("\"repetitions\":2"));
    }

    @Test
    void anAgreedDrawIsFinishedWithNoWinnerAndNoDrawFields() throws Exception {
        ThreeMenMorris game = movement();
        game.offerDraw(WHITE);
        game.acceptDraw(BLACK);

        ThreeMenMorrisDto dto = ThreeMenMorrisDto.forViewer(game, WHITE);

        assertThat(dto.outcome(), is((ThreeMenMorrisDto.Outcome) new ThreeMenMorrisDto.Finished(null, "DRAW_AGREED")));
        assertThat(dto.drawOffer(), is(nullValue()));
        assertThat(dto.drawActions(), is(nullValue()));
        assertThat(json(game, WHITE), containsString("\"outcome\":{\"status\":\"FINISHED\",\"winner\":null,\"reason\":\"DRAW_AGREED\"}"));
    }

    @Test
    void aDrawByRepetitionIsFinishedWithNoWinner() throws Exception {
        ThreeMenMorris game = movement();
        for (int i = 0; i < 2; i++) {
            game.move(WHITE, 7, 6);
            game.move(BLACK, 8, 4);
            game.move(WHITE, 6, 7);
            game.move(BLACK, 4, 8);
        }

        assertThat(ThreeMenMorrisDto.forViewer(game, BLACK).outcome(),
                is((ThreeMenMorrisDto.Outcome) new ThreeMenMorrisDto.Finished(null, "DRAW_REPETITION")));
        assertThat(ThreeMenMorrisDto.forViewer(game, BLACK).repetitions(), is(nullValue()));
    }

    @Test
    void aPlacementGameHasNoDrawFields() {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate());

        assertThat(json(game, WHITE), not(containsString("draw")));
        assertThat(json(game, WHITE), not(containsString("repetitions")));
    }
}
