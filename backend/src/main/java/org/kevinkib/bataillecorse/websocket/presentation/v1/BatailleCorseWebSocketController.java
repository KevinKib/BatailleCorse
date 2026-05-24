package org.kevinkib.bataillecorse.websocket.presentation.v1;

import java.util.HashMap;
import java.util.Map;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.Player;
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.application.SessionService;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.ErrorResponse;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.GameActionPayload;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.Response;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.SuccessResponse;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.*;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.*;
import org.springframework.messaging.handler.annotation.MessageMapping;
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
    public Response createGame() {
        BatailleCorse batailleCorse = sessionService.createGame(NB_PLAYERS);

        Map<Integer, String> tokens = new HashMap<>();
        for (int i = 0; i < NB_PLAYERS; i++) {
            SessionToken token = sessionService.loadTokenByPlayerId(batailleCorse.getId(), new PlayerId(i));
            tokens.put(i, token.uuid().toString());
        }

        return new SuccessResponse(
                EventType.CREATE,
                new CreateEventData(new BatailleCorseIdDto(batailleCorse.getId()), tokens),
                GAME_CREATED_MESSAGE,
                new BatailleCorseDto(batailleCorse));
    }

    @MessageMapping("/send")
    public void send(GameActionPayload payload) {
        EventType eventType = EventType.SEND;

        BatailleCorse batailleCorse = sessionService.getGame(
                new BatailleCorseId(payload.gameId()));
        BatailleCorseDto batailleCorseDto = new BatailleCorseDto(batailleCorse);

        Response response;

        try {
            Player player = batailleCorse.getPlayerByIndex(payload.playerIndex());

            CardDto cardDto = new CardDto(player.getCardOnTop());
            batailleCorse.send(player);

            String message = "Player "+player.id()+" sent "+cardDto.getName()+".";
            SendEventData eventData = new SendEventData(new PlayerIdDto(player));
            response = new SuccessResponse(eventType, eventData, message, batailleCorseDto);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            response = new ErrorResponse(eventType, e.getMessage(), batailleCorseDto);
        }

        gameMessagingService.sendToGame(payload.gameId(), response);
    }

    @MessageMapping("/slap")
    public void slap(GameActionPayload payload) {
        EventType eventType = EventType.SLAP;

        BatailleCorse batailleCorse = sessionService.getGame(
                new BatailleCorseId(payload.gameId()));
        BatailleCorseDto batailleCorseDto = new BatailleCorseDto(batailleCorse);

        Response response;
        try {
            Player player = batailleCorse.getPlayerByIndex(payload.playerIndex());
            boolean successfulSlap = batailleCorse.slap(player);
            String message;

            if (successfulSlap) {
                message = "Player "+player.id()+" slapped and won.";
            } else {
                message = "Player "+player.id()+" slapped, lost, and received a penality.";
            }

            SlapEventData eventData = new SlapEventData(successfulSlap, new PlayerIdDto(player));

            response = new SuccessResponse(eventType, eventData, message, batailleCorseDto);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            response = new ErrorResponse(eventType, e.getMessage(), batailleCorseDto);
        }

        gameMessagingService.sendToGame(payload.gameId(), response);
    }

    @MessageMapping("/grab")
    public void grab(GameActionPayload payload) {
        EventType eventType = EventType.GRAB;

        BatailleCorse batailleCorse = sessionService.getGame(
                new BatailleCorseId(payload.gameId()));
        BatailleCorseDto batailleCorseDto = new BatailleCorseDto(batailleCorse);

        Response response;
        try {
            Player player = batailleCorse.getPlayerByIndex(payload.playerIndex());

            batailleCorse.grab(player);

            String message = "Player "+player.id()+" grabbed the pile. ";
            GrabEventData eventData = new GrabEventData(new PlayerIdDto(player));

            response = new SuccessResponse(eventType, eventData, message, batailleCorseDto);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            response = new ErrorResponse(eventType, e.getMessage(), batailleCorseDto);
        }

        gameMessagingService.sendToGame(payload.gameId(), response);
    }
}
