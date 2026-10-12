package org.kevinkib.cardgames.presentation.state;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.SeatNames;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.bot.BotSeats;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sends each human seat of a game its own view of the state on its private channel, then tells the
 * registered listeners (the bot coordinator) that the state moved. Bot seats are skipped: nothing
 * subscribes to their topic. The game's own {@link SeatProjection} decides what a seat sees.
 */
public class SeatStateBroadcaster<G extends Game, D> {

    private static final Logger log = LoggerFactory.getLogger(SeatStateBroadcaster.class);

    private final GameMessagingService messaging;
    private final BotSeats botSeats;
    private final SeatNames seatNames;
    private final SeatProjection<G, D> projection;
    private final List<GameStateListener<G>> listeners = new CopyOnWriteArrayList<>();

    public SeatStateBroadcaster(GameMessagingService messaging, BotSeats botSeats, SeatNames seatNames,
                                SeatProjection<G, D> projection) {
        this.messaging = messaging;
        this.botSeats = botSeats;
        this.seatNames = seatNames;
        this.projection = projection;
    }

    /** The viewer's state with every seat's display name; used by broadcasts, REST rehydration and error replies. */
    public D stateFor(G game, PlayerId viewer) {
        return projection.forViewer(game, viewer, seatNames.seatNames(game.getId()), botSeats.botSeats(game.getId()));
    }

    public void addListener(GameStateListener<G> listener) {
        listeners.add(listener);
    }

    public void broadcast(G game, String eventType, EventData eventData, String message) {
        Set<PlayerId> bots = botSeats.botSeats(game.getId());
        for (PlayerId seat : game.getPlayerIds()) {
            if (bots.contains(seat)) {
                continue;
            }
            messaging.sendToSeat(game.getId(), seat,
                    new SuccessResponse(eventType, eventData, message, stateFor(game, seat)));
        }
        for (GameStateListener<G> listener : listeners) {
            try {
                listener.onBroadcast(game, eventType, eventData);
            } catch (RuntimeException e) {
                log.error("State listener failed after {}", eventType, e);
            }
        }
    }
}
