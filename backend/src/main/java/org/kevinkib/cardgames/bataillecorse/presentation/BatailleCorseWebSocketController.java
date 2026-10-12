package org.kevinkib.cardgames.bataillecorse.presentation;
import org.kevinkib.cardgames.presentation.*;

import java.util.HashMap;
import java.util.Map;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.bataillecorse.domain.Player;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.InvalidTokenException;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameMode;
import org.kevinkib.cardgames.presentation.api.CreateGamePayload;
import org.kevinkib.cardgames.presentation.api.ErrorResponse;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.dto.*;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.*;
import org.kevinkib.cardgames.presentation.dto.event.*;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.event.*;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class BatailleCorseWebSocketController {

    public static final int NB_PLAYERS = 2;
    public static final String GAME_CREATED_MESSAGE = "Game created";

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

    @MessageMapping("/create")
    @SendTo("/topic/game")
    public Response createGame(@Payload(required = false) CreateGamePayload payload) {
        GameMode mode = (payload != null && payload.mode() != null) ? payload.mode() : GameMode.SOLO;
        String name = (payload != null) ? payload.name() : null;

        BatailleCorse batailleCorse = (BatailleCorse) sessionService.createGame(BatailleCorseFactory.GAME_TYPE, NB_PLAYERS, mode, name);

        int seatsToReturn = (mode == GameMode.SOLO) ? NB_PLAYERS : 1;
        Map<Integer, String> tokens = new HashMap<>();
        for (int i = 0; i < seatsToReturn; i++) {
            tokens.put(i, sessionService.tokenForSeat(batailleCorse.getId(), new PlayerId(i)));
        }

        return new SuccessResponse(
                LifecycleEventType.CREATE.toString(),
                new CreateEventData(new BatailleCorseIdDto(batailleCorse.getId()), tokens),
                GAME_CREATED_MESSAGE,
                BatailleCorseDto.from(batailleCorse));
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

    @MessageMapping("/rematch")
    public void rematch(GameActionPayload payload) {
        GameId gameId = new GameId(payload.gameId());
        try {
            PlayerId playerId = sessionService
                    .findPlayerIdByToken(gameId, payload.token())
                    .orElseThrow(InvalidTokenException::new);

            boolean unanimous = sessionService.requestRematch(gameId, playerId);

            if (unanimous) {
                BatailleCorse fresh = (BatailleCorse) sessionService.rematch(gameId);
                broadcaster.broadcast(fresh, LifecycleEventType.REMATCH.toString(),
                        new RematchEventData(RematchStatus.STARTED, new PlayerIdDto(String.valueOf(playerId.id()))),
                        "Rematch started.");
            } else {
                BatailleCorse current = sessionService.getGame(gameId, BatailleCorse.class);
                broadcaster.broadcast(current, LifecycleEventType.REMATCH.toString(),
                        new RematchEventData(RematchStatus.PENDING, new PlayerIdDto(String.valueOf(playerId.id()))),
                        "Rematch requested.");
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
}
