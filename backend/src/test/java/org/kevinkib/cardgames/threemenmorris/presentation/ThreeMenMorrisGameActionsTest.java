package org.kevinkib.cardgames.threemenmorris.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.threemenmorris.domain.NotPlayersTurnException;
import org.kevinkib.cardgames.threemenmorris.domain.PointOccupiedException;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.ThreeMenMorrisDto;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.event.ThreeMenMorrisEventData;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThreeMenMorrisGameActionsTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    private final ThreeMenMorrisTable table = new ThreeMenMorrisTable();

    @Test
    void aPlacementBroadcastsOneStatePerSeatWithAPlaceEvent() throws Exception {
        ThreeMenMorris game = table.newGame();

        table.actions.place(game.getId(), WHITE, 4);

        assertThat(table.messaging.seats, is(List.of(WHITE, BLACK)));
        Response first = table.messaging.payloads.get(0);
        assertThat(first.isSuccess(), is(true));
        assertThat(first.getEventType(), is("PLACE"));
        ThreeMenMorrisEventData data = (ThreeMenMorrisEventData) first.getEventData();
        assertThat(data.seat(), is(0));
        assertThat(data.point(), is(4));
        assertThat(((ThreeMenMorrisDto) first.getState()).board().get(4), is(0));
        assertThat(((ThreeMenMorrisDto) first.getState()).currentSeat(), is(1));
    }

    @Test
    void aRejectedCommandBroadcastsNothingAndPropagates() throws Exception {
        ThreeMenMorris game = table.newGame();
        table.actions.place(game.getId(), WHITE, 4);
        table.messaging.clear();

        assertThrows(NotPlayersTurnException.class, () -> table.actions.place(game.getId(), WHITE, 0));
        assertThrows(PointOccupiedException.class, () -> table.actions.place(game.getId(), BLACK, 4));

        assertThat(table.messaging.payloads.isEmpty(), is(true));
        assertThat(game.version(), is(1));
    }

    @Test
    void aMoveIsBroadcastWithItsEndpoints() throws Exception {
        ThreeMenMorris game = table.newGame();
        for (int[] placement : new int[][]{{0, 0}, {1, 1}, {0, 2}, {1, 4}, {0, 7}, {1, 8}}) {
            table.actions.place(game.getId(), new PlayerId(placement[0]), placement[1]);
        }
        table.messaging.clear();

        table.actions.move(game.getId(), WHITE, 7, 6);

        Response first = table.messaging.payloads.get(0);
        assertThat(first.getEventType(), is("MOVE"));
        ThreeMenMorrisEventData data = (ThreeMenMorrisEventData) first.getEventData();
        assertThat(data.from(), is(7));
        assertThat(data.to(), is(6));
    }

    @Test
    void theCommandThatEndsTheGameIsAnnouncedAsGameOverAndNamesTheCommand() throws Exception {
        ThreeMenMorris game = table.newGame();
        for (int[] placement : new int[][]{{0, 0}, {1, 3}, {0, 1}, {1, 4}}) {
            table.actions.place(game.getId(), new PlayerId(placement[0]), placement[1]);
        }
        table.messaging.clear();

        table.actions.place(game.getId(), WHITE, 2);

        Response over = table.messaging.payloads.get(0);
        assertThat(over.getEventType(), is("GAME_OVER"));
        assertThat(((ThreeMenMorrisEventData) over.getEventData()).action(), is("PLACE"));
        ThreeMenMorrisDto state = (ThreeMenMorrisDto) over.getState();
        assertThat(state.outcome(), is(new ThreeMenMorrisDto.Finished(0, "LINE")));
        assertThat(state.winningLine(), is(List.of(0, 1, 2)));
        assertThat(table.messaging.payloads.size(), is(2));
    }
}
