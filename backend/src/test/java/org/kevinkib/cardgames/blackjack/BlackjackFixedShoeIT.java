package org.kevinkib.cardgames.blackjack;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.blackjack.domain.Blackjack;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
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
import static org.kevinkib.cardgames.blackjack.presentation.BlackjackTable.card;

/** Under the {@code test} profile an e2e test fixes the shoe of each hand over HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BlackjackFixedShoeIT {

    @LocalServerPort
    private int port;

    @Autowired
    private SessionService sessionService;

    private final RestTemplate http = new RestTemplate();

    private ResponseEntity<String> send(HttpMethod method, String gameId, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("http://localhost:" + port + "/api/test/blackjack/" + gameId + "/deal", method,
                new HttpEntity<>(json, headers), String.class);
    }

    private Blackjack start(RoomCreated room) throws Exception {
        GameId id = new GameId(room.gameId());
        sessionService.joinRoom(id, "P1");
        Blackjack game = (Blackjack) sessionService.startGame(id, room.hostToken());
        game.bet(new PlayerId(0), 10);
        game.bet(new PlayerId(1), 10);
        return game;
    }

    @Test
    void aShoeFixedBeforeStartIsTheOneDealtWhenTheBetsAreIn() throws Exception {
        RoomCreated room = sessionService.createRoom("blackjack", "P0");

        ResponseEntity<String> response = send(HttpMethod.PUT, room.gameId(),
                "{\"hands\":[[\"AS\",\"KC\",\"6D\",\"9H\",\"QC\",\"5H\"]]}");
        Blackjack game = start(room);

        assertThat(response.getStatusCode().value(), is(204));
        assertThat(game.seat(new PlayerId(0)).hands().get(0).cards(), contains(card("AS"), card("9H")));
        assertThat(game.seat(new PlayerId(1)).hands().get(0).cards(), contains(card("KC"), card("QC")));
        assertThat(game.dealerUpCard(), is(card("6D")));
    }

    @Test
    void theSecondHandCanBeFixedToo() throws Exception {
        RoomCreated room = sessionService.createRoom("blackjack", "P0");
        send(HttpMethod.PUT, room.gameId(),
                "{\"hands\":[[\"10S\",\"9H\",\"6D\",\"7C\",\"8C\",\"5H\",\"10D\",\"10C\"],[\"AS\",\"KC\",\"6D\",\"9H\",\"QC\",\"5H\"]]}");
        Blackjack game = start(room);
        game.stand(new PlayerId(0));
        game.stand(new PlayerId(1));
        game.bet(new PlayerId(0), 10);
        game.bet(new PlayerId(1), 10);

        assertThat(game.handNumber(), is(2));
        assertThat(game.seat(new PlayerId(1)).hands().get(0).cards(), contains(card("KC"), card("QC")));
    }

    @Test
    void clearingForgetsTheShoe() throws Exception {
        RoomCreated room = sessionService.createRoom("blackjack", "P0");
        send(HttpMethod.PUT, room.gameId(), "{\"hands\":[[\"AS\",\"KC\",\"6D\",\"9H\",\"QC\",\"5H\"]]}");

        ResponseEntity<String> cleared = send(HttpMethod.DELETE, room.gameId(), null);
        Blackjack game = start(room);

        assertThat(cleared.getStatusCode().value(), is(204));
        boolean stillFixed = game.dealerUpCard().equals(card("6D"))
                && game.seat(new PlayerId(0)).hands().get(0).cards().get(0).equals(card("AS"));
        assertThat(stillFixed, is(false));
    }

    @Test
    void anInvalidShoeIsRefusedWithA400() {
        RoomCreated room = sessionService.createRoom("blackjack", "P0");

        assertThrows(HttpClientErrorException.BadRequest.class,
                () -> send(HttpMethod.PUT, room.gameId(), "{\"hands\":[[\"ZZ\"]]}"));
        assertThrows(HttpClientErrorException.BadRequest.class,
                () -> send(HttpMethod.PUT, room.gameId(), "{}"));
        assertThrows(HttpClientErrorException.BadRequest.class,
                () -> send(HttpMethod.PUT, "nope", "{\"hands\":[[\"AS\"]]}"));
    }
}
