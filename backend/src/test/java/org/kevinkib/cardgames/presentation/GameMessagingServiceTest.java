package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameMessagingServiceTest {

    @Test
    void givenGameId_whenSendToGame_thenBroadcastsToPerGameChannel() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        GameMessagingService service = new GameMessagingService(template, mock(SessionService.class));

        service.sendToGame("abc-123", "payload");

        verify(template).convertAndSend("/topic/game/abc-123", (Object) "payload");
    }

    @Test
    void givenSeat_whenSendToSeat_thenAddressesTopicByTheSeatsToken() {
        AtomicReference<String> destination = new AtomicReference<>();
        SimpMessagingTemplate template = new SimpMessagingTemplate((message, timeout) -> true) {
            @Override
            public void convertAndSend(String dest, Object payload) {
                destination.set(dest);
            }
        };
        GameId gameId = new GameId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        SessionService sessionService = mock(SessionService.class);
        when(sessionService.tokenForSeat(gameId, new PlayerId(2))).thenReturn("seat-2-token");
        GameMessagingService service = new GameMessagingService(template, sessionService);

        service.sendToSeat(gameId, new PlayerId(2), "payload");

        assertThat(destination.get(),
                is("/topic/game/11111111-1111-1111-1111-111111111111/seat/seat-2-token"));
    }
}
