package org.kevinkib.bataillecorse.presentation.websocket.v1;

import org.kevinkib.bataillecorse.domain.BatailleCorse;
import org.kevinkib.bataillecorse.domain.Player;
import org.kevinkib.bataillecorse.presentation.websocket.v1.model.*;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class BatailleCorseWebSocketController {

    private BatailleCorse batailleCorse;
    private BatailleCorseDto batailleCorseDto;

    @MessageMapping("/create")
    @SendTo("/topic/game")
    public Response createGame() {
        batailleCorse = new BatailleCorse(2);
        batailleCorseDto = new BatailleCorseDto(batailleCorse);
        return new SuccessResponse(EventType.CREATE, "Game created", batailleCorseDto);
    }

    @MessageMapping("/send/{playerIndex}")
    @SendTo("/topic/game")
    public Response send(@DestinationVariable("playerIndex") Integer playerIndex) {
        EventType eventType = EventType.SEND;

        try {
            Player player = batailleCorse.getPlayerByIndex(playerIndex);

            CardDto cardDto = new CardDto(player.getCardOnTop());
            batailleCorse.send(player);

            String message = "Player "+player.getId()+" sent "+cardDto.getName()+".";
            return new SuccessResponse(eventType, message, batailleCorseDto);

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
                message = "Player "+player.getId()+" slapped and won.";
            } else {
                message = "Player "+player.getId()+" slapped, lost, and received a penality.";
            }

            return new SuccessResponse(eventType, message, batailleCorseDto);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            return new ErrorResponse(eventType, e.getMessage(), batailleCorseDto);
        }
    }

    @MessageMapping("/grab/{playerIndex}")
    @SendTo("/topic/game")
    public Response grab(@DestinationVariable("playerIndex") Integer playerIndex) {
        EventType eventType = EventType.SEND;

        try {
            Player player = batailleCorse.getPlayerByIndex(playerIndex);

            batailleCorse.grab(player);

            String message = "Player "+player.getId()+" grabbed the pile. ";
            return new SuccessResponse(eventType, message, batailleCorseDto);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            return new ErrorResponse(eventType, e.getMessage(), batailleCorseDto);
        }
    }
}
