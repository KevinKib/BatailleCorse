package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.sessionmanagement.core.application.AddressedLobbyView;

import java.util.List;

/** Sends a per-viewer lobby view to each claimed human seat of a not-yet-started session, addressed by token. */
public class LobbyBroadcaster {

    private final GameMessagingService messaging;

    public LobbyBroadcaster(GameMessagingService messaging) {
        this.messaging = messaging;
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
