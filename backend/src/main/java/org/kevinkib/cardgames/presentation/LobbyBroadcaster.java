package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.dto.LobbyDto;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionPlayer;

/** Sends a per-viewer {@link LobbyDto} to each claimed seat of a not-yet-started session. */
public class LobbyBroadcaster {

    private final GameMessagingService messaging;
    private final GameFactories gameFactories;

    public LobbyBroadcaster(GameMessagingService messaging, GameFactories gameFactories) {
        this.messaging = messaging;
        this.gameFactories = gameFactories;
    }

    public void broadcast(SessionGame lobby, String eventType, EventData eventData, String message) {
        int min = gameFactories.minPlayers(lobby.gameType());
        int max = gameFactories.maxPlayers(lobby.gameType());
        for (SessionPlayer seat : lobby.seats()) {
            if (!seat.isClaimed()) {
                continue;
            }
            LobbyDto view = LobbyDto.forViewer(lobby, min, max, seat.id());
            messaging.sendToSeat(lobby.id(), seat.id(), new SuccessResponse(eventType, eventData, message, view));
        }
    }
}
