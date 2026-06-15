package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.springframework.messaging.simp.SimpMessagingTemplate;

public class GameMessagingService {

    private final SimpMessagingTemplate messagingTemplate;

    public GameMessagingService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendToGame(String gameId, Object payload) {
        messagingTemplate.convertAndSend(destination(gameId), payload);
    }

    public void sendToSeat(GameId gameId, PlayerId seat, Object payload) {
        messagingTemplate.convertAndSend(seatDestination(gameId, seat), payload);
    }

    private String destination(String gameId) {
        return "/topic/game/" + gameId;
    }

    private String seatDestination(GameId gameId, PlayerId seat) {
        return "/topic/game/" + gameId.uuid() + "/seat/" + seat.id();
    }

}
