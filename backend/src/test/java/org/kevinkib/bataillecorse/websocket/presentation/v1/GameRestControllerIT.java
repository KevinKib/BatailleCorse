package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.Response;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.BatailleCorseDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.PlayerDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.CreateEventData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@SpringBootTest
class GameRestControllerIT {

    @Autowired
    private BatailleCorseWebSocketController wsController;

    @Autowired
    private GameRestController gameRestController;

    @Test
    void givenUnknownId_whenGetGame_thenReturns404() {
        ResponseEntity<?> response = gameRestController.getGame("unknown-id");

        assertThat(response.getStatusCode(), is(HttpStatus.NOT_FOUND));
    }

    @Test
    void givenExistingGame_whenGetGame_thenReturnsGameStateWithTwoPlayers() {
        Response createResponse = wsController.createGame();
        CreateEventData createData = (CreateEventData) createResponse.getEventData();
        String gameId = createData.game().getId();

        ResponseEntity<BatailleCorseDto> response = gameRestController.getGame(gameId);

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
}
