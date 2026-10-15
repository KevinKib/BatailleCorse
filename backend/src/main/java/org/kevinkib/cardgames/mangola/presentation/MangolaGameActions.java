package org.kevinkib.cardgames.mangola.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaEvent;
import org.kevinkib.cardgames.mangola.domain.MangolaException;
import org.kevinkib.cardgames.mangola.presentation.dto.MangolaDto;
import org.kevinkib.cardgames.mangola.presentation.dto.event.MangolaEventData;
import org.kevinkib.cardgames.mangola.presentation.dto.event.MangolaEventType;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;

/**
 * The Mangola use cases: domain call, session touch, then one broadcast. Authentication stays with the
 * caller; domain exceptions propagate to it. The call and the broadcast run under the monitor of the game, so
 * events leave in the order the state changed. Each seat gets its own projection, so a draft change reaches
 * the other seat only as a state in which nothing of the draft shows.
 */
public class MangolaGameActions {

    @FunctionalInterface
    private interface Command {
        MangolaEvent apply(Mangola game) throws MangolaException;
    }

    private final SessionService sessionService;
    private final SeatStateBroadcaster<Mangola, MangolaDto> broadcaster;

    public MangolaGameActions(SessionService sessionService, SeatStateBroadcaster<Mangola, MangolaDto> broadcaster) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
    }

    public void arrange(GameId id, PlayerId seat, int[] counts) throws MangolaException {
        run(id, g -> g.arrange(seat, counts), "Player " + seat + " changed their layout.");
    }

    public void confirmLayout(GameId id, PlayerId seat) throws MangolaException {
        run(id, g -> g.confirmLayout(seat), "Player " + seat + " confirmed their layout.");
    }

    public void editLayout(GameId id, PlayerId seat) throws MangolaException {
        run(id, g -> g.editLayout(seat), "Player " + seat + " is arranging again.");
    }

    public void sow(GameId id, PlayerId seat, int pit) throws MangolaException {
        run(id, g -> g.sow(seat, pit), "Player " + seat + " sowed.");
    }

    private void run(GameId gameId, Command command, String message) throws MangolaException {
        Mangola game = sessionService.getGame(gameId, Mangola.class);
        synchronized (game) {
            boolean finishedBefore = game.isFinished();
            MangolaEvent event = command.apply(game);
            sessionService.touch(gameId);
            MangolaEventType type = game.isFinished() && !finishedBefore
                    ? MangolaEventType.GAME_OVER
                    : MangolaEventData.typeOf(event);
            broadcaster.broadcast(game, type.toString(), MangolaEventData.from(event), message);
        }
    }
}
