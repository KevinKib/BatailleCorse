package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.Player;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.*;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.*;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class BatailleCorseWebSocketController {

    public static final int NB_PLAYERS = 2;
    public static final String GAME_CREATED_MESSAGE = "Game created";
    private BatailleCorse batailleCorse;
    private BatailleCorseDto batailleCorseDto;

    @MessageMapping("/create")
    @SendTo("/topic/game")
    public Response createGame() {
        batailleCorse = new BatailleCorse(BatailleCorseId.generate(), NB_PLAYERS);
        batailleCorseDto = new BatailleCorseDto(batailleCorse);
        return new SuccessResponse(EventType.CREATE, new EmptyEventData(), GAME_CREATED_MESSAGE, batailleCorseDto);
    }

    @MessageMapping("/send/{playerIndex}")
    @SendTo("/topic/game")
    public Response send(@DestinationVariable("playerIndex") Integer playerIndex) {
        EventType eventType = EventType.SEND;

        try {
            Player player = batailleCorse.getPlayerByIndex(playerIndex);

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

    @MessageMapping("/slap/{playerIndex}")
    @SendTo("/topic/game")
    public Response slap(@DestinationVariable("playerIndex") Integer playerIndex) {
        EventType eventType = EventType.SLAP;

        try {
            Player player = batailleCorse.getPlayerByIndex(playerIndex);
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

    @MessageMapping("/grab/{playerIndex}")
    @SendTo("/topic/game")
    public Response grab(@DestinationVariable("playerIndex") Integer playerIndex) {
        EventType eventType = EventType.GRAB;

        try {
            Player player = batailleCorse.getPlayerByIndex(playerIndex);

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
