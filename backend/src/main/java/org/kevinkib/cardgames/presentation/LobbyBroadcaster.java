package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.sessionmanagement.core.application.AddressedLobbyView;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;

import java.util.List;

/** Sends a per-viewer lobby view to each claimed human seat of a not-yet-started session, addressed by token. */
public class LobbyBroadcaster {

    private final GameMessagingService messaging;
    private final SessionService sessionService;

    public LobbyBroadcaster(GameMessagingService messaging, SessionService sessionService) {
        this.messaging = messaging;
        this.sessionService = sessionService;
    }

    /** Computes the views under the lobby lock, then sends them. */
    public void broadcast(GameId gameId, String eventType, EventData eventData, String message) {
        send(gameId, sessionService.lobbyViews(gameId), eventType, eventData, message);
    }

    /** Sends views that a use case already computed under the lobby lock. */
    public void send(GameId gameId, List<AddressedLobbyView> views, String eventType, EventData eventData,
                     String message) {
        for (AddressedLobbyView addressed : views) {
            messaging.sendToToken(gameId, addressed.token(),
                    new SuccessResponse(eventType, eventData, message, addressed.view()));
        }
    }
}
