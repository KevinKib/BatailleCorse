package org.kevinkib.cardgames.kobo;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;

/** Under the {@code test} profile an e2e test fixes the deal of a room over HTTP before the game starts. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class KoboFixedDealIT {

    @LocalServerPort
    private int port;

    @Autowired
    private SessionService sessionService;

    private final RestTemplate http = new RestTemplate();

    private ResponseEntity<String> send(HttpMethod method, String gameId, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("http://localhost:" + port + "/api/test/kobo/" + gameId + "/deal", method,
                new HttpEntity<>(json, headers), String.class);
    }

    private Kobo start(RoomCreated room) {
        GameId id = new GameId(room.gameId());
        sessionService.joinRoom(id, "P1");
        return (Kobo) sessionService.startGame(id, room.hostToken());
    }

    @Test
    void aDealFixedBeforeStartIsTheOneDealtWhenTheRoomStarts() {
        RoomCreated room = sessionService.createRoom("kobo", "P0");

        ResponseEntity<String> response = send(HttpMethod.PUT, room.gameId().toString(),
                "{\"round\":1,\"cards\":[\"KH\",\"10S\",\"AD\",\"2C\",\"7H\",\"7D\",\"7C\",\"7S\",\"QS\"]}");
        Kobo kobo = start(room);

        assertThat(response.getStatusCode().value(), is(204));
        assertThat(kobo.slots(new PlayerId(0)).stream().map(s -> s.card()).toList(),
                contains(card("KH"), card("10S"), card("AD"), card("2C")));
        assertThat(kobo.slots(new PlayerId(1)).stream().map(s -> s.card()).toList(),
                contains(card("7H"), card("7D"), card("7C"), card("7S")));
    }

    @Test
    void anInvalidDealIsRefusedWithA400() {
        RoomCreated room = sessionService.createRoom("kobo", "P0");

        HttpClientErrorException e = assertThrows(HttpClientErrorException.BadRequest.class,
                () -> send(HttpMethod.PUT, room.gameId().toString(), "{\"cards\":[\"KH\",\"KH\"]}"));
        assertThat(e.getResponseBodyAsString().contains("error"), is(true));
    }

    @Test
    void aClearedDealIsRandomAgain() {
        RoomCreated room = sessionService.createRoom("kobo", "P0");
        send(HttpMethod.PUT, room.gameId().toString(), "{\"cards\":[\"KH\",\"KD\",\"KC\",\"KS\"]}");

        assertThat(send(HttpMethod.DELETE, room.gameId().toString(), null).getStatusCode().value(), is(204));
        Kobo kobo = start(room);

        assertThat(kobo.slots(new PlayerId(0)).stream().map(s -> s.card()).toList()
                .equals(java.util.List.of(card("KH"), card("KD"), card("KC"), card("KS"))), is(false));
    }
}
