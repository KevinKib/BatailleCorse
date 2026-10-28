package org.kevinkib.cardgames.blackjack.presentation;

import org.kevinkib.cardgames.blackjack.domain.Blackjack;
import org.kevinkib.cardgames.blackjack.domain.BlackjackException;
import org.kevinkib.cardgames.blackjack.domain.event.BlackjackEvent;
import org.kevinkib.cardgames.blackjack.presentation.dto.BlackjackDto;
import org.kevinkib.cardgames.blackjack.presentation.dto.event.BlackjackEventData;
import org.kevinkib.cardgames.blackjack.presentation.dto.event.BlackjackEventType;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;

import java.util.List;

/**
 * The Blackjack use cases, shared by humans and the bots: domain call, session touch, then one broadcast per domain
 * event of the command (a stand that ends the hand announces STAND, DEALER_PLAY, SETTLE, each with the state after
 * the whole command). Authentication stays with the caller; domain exceptions propagate to it. The call and the
 * broadcasts run under the monitor of the game, so two commands' events leave in the order the state changed.
 */
public class BlackjackGameActions {

    private final SessionService sessionService;
    private final SeatStateBroadcaster<Blackjack, BlackjackDto> broadcaster;

    public BlackjackGameActions(SessionService sessionService,
                                SeatStateBroadcaster<Blackjack, BlackjackDto> broadcaster) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
    }

    public void bet(GameId id, PlayerId seat, int amount) throws BlackjackException {
        Blackjack game = sessionService.getGame(id, Blackjack.class);
        synchronized (game) {
            announce(id, game, game.bet(seat, amount), "Player " + seat + " bet.");
        }
    }

    public void hit(GameId id, PlayerId seat) throws BlackjackException {
        Blackjack game = sessionService.getGame(id, Blackjack.class);
        synchronized (game) {
            announce(id, game, game.hit(seat), "Player " + seat + " hit.");
        }
    }

    public void stand(GameId id, PlayerId seat) throws BlackjackException {
        Blackjack game = sessionService.getGame(id, Blackjack.class);
        synchronized (game) {
            announce(id, game, game.stand(seat), "Player " + seat + " stood.");
        }
    }

    public void doubleDown(GameId id, PlayerId seat) throws BlackjackException {
        Blackjack game = sessionService.getGame(id, Blackjack.class);
        synchronized (game) {
            announce(id, game, game.doubleDown(seat), "Player " + seat + " doubled.");
        }
    }

    public void split(GameId id, PlayerId seat) throws BlackjackException {
        Blackjack game = sessionService.getGame(id, Blackjack.class);
        synchronized (game) {
            announce(id, game, game.split(seat), "Player " + seat + " split.");
        }
    }

    /**
     * Announces what the last forfeit of the game caused (a deal, the dealer's turn, the end of the game), after the
     * generic FORFEIT broadcast. The forfeit itself is announced by the lifecycle broadcaster.
     */
    public void announceForfeitAftermath(Blackjack game, String message) {
        synchronized (game) {
            announce(game.getId(), game, game.lastForfeitEvents(), message);
        }
    }

    private void announce(GameId id, Blackjack game, List<BlackjackEvent> events, String message) {
        sessionService.touch(id);
        for (BlackjackEvent event : events) {
            BlackjackEventType type = BlackjackEventType.of(event);
            if (type != null) {
                broadcaster.broadcast(game, type.toString(), BlackjackEventData.from(event), message);
            }
        }
    }
}
