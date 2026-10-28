package org.kevinkib.cardgames.blackjack.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.blackjack.domain.Blackjack;
import org.kevinkib.cardgames.blackjack.domain.BlackjackFactory;
import org.kevinkib.cardgames.blackjack.domain.Phase;
import org.kevinkib.cardgames.blackjack.presentation.api.BlackjackBetPayload;
import org.kevinkib.cardgames.blackjack.presentation.api.BlackjackErrorResponse;
import org.kevinkib.cardgames.blackjack.presentation.dto.BlackjackDto;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlackjackWebSocketControllerTest {

    private static final PlayerId S0 = new PlayerId(0);
    private static final PlayerId S1 = new PlayerId(1);
    private static final String HAND_1 = "10S 9H 6D 7C 8C 5H 10D 3H";

    private final BlackjackTable table = new BlackjackTable();

    private static BlackjackErrorResponse error(Response response) {
        assertThat(response.isSuccess()).isFalse();
        return (BlackjackErrorResponse) response;
    }

    @Test
    void startByTheHostBroadcastsTheFirstStateToEverySeat() {
        RoomCreated room = table.sessions.createRoom(BlackjackFactory.GAME_TYPE, "Alice");
        table.sessions.joinRoom(new GameId(room.gameId()), "Bob");

        table.ws.start(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(table.messaging.payloads).hasSize(2);
        Response first = table.messaging.payloads.get(0);
        assertThat(first.getEventType()).isEqualTo("START");
        BlackjackDto state = (BlackjackDto) first.getState();
        assertThat(state.phase()).isEqualTo("BETTING");
        assertThat(state.players()).hasSize(2);
    }

    @Test
    void aSoloRoomStartsWithOnlyTheHost() {
        RoomCreated room = table.sessions.createRoom(BlackjackFactory.GAME_TYPE, "Alice");

        table.ws.start(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(table.messaging.payloads).hasSize(1);
        assertThat(table.messaging.payloads.get(0).isSuccess()).isTrue();
        assertThat(((BlackjackDto) table.messaging.payloads.get(0).getState()).solo()).isTrue();
    }

    @Test
    void startByANonHostRepliesAnErrorToTheSenderOnly() {
        RoomCreated room = table.sessions.createRoom(BlackjackFactory.GAME_TYPE, "Alice");
        String guestToken = table.sessions.joinRoom(new GameId(room.gameId()), "Bob").token();

        table.ws.start(new GameActionPayload(room.gameId(), guestToken));

        assertThat(table.messaging.seats).containsExactly(S1);
        assertThat(table.messaging.payloads.get(0).isSuccess()).isFalse();
    }

    @Test
    void startOnAnotherGameTypesRoomIsRejectedAndDoesNotStartThatGame() {
        SessionService both = new SessionService(table.repository,
                new GameFactories(List.of(new BlackjackFactory(), new BullshitFactory())));
        BlackjackWebSocketController ws = new BlackjackWebSocketController(both, table.broadcaster,
                new SeatCommands(both, table.messaging), new BlackjackGameActions(both, table.broadcaster),
                table.messaging);
        RoomCreated room = both.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        both.joinRoom(new GameId(room.gameId()), "Bob");

        ws.start(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(both.findGame(new GameId(room.gameId()))).isEmpty();
        assertThat(table.messaging.payloads).hasSize(1);
        assertThat(table.messaging.payloads.get(0).isSuccess()).isFalse();
    }

    @Test
    void everyCommandIsAuthenticatedByTheSeatToken() {
        Blackjack game = table.start(2, HAND_1);
        String id = game.getId().uuid().toString();

        table.ws.bet(new BlackjackBetPayload(id, "not-a-token", 10));
        table.ws.bet(new BlackjackBetPayload(id, null, 10));
        table.ws.hit(new GameActionPayload(id, "forged"));

        assertThat(table.messaging.payloads).isEmpty();
        assertThat(game.version()).isZero();

        table.ws.bet(new BlackjackBetPayload(id, table.token(game, 0), 10));
        assertThat(table.messaging.payloads.get(0).getEventType()).isEqualTo("BET");
    }

    @Test
    void theFullFlowRunsThroughTheFiveCommands() {
        Blackjack game = table.start(2, "5S 9H 6D 6C 8C 5H 2D 3H 4C");
        String id = game.getId().uuid().toString();
        String t0 = table.token(game, 0);
        String t1 = table.token(game, 1);

        table.ws.bet(new BlackjackBetPayload(id, t0, 10));
        table.ws.bet(new BlackjackBetPayload(id, t1, 10));
        table.ws.hit(new GameActionPayload(id, t0));       // 5S 6C 2D = 13
        table.ws.doubleDown(new GameActionPayload(id, t0)); // not allowed after a hit
        table.ws.stand(new GameActionPayload(id, t0));
        table.ws.stand(new GameActionPayload(id, t1));

        assertThat(game.phase()).isEqualTo(Phase.BETTING);
        assertThat(game.handNumber()).isEqualTo(2);
    }

    @Test
    void aSplitIsReadFromThePairAndRefusedOtherwise() {
        Blackjack game = table.start(1, "8S 6D 8C 5H 3H 10D 4S");
        String id = game.getId().uuid().toString();
        String t0 = table.token(game, 0);
        table.ws.bet(new BlackjackBetPayload(id, t0, 10));

        table.ws.split(new GameActionPayload(id, t0));

        assertThat(game.seat(S0).hands()).hasSize(2);
        table.messaging.clear();
        table.ws.split(new GameActionPayload(id, t0));
        assertThat(error(table.messaging.payloads.get(0)).getErrorCode()).isEqualTo("ACTION_NOT_ALLOWED");
    }

    @Test
    void aRefusalCarriesItsCodeAndTheSendersCurrentStateAndGoesToTheSenderOnly() {
        Blackjack game = table.start(2, HAND_1);
        String id = game.getId().uuid().toString();
        String t0 = table.token(game, 0);
        String t1 = table.token(game, 1);

        table.ws.hit(new GameActionPayload(id, t0));
        assertThat(table.messaging.seats).containsExactly(S0);
        BlackjackErrorResponse wrongPhase = error(table.messaging.payloads.get(0));
        assertThat(wrongPhase.getErrorCode()).isEqualTo("WRONG_PHASE");
        assertThat(wrongPhase.getEventType()).isEqualTo("HIT");
        assertThat(wrongPhase.getMessage()).isNotBlank();
        assertThat(((BlackjackDto) wrongPhase.getState()).phase()).isEqualTo("BETTING");

        table.messaging.clear();
        table.ws.bet(new BlackjackBetPayload(id, t0, 51));
        assertThat(error(table.messaging.payloads.get(0)).getErrorCode()).isEqualTo("INVALID_BET");
        table.ws.bet(new BlackjackBetPayload(id, t0, 10));
        table.messaging.clear();
        table.ws.bet(new BlackjackBetPayload(id, t0, 10));
        assertThat(error(table.messaging.payloads.get(0)).getErrorCode()).isEqualTo("ALREADY_BET");
        table.ws.bet(new BlackjackBetPayload(id, t1, 10));
        table.messaging.clear();
        table.ws.stand(new GameActionPayload(id, t1));
        assertThat(error(table.messaging.payloads.get(0)).getErrorCode()).isEqualTo("NOT_YOUR_TURN");
        assertThat(table.messaging.seats).containsExactly(S1);
    }

    @Test
    void aFinishedGameAnswersGameFinished() {
        Blackjack game = table.start(2, HAND_1);
        game.forfeit(S1);
        String id = game.getId().uuid().toString();

        table.ws.bet(new BlackjackBetPayload(id, table.token(game, 0), 10));

        assertThat(error(table.messaging.payloads.get(0)).getErrorCode()).isEqualTo("GAME_FINISHED");
    }

    @Test
    void anEliminatedSeatIsRefusedAsNotItsTurn() {
        Blackjack game = table.start(3, HAND_1 + " 2C");
        game.forfeit(new PlayerId(2));
        String id = game.getId().uuid().toString();

        table.ws.hit(new GameActionPayload(id, table.token(game, 2)));

        assertThat(table.messaging.payloads).hasSize(1);
        assertThat(table.messaging.payloads.get(0).isSuccess()).isFalse();
    }

    @Test
    void aBetWithoutAnAmountIsRejectedWithoutAnyEffect() {
        Blackjack game = table.start(2, HAND_1);

        table.ws.bet(new BlackjackBetPayload(game.getId().uuid().toString(), table.token(game, 0), null));

        assertThat(table.messaging.payloads).hasSize(1);
        assertThat(table.messaging.payloads.get(0).isSuccess()).isFalse();
        assertThat(game.version()).isZero();
    }
}
