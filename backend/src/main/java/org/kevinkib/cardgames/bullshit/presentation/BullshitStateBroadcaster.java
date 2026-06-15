package org.kevinkib.cardgames.bullshit.presentation;

import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.presentation.dto.BullshitDto;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.dto.event.EventData;

public class BullshitStateBroadcaster {

    private final GameMessagingService messaging;

    public BullshitStateBroadcaster(GameMessagingService messaging) {
        this.messaging = messaging;
    }

    public void broadcast(Bullshit game, String eventType, EventData eventData, String message) {
        for (PlayerId seat : game.getPlayerIds()) {
            BullshitDto state = BullshitDto.forViewer(game, seat);
            messaging.sendToSeat(game.getId(), seat, new SuccessResponse(eventType, eventData, message, state));
        }
    }
}
