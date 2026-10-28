package org.kevinkib.cardgames.draughts.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kevinkib.cardgames.draughts.domain.DraughtsGames.BLACK;
import static org.kevinkib.cardgames.draughts.domain.DraughtsGames.WHITE;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.draughts.domain.Draughts;
import org.kevinkib.cardgames.draughts.domain.DraughtsFactory;
import org.kevinkib.cardgames.draughts.presentation.api.DraughtsMovePayload;
import org.kevinkib.cardgames.draughts.presentation.dto.DraughtsDto;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.presentation.api.ErrorResponse;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;

class DraughtsWebSocketControllerTest {

    private final DraughtsTable table = new DraughtsTable();

    private RoomCreated createRoom() {
        return table.sessions.createRoom(DraughtsFactory.GAME_TYPE, "Alice",
                GameOptions.of(Map.of("hostColour", "white")));
    }

    @Test
    void startByTheHostBroadcastsTheFirstStateToBothSeats() {
        RoomCreated room = createRoom();
        table.sessions.joinRoom(new GameId(room.gameId()), "Bob");

        table.ws.start(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(table.messaging.payloads).hasSize(2);
        Response first = table.messaging.payloads.get(0);
        assertThat(first.getEventType()).isEqualTo("START");
        DraughtsDto state = (DraughtsDto) first.getState();
        assertThat(state.phase()).isEqualTo("PLAY");
        assertThat(state.availableActions()).containsExactly("MOVE", "OFFER_DRAW");
        assertThat(((DraughtsDto) table.messaging.payloads.get(1).getState()).availableActions())
                .containsExactly("OFFER_DRAW");
    }

    @Test
    void startWithOnlyOnePlayerOrByANonHostRepliesAnErrorToTheSenderOnly() {
        RoomCreated room = createRoom();
        table.ws.start(new GameActionPayload(room.gameId(), room.hostToken()));
        assertThat(table.messaging.payloads).hasSize(1);
        assertThat(table.messaging.payloads.get(0).isSuccess()).isFalse();
        assertThat(table.messaging.seats.get(0)).isEqualTo(WHITE);

        table.messaging.clear();
        String guestToken = table.sessions.joinRoom(new GameId(room.gameId()), "Bob").token();
        table.ws.start(new GameActionPayload(room.gameId(), guestToken));
        assertThat(table.messaging.seats).containsExactly(BLACK);
        assertThat(table.messaging.payloads.get(0).isSuccess()).isFalse();
    }

    @Test
    void startOnAnotherGameTypesRoomIsRejectedAndDoesNotStartThatGame() {
        SessionService both = new SessionService(table.repository,
                new GameFactories(List.of(new DraughtsFactory(), new BullshitFactory())));
        DraughtsWebSocketController ws = new DraughtsWebSocketController(both, table.broadcaster,
                new SeatCommands(both, table.messaging), new DraughtsGameActions(both, table.broadcaster));
        RoomCreated room = both.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        both.joinRoom(new GameId(room.gameId()), "Bob");

        ws.start(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(both.findGame(new GameId(room.gameId()))).isEmpty();
        assertThat(table.messaging.payloads).hasSize(1);
        assertThat(table.messaging.payloads.get(0).isSuccess()).isFalse();
    }

    @Test
    void aMoveIsAuthenticatedByTheSeatToken() {
        Draughts game = table.newGame();
        String gameId = game.getId().uuid().toString();

        table.ws.move(new DraughtsMovePayload(gameId, table.token(game, 0), 32, 28, List.of()));
        assertThat(game.position().pieceAt(28)).isNotNull();
        assertThat(table.messaging.payloads.get(0).getEventType()).isEqualTo("MOVE");

        table.ws.move(new DraughtsMovePayload(gameId, "not-a-token", 17, 22, List.of()));
        assertThat(game.position().pieceAt(22)).isNull();
        assertThat(table.messaging.payloads).hasSize(2);
    }

    @Test
    void aMissingCapturedListStandsForNone() {
        Draughts game = table.newGame();

        table.ws.move(new DraughtsMovePayload(game.getId().uuid().toString(), table.token(game, 0), 32, 28, null));

        assertThat(game.history()).containsExactly("32-28");
    }

    @Test
    void aRefusedMoveRepliesToTheSenderOnlyWithAnErrorCodeAndItsCurrentState() {
        Draughts game = table.newGame();
        String gameId = game.getId().uuid().toString();

        table.ws.move(new DraughtsMovePayload(gameId, table.token(game, 1), 17, 22, List.of()));

        assertThat(table.messaging.seats).containsExactly(BLACK);
        Response error = table.messaging.payloads.get(0);
        assertThat(error.isSuccess()).isFalse();
        assertThat(error.getEventType()).isEqualTo("MOVE");
        assertThat(((ErrorResponse) error).getMessage()).isEqualTo("draughts.errors.notYourTurn");
        assertThat(((DraughtsDto) error.getState()).version()).isZero();

        table.messaging.clear();
        table.ws.move(new DraughtsMovePayload(gameId, table.token(game, 0), 32, 37, List.of()));
        assertThat(((ErrorResponse) table.messaging.payloads.get(0)).getMessage())
                .isEqualTo("draughts.errors.illegalMove");
        assertThat(game.version()).isZero();
    }

    @Test
    void aMoveWithAMissingFieldIsRejectedWithoutAnyEffect() {
        Draughts game = table.newGame();
        String gameId = game.getId().uuid().toString();
        String white = table.token(game, 0);

        table.ws.move(new DraughtsMovePayload(gameId, white, null, 28, List.of()));
        table.ws.move(new DraughtsMovePayload(gameId, white, 32, null, List.of()));

        assertThat(table.messaging.payloads).hasSize(2);
        assertThat(table.messaging.payloads.stream().noneMatch(Response::isSuccess)).isTrue();
        assertThat(game.version()).isZero();
    }

    @Test
    void theDrawCommandsAreAuthenticatedAndAnswered() {
        Draughts game = table.newGame();
        String gameId = game.getId().uuid().toString();

        table.ws.offerDraw(new GameActionPayload(gameId, table.token(game, 0)));
        assertThat(table.messaging.payloads.get(0).getEventType()).isEqualTo("DRAW_OFFERED");
        table.messaging.clear();

        table.ws.declineDraw(new GameActionPayload(gameId, table.token(game, 1)));
        assertThat(table.messaging.payloads.get(0).getEventType()).isEqualTo("DRAW_DECLINED");
        table.messaging.clear();

        table.ws.offerDraw(new GameActionPayload(gameId, table.token(game, 1)));
        table.messaging.clear();
        table.ws.acceptDraw(new GameActionPayload(gameId, table.token(game, 0)));
        assertThat(table.messaging.payloads.get(0).getEventType()).isEqualTo("GAME_OVER");
        assertThat(game.isFinished()).isTrue();
    }

    @Test
    void anOfferThatIsNotAllowedIsRefusedToItsSenderOnly() {
        Draughts game = table.newGame();
        String gameId = game.getId().uuid().toString();
        table.ws.offerDraw(new GameActionPayload(gameId, table.token(game, 0)));
        table.messaging.clear();

        table.ws.offerDraw(new GameActionPayload(gameId, table.token(game, 0)));

        assertThat(table.messaging.seats).containsExactly(WHITE);
        assertThat(((ErrorResponse) table.messaging.payloads.get(0)).getMessage())
                .isEqualTo("draughts.errors.drawOfferPending");
    }

    @Test
    void aMalformedOrUnknownGameIdIsIgnoredWithoutAnyException() {
        for (String rawId : new String[] {"not-a-uuid", "", UUID.randomUUID().toString()}) {
            table.ws.move(new DraughtsMovePayload(rawId, "token", 32, 28, List.of()));
            table.ws.start(new GameActionPayload(rawId, "token"));
            table.ws.offerDraw(new GameActionPayload(rawId, "token"));
        }

        assertThat(table.messaging.payloads).isEmpty();
    }
}
