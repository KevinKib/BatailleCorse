package org.kevinkib.cardgames.kilodemerde;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.presentation.dto.CardDto;
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

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.card;

/** Under the {@code test} profile an e2e test fixes the deal and the kilo pile of a room's game over HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class KiloDeMerdeFixedDealIT {

    @LocalServerPort
    private int port;

    @Autowired
    private SessionService sessionService;

    private final RestTemplate http = new RestTemplate();

    /** {@code "KS KH"} as the JSON array of the cards the frontend sends. */
    private static String json(String codes) {
        return Arrays.stream(codes.trim().split("\\s+")).map(code -> {
            CardDto dto = CardDto.from(card(code));
            return "{\"rank\":\"" + dto.rank() + "\",\"suit\":\"" + dto.suit() + "\",\"name\":\"" + dto.name() + "\"}";
        }).collect(Collectors.joining(",", "[", "]"));
    }

    private ResponseEntity<String> send(HttpMethod method, String gameId, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("http://localhost:" + port + "/api/test/kilo-de-merde/" + gameId + "/deal", method,
                new HttpEntity<>(body, headers), String.class);
    }

    private RoomCreated roomOfThree() {
        RoomCreated room = sessionService.createRoom("kilo-de-merde", "P0");
        sessionService.joinRoom(new GameId(room.gameId()), "P1");
        sessionService.joinRoom(new GameId(room.gameId()), "P2");
        return room;
    }

    private KiloDeMerde start(RoomCreated room) {
        return (KiloDeMerde) sessionService.startGame(new GameId(room.gameId()), room.hostToken());
    }

    @Test
    void aDealFixedBeforeStartIsTheOneDealtWhenTheRoomStarts() {
        RoomCreated room = roomOfThree();

        ResponseEntity<String> response = send(HttpMethod.PUT, room.gameId(),
                "{\"hands\":[" + json("KS KH KD QC") + "," + json("JS JH QD QH") + "," + json("KC JD JC QS")
                        + "],\"pile\":" + json("AS") + "}");
        KiloDeMerde game = start(room);

        assertThat(response.getStatusCode().value(), is(204));
        assertThat(game.hand(new PlayerId(0)), contains(card("KS"), card("KH"), card("KD"), card("QC")));
        assertThat(game.hand(new PlayerId(1)), contains(card("JS"), card("JH"), card("QD"), card("QH")));
        assertThat(game.hand(new PlayerId(2)), contains(card("KC"), card("JD"), card("JC"), card("QS")));
        assertThat(game.pileSize(), is(1));
    }

    @Test
    void aPileAloneCanBeFixedAndTheHandsStayRandom() {
        RoomCreated room = roomOfThree();

        ResponseEntity<String> response = send(HttpMethod.PUT, room.gameId(), "{\"pile\":" + json("AS 9C") + "}");
        KiloDeMerde game = start(room);

        assertThat(response.getStatusCode().value(), is(204));
        assertThat(game.pileSize(), is(2));
        assertThat(game.hand(new PlayerId(0)).size(), is(4));
    }

    @Test
    void clearingForgetsTheDeal() {
        RoomCreated room = roomOfThree();
        send(HttpMethod.PUT, room.gameId(), "{\"pile\":" + json("AS") + "}");

        ResponseEntity<String> cleared = send(HttpMethod.DELETE, room.gameId(), null);
        KiloDeMerde game = start(room);

        assertThat(cleared.getStatusCode().value(), is(204));
        assertThat(game.pileSize(), is(40));
    }

    @Test
    void anInvalidDealIsRefusedWithA400() {
        RoomCreated room = roomOfThree();

        assertThrows(HttpClientErrorException.BadRequest.class, () -> send(HttpMethod.PUT, room.gameId(),
                "{\"pile\":[{\"rank\":\"NOPE\",\"suit\":\"HEART\",\"name\":\"HEART_NOPE\"}]}"));
        assertThrows(HttpClientErrorException.BadRequest.class, () -> send(HttpMethod.PUT, room.gameId(),
                "{\"hands\":[" + json("KS KH") + "],\"pile\":" + json("KS") + "}")); // a card fixed twice
        assertThrows(HttpClientErrorException.BadRequest.class, () -> send(HttpMethod.PUT, room.gameId(), "{}"));
        assertThrows(HttpClientErrorException.BadRequest.class, () -> send(HttpMethod.PUT, "nope", "{\"pile\":"
                + json("AS") + "}"));
    }
}
