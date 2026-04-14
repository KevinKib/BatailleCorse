package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.Player;
import org.kevinkib.bataillecorse.sessionmanagement.application.SessionService;
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

    public BatailleCorseWebSocketController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @MessageMapping("/create")
    @SendTo("/topic/game")
    public Response createGame() {
        BatailleCorse batailleCorse = sessionService.createGame(NB_PLAYERS);
        return new SuccessResponse(
                EventType.CREATE,
                new EmptyEventData(),
                GAME_CREATED_MESSAGE,
                new BatailleCorseDto(batailleCorse));
    }

    @MessageMapping("/send")
    @SendTo("/topic/game")
    public Response send(GameActionPayload payload) {
        EventType eventType = EventType.SEND;

        BatailleCorse batailleCorse = sessionService.getGame(
                new BatailleCorseId(payload.gameId()));
        BatailleCorseDto batailleCorseDto = new BatailleCorseDto(batailleCorse);

        try {
            Player player = batailleCorse.getPlayerByIndex(payload.playerIndex());

            CardDto cardDto = new CardDto(player.getCardOnTop());
            batailleCorse.send(player);

            String message = "Player "+player.id()+" sent "+cardDto.getName()+".";
            SendEventData eventData = new SendEventData(new PlayerIdDto(player));
            return new SuccessResponse(eventType, eventData, message, batailleCorseDto);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            return new ErrorResponse(eventType, e.getMessage(), batailleCorseDto);
        }
    }

    @MessageMapping("/slap")
    @SendTo("/topic/game")
    public Response slap(GameActionPayload payload) {
        EventType eventType = EventType.SLAP;

        BatailleCorse batailleCorse = sessionService.getGame(
                new BatailleCorseId(payload.gameId()));
        BatailleCorseDto batailleCorseDto = new BatailleCorseDto(batailleCorse);

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

            return new SuccessResponse(eventType, eventData, message, batailleCorseDto);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            return new ErrorResponse(eventType, e.getMessage(), batailleCorseDto);
        }
    }

    @MessageMapping("/grab")
    @SendTo("/topic/game")
    public Response grab(GameActionPayload payload) {
        EventType eventType = EventType.GRAB;

        BatailleCorse batailleCorse = sessionService.getGame(
                new BatailleCorseId(payload.gameId()));
        BatailleCorseDto batailleCorseDto = new BatailleCorseDto(batailleCorse);

        try {
            Player player = batailleCorse.getPlayerByIndex(payload.playerIndex());

            batailleCorse.grab(player);

            String message = "Player "+player.id()+" grabbed the pile. ";
            GrabEventData eventData = new GrabEventData(new PlayerIdDto(player));

            return new SuccessResponse(eventType, eventData, message, batailleCorseDto);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            return new ErrorResponse(eventType, e.getMessage(), batailleCorseDto);
        }
    }
}
