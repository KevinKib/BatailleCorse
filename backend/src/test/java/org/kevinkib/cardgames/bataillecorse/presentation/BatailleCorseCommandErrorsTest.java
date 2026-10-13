package org.kevinkib.cardgames.bataillecorse.presentation;

import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.support.SessionTable;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Contract of a refused Bataille Corse command: the error goes to the sender's seat alone (never to the game
 * topic), carries the sender's current state when a game runs, and keeps the domain's message. An
 * unauthenticated command (malformed id, unknown token) is ignored: nothing is sent and nothing is thrown.
 */
class BatailleCorseCommandErrorsTest {

    private final SessionTable table = new SessionTable(new BatailleCorseFactory(), new BullshitFactory());
    private final BatailleCorseStateBroadcaster broadcaster = new BatailleCorseStateBroadcaster(table.messaging);
    private final BatailleCorseWebSocketController ws = new BatailleCorseWebSocketController(
            table.sessions, new SeatCommands(table.sessions, table.messaging),
            new BatailleCorseGameActions(table.sessions, broadcaster), broadcaster);

    private record Room(String gameId, String hostToken, String guestToken) { }

    private Room started() {
        var room = table.sessions.createRoom(BatailleCorseFactory.GAME_TYPE, "Alice");
        GameId id = new GameId(room.gameId());
        String guest = table.sessions.joinRoom(id, "Bob").token();
        table.sessions.startGame(id, room.hostToken(), org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse.class);
        return new Room(room.gameId(), room.hostToken(), guest);
    }

    private Response onlyReply(PlayerId expectedSeat) {
        assertThat(table.messaging.toGame, is(empty()));
        assertThat(table.messaging.seats, is(List.of(expectedSeat)));
        return table.messaging.payloads.get(0);
    }

    @Test
    void aSendOutOfTurnIsRefusedToTheSenderAloneWithItsState() {
        Room room = started();

        ws.send(new GameActionPayload(room.gameId(), room.guestToken()));

        Response error = onlyReply(new PlayerId(1));
        assertThat(error.isSuccess(), is(false));
        assertThat(error.getEventType(), is("SEND"));
        assertThat(error.getMessage(), not(is(nullValue())));
        assertThat(error.getState(), instanceOf(BatailleCorseDto.class));
    }

    @Test
    void aSlapOnAnEmptyPileIsRefusedToTheSenderAloneWithItsState() {
        Room room = started();

        ws.slap(new GameActionPayload(room.gameId(), room.hostToken()));

        Response error = onlyReply(new PlayerId(0));
        assertThat(error.isSuccess(), is(false));
        assertThat(error.getEventType(), is("SLAP"));
        assertThat(error.getState(), notNullValue());
    }

    @Test
    void aGrabOfAPileThatCannotBeGrabbedIsRefusedToTheSenderAloneWithItsState() {
        Room room = started();

        ws.grab(new GameActionPayload(room.gameId(), room.guestToken()));

        Response error = onlyReply(new PlayerId(1));
        assertThat(error.isSuccess(), is(false));
        assertThat(error.getEventType(), is("GRAB"));
        assertThat(error.getState(), instanceOf(BatailleCorseDto.class));
    }

    @Test
    void aStartRefusedOnALobbyCarriesNoState() {
        var room = table.sessions.createRoom(BatailleCorseFactory.GAME_TYPE, "Alice");

        ws.start(new GameActionPayload(room.gameId(), room.hostToken()));

        Response error = onlyReply(new PlayerId(0));
        assertThat(error.getEventType(), is("START"));
        assertThat(error.getState(), is(nullValue()));
    }

    @Test
    void anUnknownTokenIsIgnored() {
        Room room = started();

        ws.send(new GameActionPayload(room.gameId(), UUID.randomUUID().toString()));
        ws.slap(new GameActionPayload(room.gameId(), UUID.randomUUID().toString()));
        ws.grab(new GameActionPayload(room.gameId(), UUID.randomUUID().toString()));
        ws.start(new GameActionPayload(room.gameId(), UUID.randomUUID().toString()));

        assertThat(table.messaging.toGame, is(empty()));
        assertThat(table.messaging.seats, is(empty()));
    }

    @Test
    void aMalformedOrUnknownGameIdIsIgnoredWithoutThrowing() {
        started();

        assertDoesNotThrow(() -> ws.send(new GameActionPayload("nope", "t")));
        assertDoesNotThrow(() -> ws.slap(new GameActionPayload(UUID.randomUUID().toString(), "t")));
        assertDoesNotThrow(() -> ws.grab(new GameActionPayload("nope", "t")));
        assertDoesNotThrow(() -> ws.start(new GameActionPayload("nope", "t")));

        assertThat(table.messaging.toGame, is(empty()));
        assertThat(table.messaging.seats, is(empty()));
    }
}
