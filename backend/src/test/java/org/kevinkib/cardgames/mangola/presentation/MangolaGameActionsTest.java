package org.kevinkib.cardgames.mangola.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.mangola.domain.AlreadyReadyException;
import org.kevinkib.cardgames.mangola.domain.InvalidLayoutException;
import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaGeometry;
import org.kevinkib.cardgames.mangola.domain.NotPlayersTurnException;
import org.kevinkib.cardgames.mangola.domain.StuckStartException;
import org.kevinkib.cardgames.mangola.presentation.dto.MangolaDto;
import org.kevinkib.cardgames.mangola.presentation.dto.event.MangolaEventData;
import org.kevinkib.cardgames.presentation.api.Response;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MangolaGameActionsTest {

    private static final org.kevinkib.cardgames.game.PlayerId SEAT_0 = MangolaTable.SEAT_0;
    private static final org.kevinkib.cardgames.game.PlayerId SEAT_1 = MangolaTable.SEAT_1;

    private final MangolaTable table = new MangolaTable();

    @Test
    void anArrangeBroadcastsOneStatePerSeatWithALayoutChangedEventThatCarriesNoCounts() throws Exception {
        Mangola game = table.placementGame();

        table.actions.arrange(game.getId(), SEAT_0, MangolaTable.twoPerPit());

        assertThat(table.messaging.seats, is(List.of(SEAT_0, SEAT_1)));
        Response mine = table.messaging.payloads.get(0);
        Response theirs = table.messaging.payloads.get(1);
        assertThat(mine.isSuccess(), is(true));
        assertThat(mine.getEventType(), is("LAYOUT_CHANGED"));
        MangolaEventData data = (MangolaEventData) mine.getEventData();
        assertThat(data.seat(), is(0));
        assertThat(data.action(), is("ARRANGE"));
        assertThat(data.pit(), is(nullValue()));
        assertThat(data.trace(), is(nullValue()));
        assertThat(((MangolaDto) mine.getState()).board().get(8), is(2));
        // the other seat is told that a draft changed, and its state still shows nothing of it
        assertThat(((MangolaDto) theirs.getState()).board().get(8), is(nullValue()));
        assertThat(((MangolaDto) theirs.getState()).version(), is(1));
    }

    @Test
    void aConfirmAndAnEditAreBroadcastWithTheirOwnEvents() throws Exception {
        Mangola game = table.placementGame();
        table.actions.arrange(game.getId(), SEAT_0, MangolaTable.twoPerPit());
        table.messaging.clear();

        table.actions.confirmLayout(game.getId(), SEAT_0);
        assertThat(table.messaging.payloads.get(0).getEventType(), is("LAYOUT_CONFIRMED"));
        assertThat(((MangolaDto) table.messaging.payloads.get(1).getState()).players().get(0).ready(), is(true));

        table.messaging.clear();
        table.actions.editLayout(game.getId(), SEAT_0);
        assertThat(table.messaging.payloads.get(0).getEventType(), is("LAYOUT_WITHDRAWN"));
        assertThat(((MangolaDto) table.messaging.payloads.get(1).getState()).players().get(0).ready(), is(false));
    }

    @Test
    void theSecondConfirmationRevealsBothLayoutsToBothSeatsInOneEvent() throws Exception {
        Mangola game = table.placementGame();
        table.actions.arrange(game.getId(), SEAT_0, MangolaTable.twoPerPit());
        table.actions.arrange(game.getId(), SEAT_1, MangolaTable.allOnFirst());
        table.actions.confirmLayout(game.getId(), SEAT_0);
        table.messaging.clear();

        table.actions.confirmLayout(game.getId(), SEAT_1);

        assertThat(table.messaging.payloads.size(), is(2));
        for (Response response : table.messaging.payloads) {
            assertThat(response.getEventType(), is("LAYOUTS_REVEALED"));
            MangolaDto state = (MangolaDto) response.getState();
            assertThat(state.phase(), is("PLAY"));
            assertThat(state.board().get(24), is(32));
            assertThat(state.board().get(8), is(2));
        }
        assertThat(((MangolaEventData) table.messaging.payloads.get(0).getEventData()).seat(), is(nullValue()));
    }

    @Test
    void aSowIsBroadcastWithItsTraceAndTheSeedsCaptured() throws Exception {
        // E3 with one more B pit so that the game goes on: a capture of 5 seeds
        Mangola game = table.playGame(MangolaTable.board("a2:2 c2:1 c3:2 c4:3 h4:5"));
        table.messaging.clear();

        table.actions.sow(game.getId(), SEAT_0, MangolaGeometry.pit("a2"));

        Response first = table.messaging.payloads.get(0);
        assertThat(first.getEventType(), is("SOWN"));
        MangolaEventData data = (MangolaEventData) first.getEventData();
        assertThat(data.seat(), is(0));
        assertThat(data.action(), is("SOW"));
        assertThat(data.pit(), is(8));
        assertThat(data.captured(), is(5));
        assertThat(data.trace().get(0), is(new MangolaDto.StepDto("PICK", 8, 2, null, null)));
        assertThat(data.trace().stream().anyMatch(s -> s.type().equals("CAPTURE")), is(true));
        MangolaDto state = (MangolaDto) first.getState();
        assertThat(state.currentSeat(), is(1));
        assertThat(state.lastMove().trace(), is(data.trace()));
        assertThat(table.messaging.payloads.size(), is(2));
    }

    @Test
    void theSowThatLeavesTheMoverStuckForGoodIsAnnouncedAsGameOverWithItsReason() throws Exception {
        // a2:3 leaves side A with single seeds only, side B still has a move: B wins at once
        Mangola game = table.playGame(MangolaTable.board("a2:3 c1:1 c3:2 d4:3"));
        table.messaging.clear();

        table.actions.sow(game.getId(), SEAT_0, MangolaGeometry.pit("a2"));

        Response over = table.messaging.payloads.get(0);
        assertThat(over.getEventType(), is("GAME_OVER"));
        assertThat(((MangolaDto) over.getState()).outcome(), is(new MangolaDto.Finished(1, "CANNOT_MOVE_AGAIN")));
    }

    @Test
    void theSowThatLeavesTheOpponentWithoutAMoveIsAnnouncedAsGameOver() throws Exception {
        // E3: the capture empties side B, so A wins at once
        Mangola game = table.playGame(MangolaTable.board("a2:2 c2:1 c3:2 c4:3"));
        table.messaging.clear();

        table.actions.sow(game.getId(), SEAT_0, MangolaGeometry.pit("a2"));

        Response over = table.messaging.payloads.get(0);
        assertThat(over.getEventType(), is("GAME_OVER"));
        assertThat(((MangolaEventData) over.getEventData()).action(), is("SOW"));
        assertThat(((MangolaDto) over.getState()).outcome(), is(new MangolaDto.Finished(0, "NO_MOVE")));
        assertThat(table.messaging.payloads.size(), is(2));
    }

    @Test
    void aConfirmationRefusedForAStuckStartBroadcastsNothingAndPropagates() throws Exception {
        Mangola game = table.placementGame();
        table.actions.arrange(game.getId(), SEAT_0,
                new int[]{24, 1, 0, 1, 0, 0, 0, 1, 0, 1, 0, 1, 0, 1, 1, 1});
        table.actions.arrange(game.getId(), SEAT_1,
                new int[]{2, 1, 1, 1, 1, 2, 3, 0, 3, 0, 1, 5, 2, 2, 3, 5});
        table.actions.confirmLayout(game.getId(), SEAT_1);
        table.messaging.clear();
        int version = game.version();

        assertThrows(StuckStartException.class, () -> table.actions.confirmLayout(game.getId(), SEAT_0));

        assertThat(table.messaging.payloads.isEmpty(), is(true));
        assertThat(game.version(), is(version));
    }

    @Test
    void aRejectedCommandBroadcastsNothingAndPropagates() throws Exception {
        Mangola placement = table.placementGame();
        assertThrows(InvalidLayoutException.class,
                () -> table.actions.arrange(placement.getId(), SEAT_0, new int[]{1, 2, 3}));
        table.actions.arrange(placement.getId(), SEAT_0, MangolaTable.twoPerPit());
        table.actions.confirmLayout(placement.getId(), SEAT_0);
        table.messaging.clear();

        assertThrows(AlreadyReadyException.class,
                () -> table.actions.arrange(placement.getId(), SEAT_0, MangolaTable.twoPerPit()));

        Mangola play = table.playGame(MangolaTable.board("a2:3 h4:5"));
        assertThrows(NotPlayersTurnException.class, () -> table.actions.sow(play.getId(), SEAT_1, MangolaGeometry.pit("h4")));

        assertThat(table.messaging.payloads.isEmpty(), is(true));
        assertThat(placement.version(), is(2));
        assertThat(play.version(), is(0));
    }
}
