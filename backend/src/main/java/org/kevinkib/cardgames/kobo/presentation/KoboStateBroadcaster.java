package org.kevinkib.cardgames.kobo.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.presentation.dto.KoboDto;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.dto.event.EventData;

import java.util.Map;
import java.util.function.Function;

/** Sends each seat of a running game its own KoboDto on its private channel. */
public class KoboStateBroadcaster {

    private final GameMessagingService messaging;
    private final Function<GameId, Map<PlayerId, String>> seatNames;

    public KoboStateBroadcaster(GameMessagingService messaging) {
        this(messaging, id -> Map.of());
    }

    public KoboStateBroadcaster(GameMessagingService messaging, Function<GameId, Map<PlayerId, String>> seatNames) {
        this.messaging = messaging;
        this.seatNames = seatNames;
    }

    /** The viewer state with every display name: broadcasts, REST rehydration and error replies. */
    public KoboDto stateFor(Kobo game, PlayerId viewer) {
        return KoboDto.forViewer(game, viewer, seatNames.apply(game.getId()));
    }

    public void broadcast(Kobo game, String eventType, EventData eventData, String message) {
        for (PlayerId seat : game.getPlayerIds()) {
            messaging.sendToSeat(game.getId(), seat,
                    new SuccessResponse(eventType, eventData, message, stateFor(game, seat)));
        }
    }
}
