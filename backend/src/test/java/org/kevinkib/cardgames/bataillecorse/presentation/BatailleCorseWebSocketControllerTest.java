package org.kevinkib.cardgames.bataillecorse.presentation;
import org.kevinkib.cardgames.presentation.command.SeatCommands;
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
import static org.mockito.Mockito.verifyNoInteractions;

class BatailleCorseWebSocketControllerTest {

    private SessionService sessionService;
    private SimpMessagingTemplate template;
    private BatailleCorseWebSocketController controller;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(new InMemorySessionRepository(java.time.Clock.systemUTC()), new org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories(java.util.List.of(new org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory(), new org.kevinkib.cardgames.bullshit.domain.BullshitFactory())));
        template = mock(SimpMessagingTemplate.class);
        GameMessagingService messaging = new GameMessagingService(template, sessionService);
        BatailleCorseStateBroadcaster broadcaster = new BatailleCorseStateBroadcaster(messaging);
        controller = new BatailleCorseWebSocketController(sessionService, new SeatCommands(sessionService, messaging),
                new BatailleCorseGameActions(sessionService, broadcaster), broadcaster);
    }

    /** A Bataille Corse room with two humans seated, started by its host. */
    private BatailleCorse twoHumanGame() {
        var room = sessionService.createRoom("bataille-corse", "Alice");
        GameId id = new GameId(room.gameId());
        sessionService.joinRoom(id, "Bob");
        return sessionService.startGame(id, room.hostToken(), BatailleCorse.class);
    }

    @Test
    void givenARoomOfAnotherGame_whenStart_thenRefusedToTheSenderAndNotStarted() {
        var room = sessionService.createRoom("bullshit", "Alice");
        GameId id = new GameId(room.gameId());
        sessionService.joinRoom(id, "Bob");

        controller.start(new GameActionPayload(room.gameId(), room.hostToken()));

        verify(template).convertAndSend(
                org.mockito.ArgumentMatchers.anyString(),
                (Object) argThat(r -> !((Response) r).isSuccess()
                        && "This room is not a Bataille Corse game".equals(((Response) r).getMessage())));
        org.junit.jupiter.api.Assertions.assertTrue(sessionService.findGame(id).isEmpty());
    }

    @Nested
    class SendTest {

        @Test
        void givenValidToken_whenSend_thenBroadcastsSuccessResponse() {
            var game = twoHumanGame();
            String gameId = game.getId().uuid().toString();
            String token = sessionService.tokenForSeat(game.getId(), new PlayerId(0));

            controller.send(new GameActionPayload(gameId, token));

            verify(template).convertAndSend(
                    eq("/topic/game/" + gameId),
                    (Object) argThat(r -> ((Response) r).isSuccess())
            );
        }

        @Test
        void givenInvalidToken_whenSend_thenNothingIsSent() {
            var game = twoHumanGame();
            String gameId = game.getId().uuid().toString();
            clearInvocations(template);

            controller.send(new GameActionPayload(gameId, SessionToken.generate().uuid().toString()));

            verifyNoInteractions(template);
        }
    }

    @Nested
    class SlapTest {

        @Test
        void givenValidToken_whenSlap_thenBroadcastsSuccessResponse() {
            var game = twoHumanGame();
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
        void givenInvalidToken_whenSlap_thenNothingIsSent() {
            var game = twoHumanGame();
            String gameId = game.getId().uuid().toString();
            clearInvocations(template);

            controller.slap(new GameActionPayload(gameId, SessionToken.generate().uuid().toString()));

            verifyNoInteractions(template);
        }
    }

    @Nested
    class GrabTest {

        @Test
        void givenValidToken_whenGrab_thenBroadcastsSuccessResponse() {
            var game = twoHumanGame();
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
        void givenInvalidToken_whenGrab_thenNothingIsSent() {
            var game = twoHumanGame();
            String gameId = game.getId().uuid().toString();
            clearInvocations(template);

            controller.grab(new GameActionPayload(gameId, SessionToken.generate().uuid().toString()));

            verifyNoInteractions(template);
        }
    }
}
