package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.presentation.dto.event.CreateEventData;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.kevinkib.cardgames.presentation.BatailleCorseWebSocketController.GAME_CREATED_MESSAGE;
import static org.kevinkib.cardgames.presentation.BatailleCorseWebSocketController.NB_PLAYERS;

@SpringBootTest
class BatailleCorseControllerIT {

    @Autowired
    private BatailleCorseWebSocketController controller;

    @Test
    void givenNoGame_whenCreateGame_thenReturnsSuccessResponseWithCreateEventAndNonNullState() {
        Response response = controller.createGame(null);

        assertThat(response, instanceOf(SuccessResponse.class));
        assertThat(response.getEventType(), is(LifecycleEventType.CREATE.toString()));
        assertThat(response.getEventData(), instanceOf(CreateEventData.class));
        assertThat(response.getMessage(), is(GAME_CREATED_MESSAGE));
        assertThat(response.getState(), notNullValue());
        assertThat(((BatailleCorseDto) response.getState()).getPlayers(), hasSize(NB_PLAYERS));
    }
}
