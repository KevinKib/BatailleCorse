package org.kevinkib.cardgames.ninetynine.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNine;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNineException;
import org.kevinkib.cardgames.ninetynine.domain.Phase;
import org.kevinkib.cardgames.ninetynine.domain.event.NinetyNineEvent;
import org.kevinkib.cardgames.ninetynine.presentation.dto.NinetyNineDto;
import org.kevinkib.cardgames.ninetynine.presentation.dto.event.NinetyNineEventData;
import org.kevinkib.cardgames.ninetynine.presentation.dto.event.NinetyNineEventType;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cards.domain.Card;

import java.util.List;

/**
 * The 99 use cases, shared by humans, the bots and the round pause: domain call, session touch, then one broadcast
 * per domain event of the command (a play that busts and eliminates announces PLAY, BUST, ELIMINATED, GAME_OVER, each
 * with the state after the whole command). Authentication stays with the caller; domain exceptions propagate to it.
 * The call and the broadcasts run under the monitor of the game, so two commands' events leave in the order the state
 * changed.
 */
public class NinetyNineGameActions {

    private final SessionService sessionService;
    private final SeatStateBroadcaster<NinetyNine, NinetyNineDto> broadcaster;

    public NinetyNineGameActions(SessionService sessionService,
                                 SeatStateBroadcaster<NinetyNine, NinetyNineDto> broadcaster) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
    }

    /** Plays a card of the seat; {@code aceValue} (1 or 11) matters for an Ace only. */
    public void play(GameId id, PlayerId seat, Card card, Integer aceValue) throws NinetyNineException {
        NinetyNine game = sessionService.getGame(id, NinetyNine.class);
        synchronized (game) {
            announce(id, game, game.play(seat, card, aceValue), "Player " + seat + " played.");
        }
    }

    /**
     * Deals the next round when the game is still in the round end it was in at {@code version}; a stale call (the
     * state moved on, a forfeit, a new game) does nothing.
     */
    public void nextRound(GameId id, int version) throws NinetyNineException {
        NinetyNine game = sessionService.getGame(id, NinetyNine.class);
        synchronized (game) {
            if (game.version() != version || game.phase() != Phase.ROUND_OVER) {
                return;
            }
            announce(id, game, game.nextRound(), "A new round was dealt.");
        }
    }

    /**
     * Announces what the last forfeit of the game caused (elimination, bust, end of the game), after the generic
     * FORFEIT broadcast. The forfeit itself is announced by the lifecycle broadcaster.
     */
    public void announceForfeitAftermath(NinetyNine game, String message) {
        synchronized (game) {
            announce(game.getId(), game, game.lastForfeitEvents(), message);
        }
    }

    private void announce(GameId id, NinetyNine game, List<NinetyNineEvent> events, String message) {
        sessionService.touch(id);
        for (NinetyNineEvent event : events) {
            NinetyNineEventType type = NinetyNineEventType.of(event);
            if (type != null) {
                broadcaster.broadcast(game, type.toString(), NinetyNineEventData.from(event), message);
            }
        }
    }
}
