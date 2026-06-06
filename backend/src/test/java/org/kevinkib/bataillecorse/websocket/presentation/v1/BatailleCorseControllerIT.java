package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.Response;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.SuccessResponse;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.CreateEventData;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.EmptyEventData;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.EventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.kevinkib.bataillecorse.websocket.presentation.v1.BatailleCorseWebSocketController.GAME_CREATED_MESSAGE;
import static org.kevinkib.bataillecorse.websocket.presentation.v1.BatailleCorseWebSocketController.NB_PLAYERS;

@SpringBootTest
class BatailleCorseControllerIT {

    @Autowired
    private BatailleCorseWebSocketController controller;

    @Test
    void givenNoGame_whenCreateGame_thenReturnsSuccessResponseWithCreateEventAndNonNullState() {
        Response response = controller.createGame(null);

        assertThat(response, instanceOf(SuccessResponse.class));
        assertThat(response.getEventType(), is(EventType.CREATE.toString()));
        assertThat(response.getEventData(), instanceOf(CreateEventData.class));
        assertThat(response.getMessage(), is(GAME_CREATED_MESSAGE));
        assertThat(response.getState(), notNullValue());
        assertThat(response.getState().getPlayers(), hasSize(NB_PLAYERS));
    }
}
