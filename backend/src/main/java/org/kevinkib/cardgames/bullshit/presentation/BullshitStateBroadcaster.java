package org.kevinkib.cardgames.bullshit.presentation;

import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.presentation.bot.BotSeats;
import org.kevinkib.cardgames.bullshit.presentation.bot.SeatNames;
import org.kevinkib.cardgames.bullshit.presentation.dto.BullshitDto;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sends each human seat its own view of the game, then tells the registered listeners (the bot
 * coordinator) that the state moved. Bot seats are skipped: nothing subscribes to their topic.
 */
public class BullshitStateBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(BullshitStateBroadcaster.class);

    private final GameMessagingService messaging;
    private final BotSeats botSeats;
    private final SeatNames seatNames;
    private final List<BullshitStateListener> listeners = new CopyOnWriteArrayList<>();

    public BullshitStateBroadcaster(GameMessagingService messaging) {
        this(messaging, BotSeats.none());
    }

    public BullshitStateBroadcaster(GameMessagingService messaging, BotSeats botSeats) {
        this(messaging, botSeats, SeatNames.none());
    }

    public BullshitStateBroadcaster(GameMessagingService messaging, BotSeats botSeats, SeatNames seatNames) {
        this.messaging = messaging;
        this.botSeats = botSeats;
        this.seatNames = seatNames;
    }

    /** The viewer's state with every seat's display name; used by broadcasts, REST rehydration and error replies. */
    public BullshitDto stateFor(Bullshit game, PlayerId viewer) {
        return BullshitDto.forViewer(game, viewer, seatNames.seatNames(game.getId()), botSeats.botSeats(game.getId()));
    }

    public void addListener(BullshitStateListener listener) {
        listeners.add(listener);
    }

    public void broadcast(Bullshit game, String eventType, EventData eventData, String message) {
        Set<PlayerId> bots = botSeats.botSeats(game.getId());
        for (PlayerId seat : game.getPlayerIds()) {
            if (bots.contains(seat)) {
                continue;
            }
            BullshitDto state = stateFor(game, seat);
            messaging.sendToSeat(game.getId(), seat, new SuccessResponse(eventType, eventData, message, state));
        }
        for (BullshitStateListener listener : listeners) {
            try {
                listener.onBroadcast(game, eventType, eventData);
            } catch (RuntimeException e) {
                log.error("State listener failed after {}", eventType, e);
            }
        }
    }
}
