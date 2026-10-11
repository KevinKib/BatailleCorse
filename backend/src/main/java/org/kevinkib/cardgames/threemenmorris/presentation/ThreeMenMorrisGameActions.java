package org.kevinkib.cardgames.threemenmorris.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisEvent;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisException;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.event.ThreeMenMorrisEventData;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.event.ThreeMenMorrisEventType;

/**
 * The Three Men's Morris use cases: domain call, session touch, then one broadcast. Authentication
 * stays with the caller; domain exceptions propagate to it. The call and the broadcast run under the
 * monitor of the game, so events leave in the order the state changed.
 */
public class ThreeMenMorrisGameActions {

    @FunctionalInterface
    private interface Command {
        MorrisEvent apply(ThreeMenMorris game) throws ThreeMenMorrisException;
    }

    private final SessionService sessionService;
    private final ThreeMenMorrisStateBroadcaster broadcaster;

    public ThreeMenMorrisGameActions(SessionService sessionService, ThreeMenMorrisStateBroadcaster broadcaster) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
    }

    public void place(GameId id, PlayerId seat, int point) throws ThreeMenMorrisException {
        run(id, g -> g.place(seat, point), "Player " + seat + " placed a piece.");
    }

    public void move(GameId id, PlayerId seat, int from, int to) throws ThreeMenMorrisException {
        run(id, g -> g.move(seat, from, to), "Player " + seat + " moved a piece.");
    }

    private void run(GameId gameId, Command command, String message) throws ThreeMenMorrisException {
        ThreeMenMorris game = sessionService.getGame(gameId, ThreeMenMorris.class);
        synchronized (game) {
            boolean finishedBefore = game.isFinished();
            MorrisEvent event = command.apply(game);
            sessionService.touch(gameId);
            ThreeMenMorrisEventType type = game.isFinished() && !finishedBefore
                    ? ThreeMenMorrisEventType.GAME_OVER
                    : ThreeMenMorrisEventData.typeOf(event);
            broadcaster.broadcast(game, type.toString(), ThreeMenMorrisEventData.from(event), message);
        }
    }
}
