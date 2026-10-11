package org.kevinkib.cardgames.threemenmorris.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.ThreeMenMorrisDto;

import java.util.Map;
import java.util.function.Function;

/** Sends each seat of a running game its own ThreeMenMorrisDto on its private channel. */
public class ThreeMenMorrisStateBroadcaster {

    private final GameMessagingService messaging;
    private final Function<GameId, Map<PlayerId, String>> seatNames;

    public ThreeMenMorrisStateBroadcaster(GameMessagingService messaging) {
        this(messaging, id -> Map.of());
    }

    public ThreeMenMorrisStateBroadcaster(GameMessagingService messaging,
                                          Function<GameId, Map<PlayerId, String>> seatNames) {
        this.messaging = messaging;
        this.seatNames = seatNames;
    }

    /** The viewer state with every display name: broadcasts, REST rehydration and error replies. */
    public ThreeMenMorrisDto stateFor(ThreeMenMorris game, PlayerId viewer) {
        return ThreeMenMorrisDto.forViewer(game, viewer, seatNames.apply(game.getId()));
    }

    public void broadcast(ThreeMenMorris game, String eventType, EventData eventData, String message) {
        for (PlayerId seat : game.getPlayerIds()) {
            messaging.sendToSeat(game.getId(), seat,
                    new SuccessResponse(eventType, eventData, message, stateFor(game, seat)));
        }
    }
}
