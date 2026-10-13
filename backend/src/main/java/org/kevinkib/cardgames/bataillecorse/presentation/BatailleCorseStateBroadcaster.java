package org.kevinkib.cardgames.bataillecorse.presentation;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.presentation.state.GameStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sends the game state to everyone on the game topic (both seats see the same state), then tells the
 * registered listeners (the bot coordinator) that the state moved.
 */
public class BatailleCorseStateBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(BatailleCorseStateBroadcaster.class);

    private final GameMessagingService messaging;
    private final List<GameStateListener<BatailleCorse>> listeners = new CopyOnWriteArrayList<>();

    public BatailleCorseStateBroadcaster(GameMessagingService messaging) {
        this.messaging = messaging;
    }

    public void addListener(GameStateListener<BatailleCorse> listener) {
        listeners.add(listener);
    }

    public void broadcast(BatailleCorse game, String eventType, EventData eventData, String message) {
        broadcast(game, eventType, eventData, message, BatailleCorseDto.from(game));
    }

    /** For events whose state carries more than the game, such as the forfeit reasons. */
    public void broadcast(BatailleCorse game, String eventType, EventData eventData, String message,
                          BatailleCorseDto state) {
        messaging.sendToGame(game.getId().uuid().toString(), new SuccessResponse(eventType, eventData, message, state));
        for (GameStateListener<BatailleCorse> listener : listeners) {
            try {
                listener.onBroadcast(game, eventType, eventData);
            } catch (RuntimeException e) {
                log.error("State listener failed after {}", eventType, e);
            }
        }
    }
}
