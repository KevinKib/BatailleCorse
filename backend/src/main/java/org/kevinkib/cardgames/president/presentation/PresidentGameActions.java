package org.kevinkib.cardgames.president.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.event.Played;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.president.domain.rules.PresidentException;
import org.kevinkib.cardgames.president.presentation.dto.PresidentDto;
import org.kevinkib.cardgames.president.presentation.dto.event.PresidentEventData;
import org.kevinkib.cardgames.president.presentation.dto.event.PresidentEventType;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cards.domain.Card;

import java.util.List;

/**
 * The President use cases: domain call, session touch, then one broadcast. Authentication stays with the
 * caller; domain exceptions propagate to it. The call and the broadcast run under the monitor of the game, so
 * two seats' events leave in the order the state changed.
 */
public class PresidentGameActions {

    @FunctionalInterface
    private interface Command {
        List<PresidentEvent> apply(President game) throws PresidentException;
    }

    private final SessionService sessionService;
    private final SeatStateBroadcaster<President, PresidentDto> broadcaster;

    public PresidentGameActions(SessionService sessionService,
                                SeatStateBroadcaster<President, PresidentDto> broadcaster) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
    }

    public void play(GameId id, PlayerId seat, List<Card> cards) throws PresidentException {
        run(id, seat, PresidentEventType.PLAY, g -> g.play(seat, cards), "Player " + seat + " played.");
    }

    public void pass(GameId id, PlayerId seat) throws PresidentException {
        run(id, seat, PresidentEventType.PASS, g -> g.pass(seat), "Player " + seat + " passed.");
    }

    public void giveBack(GameId id, PlayerId seat, List<Card> cards) throws PresidentException {
        run(id, seat, PresidentEventType.EXCHANGE, g -> g.giveBack(seat, cards),
                "Player " + seat + " gave cards back.");
    }

    public void ready(GameId id, PlayerId seat) throws PresidentException {
        run(id, seat, PresidentEventType.READY, g -> g.ready(seat), "Player " + seat + " is ready.");
    }

    private void run(GameId gameId, PlayerId seat, PresidentEventType command, Command action, String message)
            throws PresidentException {
        President game = sessionService.getGame(gameId, President.class);
        synchronized (game) {
            Phase before = game.phase();
            List<PresidentEvent> events = action.apply(game);
            sessionService.touch(gameId);
            // a play made out of turn is the completion of the four, announced as such
            PresidentEventType named = events.stream().anyMatch(e -> e instanceof Played p && p.outOfTurn())
                    ? PresidentEventType.COMPLETION : command;
            broadcaster.broadcast(game, broadcastType(before, game, named, events).toString(),
                    PresidentEventData.from(named, seat.id(), events), message);
        }
    }

    /**
     * The type follows the command: a command that finished the match is announced as GAME_OVER, one that
     * moved the game into ROUND_OVER as ROUND_END, a ready that opens the barrier as ROUND_START, anything
     * else by its own type (a completion out of turn is COMPLETION). The data still names the command.
     */
    private static PresidentEventType broadcastType(Phase before, President game, PresidentEventType command,
                                                    List<PresidentEvent> events) {
        if (game.isFinished() && before != Phase.FINISHED) {
            return PresidentEventType.GAME_OVER;
        }
        if (game.phase() == Phase.ROUND_OVER && before != Phase.ROUND_OVER) {
            return PresidentEventType.ROUND_END;
        }
        if (PresidentEventData.opened(events)) {
            return PresidentEventType.ROUND_START;
        }
        return command;
    }
}
