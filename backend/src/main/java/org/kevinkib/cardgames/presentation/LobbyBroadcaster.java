package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.sessionmanagement.core.application.LobbyView;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;

/** Sends a per-viewer {@link LobbyView} to each claimed seat of a not-yet-started session. */
public class LobbyBroadcaster {

    private final GameMessagingService messaging;
    private final SessionService sessionService;

    public LobbyBroadcaster(GameMessagingService messaging, SessionService sessionService) {
        this.messaging = messaging;
        this.sessionService = sessionService;
    }

    public void broadcast(GameId gameId, String eventType, EventData eventData, String message) {
        for (LobbyView view : sessionService.lobbyViews(gameId)) {
            messaging.sendToSeat(gameId, new PlayerId(view.mySeat()),
                    new SuccessResponse(eventType, eventData, message, view));
        }
    }
}
