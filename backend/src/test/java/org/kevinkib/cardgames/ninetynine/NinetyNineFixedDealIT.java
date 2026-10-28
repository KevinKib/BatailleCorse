package org.kevinkib.cardgames.ninetynine;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNine;
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
import static org.kevinkib.cardgames.ninetynine.presentation.NinetyNineTable.card;

/** Under the {@code test} profile an e2e test fixes the deal, the first seat and the reshuffle over HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NinetyNineFixedDealIT {

    @LocalServerPort
    private int port;

    @Autowired
    private SessionService sessionService;

    private final RestTemplate http = new RestTemplate();

    private ResponseEntity<String> send(HttpMethod method, String gameId, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("http://localhost:" + port + "/api/test/ninety-nine/" + gameId + "/deal", method,
                new HttpEntity<>(json, headers), String.class);
    }

    private NinetyNine start(RoomCreated room) {
        GameId id = new GameId(room.gameId());
        sessionService.joinRoom(id, "P1");
        return (NinetyNine) sessionService.startGame(id, room.hostToken());
    }

    @Test
    void aDealFixedBeforeStartIsTheOneDealtWhenTheRoomStarts() {
        RoomCreated room = sessionService.createRoom("ninety-nine", "P0");

        ResponseEntity<String> response = send(HttpMethod.PUT, room.gameId(),
                "{\"round\":1,\"cards\":[\"KH\",\"10S\",\"AD\",\"2C\",\"7H\",\"7D\"],\"firstSeat\":1}");
        NinetyNine game = start(room);

        assertThat(response.getStatusCode().value(), is(204));
        assertThat(game.hand(new PlayerId(0)), contains(card("KH"), card("AD"), card("7H")));
        assertThat(game.hand(new PlayerId(1)), contains(card("10S"), card("2C"), card("7D")));
        assertThat(game.currentSeat(), is(new PlayerId(1)));
    }

    @Test
    void theSecondRoundCanBeFixedToo() throws Exception {
        RoomCreated room = sessionService.createRoom("ninety-nine", "P0");
        send(HttpMethod.PUT, room.gameId(), "{\"round\":1,\"cards\":[\"KS\",\"7H\",\"9S\",\"8H\",\"4S\",\"2H\"],\"firstSeat\":0}");
        send(HttpMethod.PUT, room.gameId(), "{\"round\":2,\"cards\":[\"QS\",\"8D\"]}");
        NinetyNine game = start(room);
        game.play(new PlayerId(0), card("KS"), null);
        game.play(new PlayerId(1), card("7H"), null);
        game.nextRound();

        assertThat(game.hand(new PlayerId(0)).get(0), is(card("QS")));
        assertThat(game.hand(new PlayerId(1)).get(0), is(card("8D")));
    }

    @Test
    void theNextReshuffleCanBeFixed() {
        RoomCreated room = sessionService.createRoom("ninety-nine", "P0");

        ResponseEntity<String> response = send(HttpMethod.PUT, room.gameId(), "{\"reshuffle\":[\"2C\",\"3C\"]}");

        assertThat(response.getStatusCode().value(), is(204));
    }

    @Test
    void clearingForgetsTheDeal() {
        RoomCreated room = sessionService.createRoom("ninety-nine", "P0");
        send(HttpMethod.PUT, room.gameId(), "{\"cards\":[\"KH\",\"10S\"],\"firstSeat\":1}");

        ResponseEntity<String> cleared = send(HttpMethod.DELETE, room.gameId(), null);
        NinetyNine game = start(room);

        assertThat(cleared.getStatusCode().value(), is(204));
        boolean stillFixed = game.hand(new PlayerId(0)).get(0).equals(card("KH"))
                && game.hand(new PlayerId(1)).get(0).equals(card("10S")) && game.currentSeat().equals(new PlayerId(1));
        assertThat(stillFixed, is(false));
    }

    @Test
    void anInvalidDealIsRefusedWithA400() {
        RoomCreated room = sessionService.createRoom("ninety-nine", "P0");

        assertThrows(HttpClientErrorException.BadRequest.class,
                () -> send(HttpMethod.PUT, room.gameId(), "{\"cards\":[\"ZZ\"]}"));
        assertThrows(HttpClientErrorException.BadRequest.class,
                () -> send(HttpMethod.PUT, room.gameId(), "{\"cards\":[\"KH\",\"KH\"]}"));
        assertThrows(HttpClientErrorException.BadRequest.class,
                () -> send(HttpMethod.PUT, room.gameId(), "{}"));
        assertThrows(HttpClientErrorException.BadRequest.class,
                () -> send(HttpMethod.PUT, "nope", "{\"cards\":[\"KH\"]}"));
    }
}
