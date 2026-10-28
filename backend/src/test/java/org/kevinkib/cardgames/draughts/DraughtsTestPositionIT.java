package org.kevinkib.cardgames.draughts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.draughts.domain.Colour;
import org.kevinkib.cardgames.draughts.domain.Draughts;
import org.kevinkib.cardgames.draughts.domain.Piece;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
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

import java.util.Map;

/** Under the {@code test} profile an e2e test replaces the position of a started draughts game over HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DraughtsTestPositionIT {

    private static final String SHORT_ENDING = ".".repeat(26) + "w" + ".".repeat(20) + "b" + ".".repeat(2);

    @LocalServerPort
    private int port;

    @Autowired
    private SessionService sessionService;

    private final RestTemplate http = new RestTemplate();

    private ResponseEntity<String> put(String gameId, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("http://localhost:" + port + "/api/test/draughts/" + gameId + "/position",
                HttpMethod.PUT, new HttpEntity<>(json, headers), String.class);
    }

    private Draughts start() {
        RoomCreated room = sessionService.createRoom("draughts", "P0",
                GameOptions.of(Map.of("hostColour", "white")));
        GameId id = new GameId(room.gameId());
        sessionService.joinRoom(id, "P1");
        return (Draughts) sessionService.startGame(id, room.hostToken());
    }

    @Test
    void aStartedGameTakesTheGivenPositionAndSideToMove() {
        Draughts game = start();
        String layout = ".".repeat(20) + "b" + ".".repeat(5) + "w" + ".".repeat(23);

        ResponseEntity<String> response = put(game.getId().uuid().toString(),
                "{\"layout\":\"" + layout + "\",\"toMove\":\"white\"}");

        assertThat(response.getStatusCode().value(), is(204));
        assertThat(game.position().layout(), is(layout));
        assertThat(game.position().toMove(), is(Colour.WHITE));
        assertThat(game.position().pieceAt(27), is(Piece.WHITE_MAN));
        assertThat(game.legalMoves(game.currentSeat()).get(0).isCapture(), is(true));
    }

    @Test
    void theVersionAdvancesSoThatClientsTakeTheNewState() {
        Draughts game = start();
        int before = game.version();

        put(game.getId().uuid().toString(), "{\"layout\":\"" + SHORT_ENDING + "\",\"toMove\":\"black\"}");

        assertThat(game.version() > before, is(true));
        assertThat(game.position().toMove(), is(Colour.BLACK));
    }

    @Test
    void anInvalidLayoutOrColourIsRefusedWithA400() {
        Draughts game = start();
        String id = game.getId().uuid().toString();

        assertThrows(HttpClientErrorException.BadRequest.class, () -> put(id, "{\"layout\":\"www\",\"toMove\":\"white\"}"));
        assertThrows(HttpClientErrorException.BadRequest.class,
                () -> put(id, "{\"layout\":\"" + SHORT_ENDING + "\",\"toMove\":\"green\"}"));
        assertThrows(HttpClientErrorException.BadRequest.class, () -> put(id, "{}"));
        assertThat(game.position().layout().equals(SHORT_ENDING), is(false));
    }

    @Test
    void anUnknownGameIsNotFound() {
        assertThrows(HttpClientErrorException.class, () -> put(GameId.generate().uuid().toString(),
                "{\"layout\":\"" + SHORT_ENDING + "\",\"toMove\":\"white\"}"));
    }
}
