package org.kevinkib.bataillecorse.websocket.presentation.v1;

import java.util.HashMap;
import java.util.Map;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.Player;
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.application.InvalidTokenException;
import org.kevinkib.bataillecorse.sessionmanagement.application.SessionService;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken;
import org.kevinkib.bataillecorse.sessionmanagement.domain.GameMode;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.CreateGamePayload;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.ErrorResponse;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.GameActionPayload;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.Response;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.SuccessResponse;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.*;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.*;
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

    public BatailleCorseWebSocketController(SessionService sessionService, GameMessagingService gameMessagingService) {
        this.sessionService = sessionService;
        this.gameMessagingService = gameMessagingService;
    }

    @MessageMapping("/create")
    @SendTo("/topic/game")
    public Response createGame(@Payload(required = false) CreateGamePayload payload) {
        GameMode mode = (payload != null && payload.mode() != null) ? payload.mode() : GameMode.SOLO;
        String name = (payload != null) ? payload.name() : null;

        BatailleCorse batailleCorse = sessionService.createGame(NB_PLAYERS, mode, name);

        int seatsToReturn = (mode == GameMode.SOLO) ? NB_PLAYERS : 1;
        Map<Integer, String> tokens = new HashMap<>();
        for (int i = 0; i < seatsToReturn; i++) {
            SessionToken token = sessionService.loadTokenByPlayerId(batailleCorse.getId(), new PlayerId(i));
            tokens.put(i, token.uuid().toString());
        }

        return new SuccessResponse(
                EventType.CREATE,
                new CreateEventData(new BatailleCorseIdDto(batailleCorse.getId()), tokens),
                GAME_CREATED_MESSAGE,
                BatailleCorseDto.from(batailleCorse));
    }

    @MessageMapping("/send")
    public void send(GameActionPayload payload) {
        EventType eventType = EventType.SEND;

        BatailleCorseId gameId = new BatailleCorseId(payload.gameId());
        BatailleCorse batailleCorse = sessionService.getGame(gameId);

        Response response;
        try {
            PlayerId playerId = sessionService
                    .findPlayerIdByToken(gameId, new SessionToken(payload.token()))
                    .orElseThrow(InvalidTokenException::new);
            Player player = batailleCorse.getPlayerByIndex(playerId.id());

            CardDto cardDto = CardDto.from(player.getCardOnTop());
            batailleCorse.send(player);

            BatailleCorseDto batailleCorseDto = BatailleCorseDto.from(batailleCorse);
            String message = "Player " + player.id() + " sent " + cardDto.getName() + ".";
            SendEventData eventData = new SendEventData(PlayerIdDto.from(player));
            response = new SuccessResponse(eventType, eventData, message, batailleCorseDto);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            BatailleCorseDto batailleCorseDto = BatailleCorseDto.from(batailleCorse);
            response = new ErrorResponse(eventType, e.getMessage(), batailleCorseDto);
        }

        gameMessagingService.sendToGame(payload.gameId(), response);
    }

    @MessageMapping("/slap")
    public void slap(GameActionPayload payload) {
        EventType eventType = EventType.SLAP;

        BatailleCorseId gameId = new BatailleCorseId(payload.gameId());
        BatailleCorse batailleCorse = sessionService.getGame(gameId);

        Response response;
        try {
            PlayerId playerId = sessionService
                    .findPlayerIdByToken(gameId, new SessionToken(payload.token()))
                    .orElseThrow(InvalidTokenException::new);
            Player player = batailleCorse.getPlayerByIndex(playerId.id());

            boolean successfulSlap = batailleCorse.slap(player);
            String message = successfulSlap
                    ? "Player " + player.id() + " slapped and won."
                    : "Player " + player.id() + " slapped, lost, and received a penality.";

            BatailleCorseDto batailleCorseDto = BatailleCorseDto.from(batailleCorse);
            SlapEventData eventData = new SlapEventData(successfulSlap, PlayerIdDto.from(player));
            response = new SuccessResponse(eventType, eventData, message, batailleCorseDto);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            BatailleCorseDto batailleCorseDto = BatailleCorseDto.from(batailleCorse);
            response = new ErrorResponse(eventType, e.getMessage(), batailleCorseDto);
        }

        gameMessagingService.sendToGame(payload.gameId(), response);
    }

    @MessageMapping("/grab")
    public void grab(GameActionPayload payload) {
        EventType eventType = EventType.GRAB;

        BatailleCorseId gameId = new BatailleCorseId(payload.gameId());
        BatailleCorse batailleCorse = sessionService.getGame(gameId);

        Response response;
        try {
            PlayerId playerId = sessionService
                    .findPlayerIdByToken(gameId, new SessionToken(payload.token()))
                    .orElseThrow(InvalidTokenException::new);
            Player player = batailleCorse.getPlayerByIndex(playerId.id());

            batailleCorse.grab(player);

            BatailleCorseDto batailleCorseDto = BatailleCorseDto.from(batailleCorse);
            String message = "Player " + player.id() + " grabbed the pile. ";
            GrabEventData eventData = new GrabEventData(PlayerIdDto.from(player));
            response = new SuccessResponse(eventType, eventData, message, batailleCorseDto);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            BatailleCorseDto batailleCorseDto = BatailleCorseDto.from(batailleCorse);
            response = new ErrorResponse(eventType, e.getMessage(), batailleCorseDto);
        }

        gameMessagingService.sendToGame(payload.gameId(), response);
    }
}
