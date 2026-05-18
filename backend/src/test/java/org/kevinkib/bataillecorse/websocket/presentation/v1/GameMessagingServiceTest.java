package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GameMessagingServiceTest {

    @Test
    void givenGameId_whenSendToGame_thenBroadcastsToPerGameChannel() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        GameMessagingService service = new GameMessagingService(template);

        service.sendToGame("abc-123", "payload");

        verify(template).convertAndSend("/topic/game/abc-123", (Object) "payload");
    }
}
