package org.kevinkib.cardgames.bataillecorse.presentation;
import org.kevinkib.cardgames.presentation.*;


import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.InvalidTokenException;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.presentation.api.ErrorResponse;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.dto.*;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.*;
import org.kevinkib.cardgames.presentation.dto.event.*;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.event.*;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
public class BatailleCorseWebSocketController {

    private final SessionService sessionService;
    private final GameMessagingService gameMessagingService;
    private final BatailleCorseGameActions actions;
    private final BatailleCorseStateBroadcaster broadcaster;

    public BatailleCorseWebSocketController(SessionService sessionService,
                                            GameMessagingService gameMessagingService,
                                            BatailleCorseGameActions actions,
                                            BatailleCorseStateBroadcaster broadcaster) {
        this.sessionService = sessionService;
        this.gameMessagingService = gameMessagingService;
        this.actions = actions;
        this.broadcaster = broadcaster;
    }

    /** Room model: the host starts the game once enough seats are taken (humans or bots). */
    @MessageMapping("/bataillecorse/start")
    public void start(@Payload GameActionPayload payload) {
        GameId gameId;
        PlayerId actor;
        try {
            gameId = new GameId(payload.gameId());
            actor = sessionService.findPlayerIdByToken(gameId, payload.token()).orElse(null);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return;
        }
        if (actor == null) {
            return;
        }
        String type = BatailleCorseEventType.START.toString();
        try {
            if (!BatailleCorseFactory.GAME_TYPE.equals(sessionService.gameType(gameId))) {
                throw new IllegalArgumentException("This room is not a Bataille Corse game");
            }
            BatailleCorse game = sessionService.startGame(gameId, payload.token(), BatailleCorse.class);
            sessionService.touch(gameId);
            broadcaster.broadcast(game, type, new EmptyEventData(), "Game started.");
        } catch (Exception e) {
            System.err.println(e.getMessage());
            gameMessagingService.sendToSeat(gameId, actor, new ErrorResponse(type, e.getMessage(), null));
        }
    }

    @MessageMapping("/send")
    public void send(GameActionPayload payload) {
        act(payload, BatailleCorseEventType.SEND, actions::send);
    }

    @MessageMapping("/slap")
    public void slap(GameActionPayload payload) {
        act(payload, BatailleCorseEventType.SLAP, actions::slap);
    }

    @MessageMapping("/grab")
    public void grab(GameActionPayload payload) {
        act(payload, BatailleCorseEventType.GRAB, actions::grab);
    }

    @FunctionalInterface
    private interface Use {
        void run(GameId gameId, PlayerId playerId) throws Exception;
    }

    /**
     * Authenticates the token, runs the use case (which broadcasts on success) and, on any failure,
     * answers the whole game with an error response carrying the unchanged state.
     */
    private void act(GameActionPayload payload, BatailleCorseEventType eventType, Use use) {
        GameId gameId = new GameId(payload.gameId());
        BatailleCorse batailleCorse = sessionService.getGame(gameId, BatailleCorse.class);

        try {
            PlayerId playerId = sessionService
                    .findPlayerIdByToken(gameId, payload.token())
                    .orElseThrow(InvalidTokenException::new);
            use.run(gameId, playerId);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            gameMessagingService.sendToGame(payload.gameId(),
                    new ErrorResponse(eventType.toString(), e.getMessage(), BatailleCorseDto.from(batailleCorse)));
        }
    }
}
