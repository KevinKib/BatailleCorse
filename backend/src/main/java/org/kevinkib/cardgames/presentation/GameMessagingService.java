package org.kevinkib.cardgames.presentation;

import org.springframework.messaging.simp.SimpMessagingTemplate;

public class GameMessagingService {

    private final SimpMessagingTemplate messagingTemplate;

    public GameMessagingService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendToGame(String gameId, Object payload) {
        messagingTemplate.convertAndSend(destination(gameId), payload);
    }

    private String destination(String gameId) {
        return "/topic/game/" + gameId;
    }

}
