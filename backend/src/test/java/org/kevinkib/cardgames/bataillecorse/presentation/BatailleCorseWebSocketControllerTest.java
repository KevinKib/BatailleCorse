package org.kevinkib.cardgames.bataillecorse.presentation;
import org.kevinkib.cardgames.presentation.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.bataillecorse.domain.Player;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionToken;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.Response;
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
        sessionService = new SessionService(new InMemorySessionRepository(java.time.Clock.systemUTC()), new org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories(java.util.List.of(new org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory())));
        template = mock(SimpMessagingTemplate.class);
        GameMessagingService messaging = new GameMessagingService(template, null);
        controller = new BatailleCorseWebSocketController(sessionService, messaging);
    }

    @Nested
    class SendTest {

        @Test
        void givenValidToken_whenSend_thenBroadcastsSuccessResponse() {
            var game = sessionService.createGame("bataille-corse", 2);
            String gameId = game.getId().uuid().toString();
            String token = sessionService.tokenForSeat(game.getId(), new PlayerId(0));

            controller.send(new GameActionPayload(gameId, token));

            verify(template).convertAndSend(
                    eq("/topic/game/" + gameId),
                    (Object) argThat(r -> ((Response) r).isSuccess())
            );
        }

        @Test
        void givenInvalidToken_whenSend_thenBroadcastsErrorResponse() {
            var game = sessionService.createGame("bataille-corse", 2);
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
            var game = sessionService.createGame("bataille-corse", 2);
            String gameId = game.getId().uuid().toString();
            String token0 = sessionService.tokenForSeat(game.getId(), new PlayerId(0));
            String token1 = sessionService.tokenForSeat(game.getId(), new PlayerId(1));

            // Send a card first so the pile is non-empty, enabling a slap
            controller.send(new GameActionPayload(gameId, token0));
            clearInvocations(template);

            controller.slap(new GameActionPayload(gameId, token1));

            verify(template).convertAndSend(
                    eq("/topic/game/" + gameId),
                    (Object) argThat(r -> ((Response) r).isSuccess())
            );
        }

        @Test
        void givenInvalidToken_whenSlap_thenBroadcastsErrorResponse() {
            var game = sessionService.createGame("bataille-corse", 2);
            String gameId = game.getId().uuid().toString();

            controller.slap(new GameActionPayload(gameId, SessionToken.generate().uuid().toString()));

            verify(template).convertAndSend(
                    eq("/topic/game/" + gameId),
                    (Object) argThat(r -> !((Response) r).isSuccess())
            );
        }
    }

    @Nested
    class GrabTest {

        @Test
        void givenValidToken_whenGrab_thenBroadcastsSuccessResponse() {
            var game = sessionService.createGame("bataille-corse", 2);
            String gameId = game.getId().uuid().toString();
            GameId batailleCorseId = game.getId();
            String token0 = sessionService.tokenForSeat(batailleCorseId, new PlayerId(0));
            String token1 = sessionService.tokenForSeat(batailleCorseId, new PlayerId(1));

            // Alternate sends until the pile becomes grabbable
            BatailleCorse batailleCorse = (BatailleCorse) sessionService.getGame(batailleCorseId);
            int currentPlayer = 0;
            while (!batailleCorse.isPileGrabbable()) {
                String currentToken = currentPlayer == 0 ? token0 : token1;
                controller.send(new GameActionPayload(gameId, currentToken));
                currentPlayer = batailleCorse.getCurrentPlayerIndex();
            }
            clearInvocations(template);

            // The player who added the last honour card can grab
            Player grabber = batailleCorse.getPile().getPlayerThatAddedLastHonourCard();
            PlayerId grabberId = grabber.id();
            String grabToken = sessionService.tokenForSeat(batailleCorseId, grabberId);

            controller.grab(new GameActionPayload(gameId, grabToken));

            verify(template).convertAndSend(
                    eq("/topic/game/" + gameId),
                    (Object) argThat(r -> ((Response) r).isSuccess())
            );
        }

        @Test
        void givenInvalidToken_whenGrab_thenBroadcastsErrorResponse() {
            var game = sessionService.createGame("bataille-corse", 2);
            String gameId = game.getId().uuid().toString();

            controller.grab(new GameActionPayload(gameId, SessionToken.generate().uuid().toString()));

            verify(template).convertAndSend(
                    eq("/topic/game/" + gameId),
                    (Object) argThat(r -> !((Response) r).isSuccess())
            );
        }
    }

    @Nested
    class RematchTest {

        @Test
        void givenSoloBothSeatsRequest_whenSecondRematch_thenBroadcastsStarted() {
            var game = sessionService.createGame("bataille-corse", 2, org.kevinkib.cardgames.sessionmanagement.core.application.GameMode.SOLO);
            String gameId = game.getId().uuid().toString();
            String token0 = sessionService.tokenForSeat(game.getId(), new PlayerId(0));
            String token1 = sessionService.tokenForSeat(game.getId(), new PlayerId(1));

            controller.rematch(new GameActionPayload(gameId, token0)); // PENDING
            clearInvocations(template);

            controller.rematch(new GameActionPayload(gameId, token1)); // STARTED

            verify(template).convertAndSend(
                    eq("/topic/game/" + gameId),
                    (Object) argThat(r -> ((Response) r).isSuccess()
                            && ((org.kevinkib.cardgames.bataillecorse.presentation.dto.event.RematchEventData)
                                    ((Response) r).getEventData()).status()
                               == org.kevinkib.cardgames.presentation.dto.event.RematchStatus.STARTED)
            );
        }

        @Test
        void givenMultiplayerSingleRequest_whenRematch_thenBroadcastsPending() {
            var game = sessionService.createGame("bataille-corse", 2, org.kevinkib.cardgames.sessionmanagement.core.application.GameMode.MULTIPLAYER);
            sessionService.joinGame(game.getId()); // claim seat 1 so the game has two humans
            String gameId = game.getId().uuid().toString();
            String token0 = sessionService.tokenForSeat(game.getId(), new PlayerId(0));

            controller.rematch(new GameActionPayload(gameId, token0));

            verify(template).convertAndSend(
                    eq("/topic/game/" + gameId),
                    (Object) argThat(r -> ((Response) r).isSuccess()
                            && ((org.kevinkib.cardgames.bataillecorse.presentation.dto.event.RematchEventData)
                                    ((Response) r).getEventData()).status()
                               == org.kevinkib.cardgames.presentation.dto.event.RematchStatus.PENDING)
            );
        }

        @Test
        void givenInvalidToken_whenRematch_thenDoesNotBroadcast() {
            var game = sessionService.createGame("bataille-corse", 2);
            String gameId = game.getId().uuid().toString();

            controller.rematch(new GameActionPayload(gameId, SessionToken.generate().uuid().toString()));

            verify(template, org.mockito.Mockito.never()).convertAndSend(
                    eq("/topic/game/" + gameId), (Object) org.mockito.ArgumentMatchers.any());
        }
    }
}
