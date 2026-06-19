package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.messaging.simp.SimpMessagingTemplate;

public class GameMessagingService {

    private final SimpMessagingTemplate messagingTemplate;
    private final SessionService sessionService;

    public GameMessagingService(SimpMessagingTemplate messagingTemplate, SessionService sessionService) {
        this.messagingTemplate = messagingTemplate;
        this.sessionService = sessionService;
    }

    public void sendToGame(String gameId, Object payload) {
        messagingTemplate.convertAndSend(destination(gameId), payload);
    }

    /**
     * Sends to a seat's private channel, addressed by the seat's token rather than its index.
     * Seat indices are reused when a room reopens for a rematch (same GameId, fresh lobby), so an
     * index-addressed topic would deliver a new occupant's messages to the previous occupant's still-live
     * subscription. Tokens are unique per claim and minted fresh on reopen, so a recycled seat is a
     * distinct topic and stale subscriptions receive nothing.
     */
    public void sendToSeat(GameId gameId, PlayerId seat, Object payload) {
        String token = sessionService.tokenForSeat(gameId, seat);
        messagingTemplate.convertAndSend(seatDestination(gameId, token), payload);
    }

    private String destination(String gameId) {
        return "/topic/game/" + gameId;
    }

    private String seatDestination(GameId gameId, String token) {
        return "/topic/game/" + gameId.uuid() + "/seat/" + token;
    }

}
