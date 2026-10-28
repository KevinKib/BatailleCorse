package org.kevinkib.cardgames.kilodemerde.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeException;
import org.kevinkib.cardgames.kilodemerde.domain.Phase;
import org.kevinkib.cardgames.kilodemerde.domain.event.KiloDeMerdeEvent;
import org.kevinkib.cardgames.kilodemerde.presentation.dto.KiloDeMerdeDto;
import org.kevinkib.cardgames.kilodemerde.presentation.dto.event.KiloDeMerdeEventData;
import org.kevinkib.cardgames.kilodemerde.presentation.dto.event.KiloDeMerdeEventType;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cards.domain.Card;

import java.util.List;

/**
 * The Kilo de merde use cases, shared by humans, the bots and the three timers: domain call, session touch, then one
 * broadcast per domain event of the command (a last choice announces PASS_CHOSEN then STEP; the last slap of a race
 * announces SLAP, DRAW, ROUND_OVER), each with the state after the whole command. Authentication stays with the
 * caller; domain exceptions propagate to it. The call and the broadcasts run under the monitor of the game, so two
 * commands' events leave in the order the state changed.
 *
 * <p>The timer entry points take the game object they were armed for and the counter that keyed them; a call whose
 * key no longer matches (the step or the race moved on, a forfeit voided the round, a rematch replaced the game) does
 * nothing, so a late timer can never act on a state it was not armed for.
 */
public class KiloDeMerdeGameActions {

    private final SessionService sessionService;
    private final SeatStateBroadcaster<KiloDeMerde, KiloDeMerdeDto> broadcaster;

    public KiloDeMerdeGameActions(SessionService sessionService,
                                  SeatStateBroadcaster<KiloDeMerde, KiloDeMerdeDto> broadcaster) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
    }

    /** The seat chooses the card it passes this step; the last choice resolves the step. */
    public void choosePass(GameId id, PlayerId seat, Card card) throws KiloDeMerdeException {
        KiloDeMerde game = sessionService.getGame(id, KiloDeMerde.class);
        synchronized (game) {
            announce(id, game, game.choosePass(seat, card), "Player " + seat + " chose a card to pass.");
        }
    }

    /** The seat slaps the pile: it opens a race, or joins the open one, or draws for a false slap. */
    public void slap(GameId id, PlayerId seat) throws KiloDeMerdeException {
        KiloDeMerde game = sessionService.getGame(id, KiloDeMerde.class);
        synchronized (game) {
            announce(id, game, game.slap(seat), "Player " + seat + " slapped.");
        }
    }

    /** The pass timer of {@code key} expired: the server passes for every seat that has not chosen. */
    public void expirePassTimer(GameId id, KiloDeMerde expected, KiloDeMerdeStepKey key) throws KiloDeMerdeException {
        KiloDeMerde game = sessionService.getGame(id, KiloDeMerde.class);
        synchronized (game) {
            if (game != expected || game.phase() != Phase.PASSING || !KiloDeMerdeStepKey.of(game).equals(key)) {
                return;
            }
            announce(id, game, game.expirePassTimer(key.step()), "The pass timer expired.");
        }
    }

    /** The race timeout of {@code raceId} expired: every seat that has not slapped draws a kilo card. */
    public void closeRace(GameId id, KiloDeMerde expected, int raceId) throws KiloDeMerdeException {
        KiloDeMerde game = sessionService.getGame(id, KiloDeMerde.class);
        synchronized (game) {
            if (game != expected || game.phase() != Phase.RACE || game.raceId() != raceId) {
                return;
            }
            announce(id, game, game.closeRace(raceId), "The race timed out.");
        }
    }

    /** Deals the next round (or ends the game) when the game is still at the end of round {@code round}. */
    public void nextRound(GameId id, KiloDeMerde expected, int round) throws KiloDeMerdeException {
        KiloDeMerde game = sessionService.getGame(id, KiloDeMerde.class);
        synchronized (game) {
            if (game != expected || game.phase() != Phase.ROUND_OVER || game.round() != round) {
                return;
            }
            announce(id, game, game.nextRound(), "A new round was dealt.");
        }
    }

    /**
     * Announces what the last forfeit of the game caused (the new deal, the end of the game), after the generic
     * FORFEIT broadcast. The forfeit itself is announced by the lifecycle broadcaster.
     */
    public void announceForfeitAftermath(KiloDeMerde game, String message) {
        synchronized (game) {
            announce(game.getId(), game, game.lastForfeitEvents(), message);
        }
    }

    private void announce(GameId id, KiloDeMerde game, List<KiloDeMerdeEvent> events, String message) {
        sessionService.touch(id);
        for (KiloDeMerdeEvent event : events) {
            KiloDeMerdeEventType type = KiloDeMerdeEventType.of(event);
            if (type != null) {
                broadcaster.broadcast(game, type.toString(), KiloDeMerdeEventData.from(event), message);
            }
        }
    }
}
