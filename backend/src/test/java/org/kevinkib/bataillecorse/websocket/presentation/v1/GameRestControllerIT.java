package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.Response;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.CreateEventData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

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
    @SuppressWarnings("unchecked")
    void givenExistingGame_whenGetGame_thenReturnsGameStateWithTwoPlayers() {
        Response createResponse = wsController.createGame();
        CreateEventData createData = (CreateEventData) createResponse.getEventData();
        String gameId = createData.game().getId();

        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/game/" + gameId,
                Map.class);

        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        List<Map<String, Object>> players = (List<Map<String, Object>>) response.getBody().get("players");
        assertThat(players, hasSize(2));
        for (Map<String, Object> player : players) {
            assertThat(player.get("nbCards"), is(26));
        }
        Map<String, Object> pile = (Map<String, Object>) response.getBody().get("pile");
        assertThat((List<?>) pile.get("cards"), empty());
    }
}
