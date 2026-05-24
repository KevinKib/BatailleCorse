package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.Response;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.BatailleCorseDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.PlayerDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.CreateEventData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameRestControllerIT {

    @LocalServerPort
    private int port;

    @Autowired
    private BatailleCorseWebSocketController wsController;

    private RestTemplate restTemplate = new RestTemplate();

    @Test
    void givenUnknownId_whenGetGame_thenReturns404() {
        try {
            restTemplate.getForEntity(
                    "http://localhost:" + port + "/api/game/unknown-id",
                    String.class);
            throw new AssertionError("Expected 404 but request succeeded");
        } catch (HttpClientErrorException.NotFound e) {
            assertThat(e.getStatusCode(), is(HttpStatus.NOT_FOUND));
        }
    }

    @Test
    void givenExistingGame_whenGetGame_thenReturnsGameStateWithTwoPlayers() {
        Response createResponse = wsController.createGame();
        CreateEventData createData = (CreateEventData) createResponse.getEventData();
        String gameId = createData.game().getId();

        ResponseEntity<BatailleCorseDto> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/game/" + gameId,
                BatailleCorseDto.class);

        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        BatailleCorseDto body = response.getBody();
        assertThat(body, notNullValue());

        List<PlayerDto> players = body.getPlayers();
        assertThat(players, hasSize(2));
        for (PlayerDto player : players) {
            assertThat(player.getNbCards(), is(26));
        }
        assertThat(body.getPile().getCards(), empty());
    }

    @Test
    void givenCreate_whenCreateGame_thenResponseIncludesTokensForBothPlayers() {
        Response createResponse = wsController.createGame();
        CreateEventData createData = (CreateEventData) createResponse.getEventData();

        assertThat(createData.tokens(), hasKey(0));
        assertThat(createData.tokens(), hasKey(1));
        assertThat(createData.tokens().get(0), notNullValue());
        assertThat(createData.tokens().get(1), notNullValue());
        assertThat(createData.tokens().get(0), not(equalTo(createData.tokens().get(1))));
    }
}
