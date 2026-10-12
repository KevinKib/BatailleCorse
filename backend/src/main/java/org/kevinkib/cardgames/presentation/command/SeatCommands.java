package org.kevinkib.cardgames.presentation.command;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.api.ErrorResponse;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a WebSocket command on behalf of a seat. The seat is authenticated from the game id and its token:
 * an unknown or malformed id, or an unknown token, is ignored (nothing is sent, the reason goes to the debug
 * log). A command the game refuses is answered to the sender alone with an {@link ErrorResponse} carrying the
 * message and, when a game runs, the sender's current state.
 *
 * <p>A plain helper that each game's controller calls; it knows no game and keeps no state.
 */
public class SeatCommands {

    private static final Logger log = LoggerFactory.getLogger(SeatCommands.class);

    /** What a seat asks for; may throw the game's own (checked) exceptions. */
    @FunctionalInterface
    public interface SeatAction {
        void run(GameId gameId, PlayerId seat) throws Exception;
    }

    /** The sender's current state for an error reply; may throw or return null when no game runs. */
    @FunctionalInterface
    public interface StateOnError {
        Object stateFor(GameId gameId, PlayerId seat);
    }

    private final SessionService sessionService;
    private final GameMessagingService messaging;

    public SeatCommands(SessionService sessionService, GameMessagingService messaging) {
        this.sessionService = sessionService;
        this.messaging = messaging;
    }

    public void handle(String rawGameId, String token, String eventType, SeatAction action, StateOnError state) {
        GameId gameId;
        PlayerId seat;
        try {
            gameId = new GameId(rawGameId);
            seat = sessionService.findPlayerIdByToken(gameId, token).orElse(null);
        } catch (Exception e) {
            log.debug("{} ignored, cannot authenticate: {}", eventType, e.getMessage());
            return;
        }
        if (seat == null) {
            log.debug("{} ignored, unknown token for game {}", eventType, gameId);
            return;
        }
        try {
            action.run(gameId, seat);
        } catch (Exception e) {
            if (e instanceof RuntimeException && !(e instanceof IllegalArgumentException)
                    && !(e instanceof IllegalStateException)) {
                log.warn("{} failed unexpectedly in game {}", eventType, gameId, e);
            } else {
                log.debug("{} refused in game {}: {}", eventType, gameId, e.getMessage());
            }
            messaging.sendToSeat(gameId, seat, new ErrorResponse(eventType, e.getMessage(), stateOrNull(state, gameId, seat)));
        }
    }

    private Object stateOrNull(StateOnError state, GameId gameId, PlayerId seat) {
        try {
            return state.stateFor(gameId, seat);
        } catch (Exception e) {
            log.debug("no state for the error reply in game {}: {}", gameId, e.getMessage());
            return null;
        }
    }
}
