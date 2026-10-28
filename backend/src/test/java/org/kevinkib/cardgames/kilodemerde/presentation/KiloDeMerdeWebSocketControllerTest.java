package org.kevinkib.cardgames.kilodemerde.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeFactory;
import org.kevinkib.cardgames.kilodemerde.domain.Phase;
import org.kevinkib.cardgames.kilodemerde.presentation.api.KiloDeMerdePassPayload;
import org.kevinkib.cardgames.kilodemerde.presentation.dto.KiloDeMerdeDto;
import org.kevinkib.cardgames.presentation.api.ErrorResponse;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.ALL_SETS;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.NO_SET;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.card;

class KiloDeMerdeWebSocketControllerTest {

    private static final PlayerId S0 = new PlayerId(0);
    private static final PlayerId S1 = new PlayerId(1);

    private final KiloDeMerdeTable table = new KiloDeMerdeTable();

    private static CardDto dto(String code) {
        return CardDto.from(card(code));
    }

    @Test
    void startByTheHostBroadcastsTheFirstStateToEverySeat() {
        RoomCreated room = table.sessions.createRoom(KiloDeMerdeFactory.GAME_TYPE, "Alice");
        table.sessions.joinRoom(new GameId(room.gameId()), "Bob");
        table.sessions.joinRoom(new GameId(room.gameId()), "Cleo");

        table.ws.start(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(table.messaging.payloads).hasSize(3);
        Response first = table.messaging.payloads.get(0);
        assertThat(first.getEventType()).isEqualTo("START");
        KiloDeMerdeDto state = (KiloDeMerdeDto) first.getState();
        assertThat(state.phase()).isEqualTo("PASSING");
        assertThat(state.hand()).hasSize(4);
        assertThat(state.players()).hasSize(3);
    }

    @Test
    void startWithTwoPlayersOrByANonHostRepliesAnErrorToTheSenderOnly() {
        RoomCreated room = table.sessions.createRoom(KiloDeMerdeFactory.GAME_TYPE, "Alice");
        String guestToken = table.sessions.joinRoom(new GameId(room.gameId()), "Bob").token();

        table.ws.start(new GameActionPayload(room.gameId(), room.hostToken()));
        assertThat(table.messaging.payloads).hasSize(1);
        assertThat(table.messaging.payloads.get(0).isSuccess()).isFalse();
        assertThat(table.messaging.seats.get(0)).isEqualTo(S0);

        table.messaging.clear();
        table.ws.start(new GameActionPayload(room.gameId(), guestToken));
        assertThat(table.messaging.seats).containsExactly(S1);
        assertThat(table.messaging.payloads.get(0).isSuccess()).isFalse();
    }

    @Test
    void startOnAnotherGameTypesRoomIsRejectedAndDoesNotStartThatGame() {
        SessionService both = new SessionService(table.repository,
                new GameFactories(List.of(new KiloDeMerdeFactory(), new BullshitFactory())));
        KiloDeMerdeWebSocketController ws = new KiloDeMerdeWebSocketController(both, table.broadcaster,
                new SeatCommands(both, table.messaging), new KiloDeMerdeGameActions(both, table.broadcaster));
        RoomCreated room = both.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        both.joinRoom(new GameId(room.gameId()), "Bob");

        ws.start(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(both.findGame(new GameId(room.gameId()))).isEmpty();
        assertThat(table.messaging.payloads).hasSize(1);
        assertThat(table.messaging.payloads.get(0).isSuccess()).isFalse();
    }

    @Test
    void aPassIsAuthenticatedByTheSeatToken() {
        KiloDeMerde game = table.start(NO_SET);
        String id = game.getId().uuid().toString();

        table.ws.pass(new KiloDeMerdePassPayload(id, table.token(game, 0), dto("QC")));
        assertThat(game.chosen(S0)).contains(card("QC"));
        assertThat(table.messaging.payloads.get(0).getEventType()).isEqualTo("PASS_CHOSEN");

        table.messaging.clear();
        table.ws.pass(new KiloDeMerdePassPayload(id, "not-a-token", dto("QD")));
        table.ws.pass(new KiloDeMerdePassPayload(id, null, dto("QD")));
        assertThat(game.chosen(S1)).isEmpty();
        assertThat(table.messaging.payloads).isEmpty();
    }

    @Test
    void aSlapIsAuthenticatedAndOpensTheRace() {
        KiloDeMerde game = table.start(ALL_SETS);
        String id = game.getId().uuid().toString();

        table.ws.slap(new GameActionPayload(id, "forged"));
        assertThat(game.phase()).isEqualTo(Phase.PASSING);
        table.ws.slap(new GameActionPayload(id, table.token(game, 2)));

        assertThat(game.phase()).isEqualTo(Phase.RACE);
        assertThat(game.slapOrder()).containsExactly(new PlayerId(2));
    }

    @Test
    void aRefusedPassRepliesToTheSenderOnlyWithItsCurrentState() {
        KiloDeMerde game = table.start(NO_SET);
        String id = game.getId().uuid().toString();

        table.ws.pass(new KiloDeMerdePassPayload(id, table.token(game, 1), dto("KS"))); // a card of another hand

        assertThat(table.messaging.seats).containsExactly(S1);
        Response error = table.messaging.payloads.get(0);
        assertThat(error.isSuccess()).isFalse();
        assertThat(error.getEventType()).isEqualTo("PASS_CHOSEN");
        assertThat(((ErrorResponse) error).getMessage()).isNotBlank();
        assertThat(((KiloDeMerdeDto) error.getState()).hand()).contains(dto("JS"));
        assertThat(game.version()).isZero();
    }

    @Test
    void aSecondSlapOfTheSameSeatIsRefusedToItsSenderAlone() {
        KiloDeMerde game = table.start(ALL_SETS);
        String id = game.getId().uuid().toString();
        table.ws.slap(new GameActionPayload(id, table.token(game, 1)));
        table.messaging.clear();

        table.ws.slap(new GameActionPayload(id, table.token(game, 1)));

        assertThat(table.messaging.seats).containsExactly(S1);
        assertThat(table.messaging.payloads.get(0).isSuccess()).isFalse();
        assertThat(table.messaging.payloads.get(0).getEventType()).isEqualTo("SLAP");
        assertThat(game.slapOrder()).containsExactly(S1);
    }

    @Test
    void aPassWithoutACardOrWithAnUnknownCardIsRejectedWithoutAnyEffect() {
        KiloDeMerde game = table.start(NO_SET);
        String id = game.getId().uuid().toString();
        String token = table.token(game, 0);

        table.ws.pass(new KiloDeMerdePassPayload(id, token, null));
        table.ws.pass(new KiloDeMerdePassPayload(id, token, new CardDto("NOPE", "HEART", "HEART_NOPE")));

        assertThat(table.messaging.payloads).hasSize(2);
        assertThat(table.messaging.payloads.stream().noneMatch(Response::isSuccess)).isTrue();
        assertThat(game.version()).isZero();
    }
}
