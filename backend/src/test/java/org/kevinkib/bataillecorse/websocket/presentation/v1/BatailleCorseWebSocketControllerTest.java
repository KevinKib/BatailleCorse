package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.application.SessionService;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken;
import org.kevinkib.bataillecorse.sessionmanagement.infrastructure.InMemorySessionRepository;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.GameActionPayload;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.Response;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BatailleCorseWebSocketControllerTest {

    private SessionService sessionService;
    private SimpMessagingTemplate template;
    private BatailleCorseWebSocketController controller;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(new InMemorySessionRepository());
        template = mock(SimpMessagingTemplate.class);
        controller = new BatailleCorseWebSocketController(sessionService, new GameMessagingService(template));
    }

    @Nested
    class SendTest {

        @Test
        void givenValidToken_whenSend_thenBroadcastsSuccessResponse() {
            var game = sessionService.createGame(2);
            String gameId = game.getId().uuid().toString();
            SessionToken token = sessionService.loadTokenByPlayerId(game.getId(), new PlayerId(0));

            controller.send(new GameActionPayload(gameId, token.uuid().toString()));

            verify(template).convertAndSend(
                    eq("/topic/game/" + gameId),
                    (Object) argThat(r -> ((Response) r).isSuccess())
            );
        }

        @Test
        void givenInvalidToken_whenSend_thenBroadcastsErrorResponse() {
            var game = sessionService.createGame(2);
            String gameId = game.getId().uuid().toString();

            controller.send(new GameActionPayload(gameId, SessionToken.generate().uuid().toString()));

            verify(template).convertAndSend(
                    eq("/topic/game/" + gameId),
                    (Object) argThat(r -> !((Response) r).isSuccess())
            );
        }
    }

    @Nested
    class SlapTest {

        @Test
        void givenValidToken_whenSlap_thenBroadcastsSuccessResponse() {
            var game = sessionService.createGame(2);
            String gameId = game.getId().uuid().toString();
            SessionToken token0 = sessionService.loadTokenByPlayerId(game.getId(), new PlayerId(0));
            SessionToken token1 = sessionService.loadTokenByPlayerId(game.getId(), new PlayerId(1));

            // Send a card first so the pile is non-empty, enabling a slap
            controller.send(new GameActionPayload(gameId, token0.uuid().toString()));
            clearInvocations(template);

            controller.slap(new GameActionPayload(gameId, token1.uuid().toString()));

            verify(template).convertAndSend(
                    eq("/topic/game/" + gameId),
                    (Object) argThat(r -> ((Response) r).isSuccess())
            );
        }

        @Test
        void givenInvalidToken_whenSlap_thenBroadcastsErrorResponse() {
            var game = sessionService.createGame(2);
            String gameId = game.getId().uuid().toString();

            controller.slap(new GameActionPayload(gameId, SessionToken.generate().uuid().toString()));

            verify(template).convertAndSend(
                    eq("/topic/game/" + gameId),
                    (Object) argThat(r -> !((Response) r).isSuccess())
            );
        }
    }
}
