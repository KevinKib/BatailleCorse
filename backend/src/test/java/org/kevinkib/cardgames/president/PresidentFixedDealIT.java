package org.kevinkib.cardgames.president;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.card;

/** Under the {@code test} profile an e2e test fixes the deal of a room over HTTP before the game starts. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PresidentFixedDealIT {

    @LocalServerPort
    private int port;

    @Autowired
    private SessionService sessionService;

    private final RestTemplate http = new RestTemplate();

    private ResponseEntity<String> send(HttpMethod method, String gameId, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("http://localhost:" + port + "/api/test/president/" + gameId + "/deal", method,
                new HttpEntity<>(json, headers), String.class);
    }

    private President start(RoomCreated room) {
        GameId id = new GameId(room.gameId());
        sessionService.joinRoom(id, "P1");
        sessionService.joinRoom(id, "P2");
        return (President) sessionService.startGame(id, room.hostToken());
    }

    @Test
    void aDealFixedBeforeStartIsTheOneDealtWhenTheRoomStarts() {
        RoomCreated room = sessionService.createRoom("president", "P0");

        ResponseEntity<String> response = send(HttpMethod.PUT, room.gameId(),
                "{\"round\":1,\"cards\":[\"KH\",\"10S\",\"AD\",\"2C\",\"7H\",\"7D\"]}");
        President president = start(room);

        assertThat(response.getStatusCode().value(), is(204));
        assertThat(president.hand(new PlayerId(0)), hasItems(card("KH"), card("2C")));
        assertThat(president.hand(new PlayerId(1)), hasItems(card("10S"), card("7H")));
        assertThat(president.hand(new PlayerId(2)), hasItems(card("AD"), card("7D")));
        assertThat(president.currentSeat(), is(new PlayerId(0)));
    }

    @Test
    void theKingOfHeartsHolderLeadsTheFirstRound() {
        RoomCreated room = sessionService.createRoom("president", "P0");

        send(HttpMethod.PUT, room.gameId(), "{\"cards\":[\"4D\",\"5D\",\"KH\"]}");
        President president = start(room);

        assertThat(president.currentSeat(), is(new PlayerId(2)));
    }

    @Test
    void anInvalidDealIsRefusedWithA400() {
        RoomCreated room = sessionService.createRoom("president", "P0");

        HttpClientErrorException e = assertThrows(HttpClientErrorException.BadRequest.class,
                () -> send(HttpMethod.PUT, room.gameId(), "{\"cards\":[\"KH\",\"KH\"]}"));
        assertThat(e.getResponseBodyAsString().contains("error"), is(true));
        assertThrows(HttpClientErrorException.BadRequest.class,
                () -> send(HttpMethod.PUT, room.gameId(), "{\"cards\":[\"1H\"]}"));
        assertThrows(HttpClientErrorException.BadRequest.class,
                () -> send(HttpMethod.PUT, room.gameId(), "{}"));
    }

    @Test
    void aClearedDealIsRandomAgain() {
        RoomCreated room = sessionService.createRoom("president", "P0");
        send(HttpMethod.PUT, room.gameId(), "{\"cards\":[\"KH\",\"KD\",\"KC\",\"KS\",\"AH\",\"AD\"]}");

        assertThat(send(HttpMethod.DELETE, room.gameId(), null).getStatusCode().value(), is(204));
        President president = start(room);

        assertThat(president.hand(new PlayerId(0)).containsAll(java.util.List.of(card("KH"), card("KS"))), is(false));
    }
}
