package org.kevinkib.cardgames.draughts.presentation;

import java.util.List;
import org.kevinkib.cardgames.draughts.domain.Draughts;
import org.kevinkib.cardgames.draughts.domain.DraughtsEvent;
import org.kevinkib.cardgames.draughts.domain.DraughtsException;
import org.kevinkib.cardgames.draughts.presentation.dto.DraughtsDto;
import org.kevinkib.cardgames.draughts.presentation.dto.event.DraughtsEventData;
import org.kevinkib.cardgames.draughts.presentation.dto.event.DraughtsEventType;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;

/**
 * The draughts use cases: domain call, session touch, then one broadcast. Authentication stays with the caller;
 * domain exceptions propagate to it. The call and the broadcast run under the monitor of the game, so events leave
 * in the order the state changed. Humans and bots go through the same methods.
 */
public class DraughtsGameActions {

    @FunctionalInterface
    private interface Command {
        DraughtsEvent apply(Draughts game) throws DraughtsException;
    }

    private final SessionService sessionService;
    private final SeatStateBroadcaster<Draughts, DraughtsDto> broadcaster;

    public DraughtsGameActions(SessionService sessionService, SeatStateBroadcaster<Draughts, DraughtsDto> broadcaster) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
    }

    public void move(GameId id, PlayerId seat, int from, int to, List<Integer> captures) throws DraughtsException {
        run(id, g -> g.move(seat, from, to, captures), "Player " + seat + " moved a piece.");
    }

    public void offerDraw(GameId id, PlayerId seat) throws DraughtsException {
        run(id, g -> g.offerDraw(seat), "Player " + seat + " offered a draw.");
    }

    public void acceptDraw(GameId id, PlayerId seat) throws DraughtsException {
        run(id, g -> g.acceptDraw(seat), "Player " + seat + " accepted the draw.");
    }

    public void declineDraw(GameId id, PlayerId seat) throws DraughtsException {
        run(id, g -> g.declineDraw(seat), "Player " + seat + " declined the draw.");
    }

    private void run(GameId gameId, Command command, String message) throws DraughtsException {
        Draughts game = sessionService.getGame(gameId, Draughts.class);
        synchronized (game) {
            boolean finishedBefore = game.isFinished();
            DraughtsEvent event = command.apply(game);
            sessionService.touch(gameId);
            DraughtsEventType type = game.isFinished() && !finishedBefore
                    ? DraughtsEventType.GAME_OVER
                    : DraughtsEventData.typeOf(event);
            broadcaster.broadcast(game, type.toString(), DraughtsEventData.from(event), message);
        }
    }
}
