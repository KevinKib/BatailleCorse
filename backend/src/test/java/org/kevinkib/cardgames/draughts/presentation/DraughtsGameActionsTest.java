package org.kevinkib.cardgames.draughts.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kevinkib.cardgames.draughts.domain.DraughtsGames.BLACK;
import static org.kevinkib.cardgames.draughts.domain.DraughtsGames.WHITE;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.draughts.domain.Colour;
import org.kevinkib.cardgames.draughts.domain.Draughts;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;
import org.kevinkib.cardgames.draughts.domain.IllegalMoveException;
import org.kevinkib.cardgames.draughts.domain.NotPlayersTurnException;
import org.kevinkib.cardgames.draughts.presentation.dto.DraughtsDto;
import org.kevinkib.cardgames.draughts.presentation.dto.event.DraughtsEventData;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.presentation.api.Response;

class DraughtsGameActionsTest {

    private final DraughtsTable table = new DraughtsTable();

    private static DraughtsPosition layout(Colour toMove, String... specs) {
        char[] chars = ".".repeat(50).toCharArray();
        for (String spec : specs) {
            chars[Integer.parseInt(spec.substring(1)) - 1] = spec.charAt(0);
        }
        return DraughtsPosition.fromLayout(new String(chars), toMove);
    }

    @Test
    void aMoveBroadcastsOneStatePerSeatWithAMoveEvent() throws Exception {
        Draughts game = table.newGame();

        table.actions.move(game.getId(), WHITE, 32, 28, List.of());

        assertThat(table.messaging.seats).containsExactly(WHITE, BLACK);
        Response first = table.messaging.payloads.get(0);
        assertThat(first.isSuccess()).isTrue();
        assertThat(first.getEventType()).isEqualTo("MOVE");
        DraughtsEventData data = (DraughtsEventData) first.getEventData();
        assertThat(data.seat()).isZero();
        assertThat(data.from()).isEqualTo(32);
        assertThat(data.to()).isEqualTo(28);
        assertThat(data.path()).containsExactly(28);
        assertThat(data.captures()).isEmpty();
        assertThat(data.promotes()).isFalse();
        DraughtsDto state = (DraughtsDto) first.getState();
        assertThat(state.board().get(27)).isEqualTo("w");
        assertThat(state.currentSeat()).isEqualTo(1);
    }

    @Test
    void aCaptureCarriesThePathAndTheCapturedSquares() throws Exception {
        Draughts game = table.install(Draughts.fromPosition(GameId.generate(), WHITE,
                layout(Colour.WHITE, "w32", "b28", "b19", "b1"), true));

        table.actions.move(game.getId(), WHITE, 32, 14, List.of(28, 19));

        DraughtsEventData data = (DraughtsEventData) table.messaging.payloads.get(0).getEventData();
        assertThat(data.path()).containsExactly(23, 14);
        assertThat(data.captures()).containsExactly(28, 19);
    }

    @Test
    void aRejectedCommandBroadcastsNothingAndPropagates() throws Exception {
        Draughts game = table.newGame();

        assertThatThrownBy(() -> table.actions.move(game.getId(), BLACK, 17, 22, List.of()))
                .isInstanceOf(NotPlayersTurnException.class);
        assertThatThrownBy(() -> table.actions.move(game.getId(), WHITE, 32, 37, List.of()))
                .isInstanceOf(IllegalMoveException.class);

        assertThat(table.messaging.payloads).isEmpty();
        assertThat(game.version()).isZero();
    }

    @Test
    void theMoveThatEndsTheGameIsAnnouncedAsGameOverWithTheMoveData() throws Exception {
        Draughts game = table.install(Draughts.fromPosition(GameId.generate(), WHITE,
                layout(Colour.WHITE, "w27", "b21"), true));

        table.actions.move(game.getId(), WHITE, 27, 16, List.of(21));

        Response over = table.messaging.payloads.get(0);
        assertThat(over.getEventType()).isEqualTo("GAME_OVER");
        DraughtsEventData data = (DraughtsEventData) over.getEventData();
        assertThat(data.from()).isEqualTo(27);
        assertThat(data.path()).containsExactly(16);
        assertThat(data.captures()).containsExactly(21);
        assertThat(((DraughtsDto) over.getState()).outcome()).isEqualTo(new DraughtsDto.Finished(0, "NO_PIECES"));
        assertThat(table.messaging.payloads).hasSize(2);
    }

    @Test
    void drawOffersAreBroadcastWithTheirSeat() throws Exception {
        Draughts game = table.newGame();

        table.actions.offerDraw(game.getId(), WHITE);
        Response offered = table.messaging.payloads.get(0);
        assertThat(offered.getEventType()).isEqualTo("DRAW_OFFERED");
        assertThat(((DraughtsEventData) offered.getEventData()).seat()).isZero();
        assertThat(((DraughtsEventData) offered.getEventData()).from()).isNull();
        assertThat(((DraughtsDto) offered.getState()).drawOffer()).isEqualTo(new DraughtsDto.DrawOfferDto(0));
        table.messaging.clear();

        table.actions.declineDraw(game.getId(), BLACK);
        Response declined = table.messaging.payloads.get(0);
        assertThat(declined.getEventType()).isEqualTo("DRAW_DECLINED");
        assertThat(((DraughtsEventData) declined.getEventData()).seat()).isEqualTo(1);
    }

    @Test
    void anAcceptedDrawIsAGameOver() throws Exception {
        Draughts game = table.newGame();
        table.actions.offerDraw(game.getId(), BLACK);
        table.messaging.clear();

        table.actions.acceptDraw(game.getId(), WHITE);

        Response over = table.messaging.payloads.get(0);
        assertThat(over.getEventType()).isEqualTo("GAME_OVER");
        assertThat(((DraughtsDto) over.getState()).outcome()).isEqualTo(new DraughtsDto.Finished(null, "DRAW_AGREED"));
    }
}
