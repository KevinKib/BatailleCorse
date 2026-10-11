package org.kevinkib.cardgames.threemenmorris.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.ThreeMenMorrisDto;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

class ThreeMenMorrisDtoTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    private final ThreeMenMorris game = new ThreeMenMorris(GameId.generate());

    @Test
    void aNewGameIsShownToTheSeatToPlayWithItsPlaceAction() {
        ThreeMenMorrisDto dto = ThreeMenMorrisDto.forViewer(game, WHITE, Map.of(WHITE, "Alice", BLACK, "Bob"));

        assertThat(dto.started(), is(true));
        assertThat(dto.gameType(), is("three-men-morris"));
        assertThat(dto.id(), is(game.getId().uuid().toString()));
        assertThat(dto.version(), is(0));
        assertThat(dto.phase(), is("PLACEMENT"));
        assertThat(dto.currentSeat(), is(0));
        assertThat(dto.board(), is(Arrays.asList(null, null, null, null, null, null, null, null, null)));
        assertThat(dto.players().get(0).name(), is("Alice"));
        assertThat(dto.players().get(1).name(), is("Bob"));
        assertThat(dto.players().get(0).current(), is(true));
        assertThat(dto.players().get(0).piecesToPlace(), is(3));
        assertThat(dto.availableActions(), is(List.of("PLACE")));
        assertThat(dto.legalMoves(), is(empty()));
        assertThat(dto.winningLine(), is(empty()));
        assertThat(dto.outcome(), is(new ThreeMenMorrisDto.Ongoing()));
    }

    @Test
    void theOtherSeatSeesTheSamePublicStateWithoutActions() {
        ThreeMenMorrisDto dto = ThreeMenMorrisDto.forViewer(game, BLACK);

        assertThat(dto.currentSeat(), is(0));
        assertThat(dto.availableActions(), is(empty()));
        assertThat(dto.players().get(0).name(), is((String) null));
    }

    @Test
    void theBoardShowsTheOwnerSeatOfEachPoint() throws Exception {
        game.place(WHITE, 4);
        game.place(BLACK, 0);

        ThreeMenMorrisDto dto = ThreeMenMorrisDto.forViewer(game, WHITE);

        assertThat(dto.board(), is(Arrays.asList(1, null, null, null, 0, null, null, null, null)));
        assertThat(dto.version(), is(2));
        assertThat(dto.players().get(0).piecesToPlace(), is(2));
    }

    @Test
    void inTheMovementPhaseTheSeatToPlayGetsItsLegalMoves() throws Exception {
        for (int[] placement : new int[][]{{0, 0}, {1, 1}, {0, 2}, {1, 4}, {0, 7}, {1, 8}}) {
            game.place(new PlayerId(placement[0]), placement[1]);
        }

        ThreeMenMorrisDto white = ThreeMenMorrisDto.forViewer(game, WHITE);
        ThreeMenMorrisDto black = ThreeMenMorrisDto.forViewer(game, BLACK);

        assertThat(white.phase(), is("MOVEMENT"));
        assertThat(white.availableActions(), is(List.of("MOVE")));
        assertThat(white.legalMoves(), containsInAnyOrder(
                new ThreeMenMorrisDto.MoveDto(0, 3), new ThreeMenMorrisDto.MoveDto(2, 5),
                new ThreeMenMorrisDto.MoveDto(7, 6)));
        assertThat(black.legalMoves(), is(empty()));
        assertThat(black.availableActions(), is(empty()));
    }

    @Test
    void aFinishedGameShowsTheWinnerTheReasonAndTheLine() throws Exception {
        game.place(WHITE, 0);
        game.place(BLACK, 3);
        game.place(WHITE, 4);
        game.place(BLACK, 1);
        game.place(WHITE, 8);

        ThreeMenMorrisDto dto = ThreeMenMorrisDto.forViewer(game, BLACK);

        assertThat(dto.phase(), is("FINISHED"));
        assertThat(dto.outcome(), is(new ThreeMenMorrisDto.Finished(0, "LINE")));
        assertThat(dto.winningLine(), is(List.of(0, 4, 8)));
        assertThat(dto.availableActions(), is(empty()));
    }
}
