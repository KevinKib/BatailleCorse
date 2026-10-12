package org.kevinkib.cardgames.threemenmorris.presentation;

import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.presentation.support.SessionTable;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory;

/** The Three Men's Morris application layer wired over a real session core and a recording messaging double. */
final class ThreeMenMorrisTable {

    private final SessionTable base = new SessionTable(new ThreeMenMorrisFactory());

    final InMemorySessionRepository repository = base.repository;
    final SessionService sessions = base.sessions;
    final RecordingMessaging messaging = base.messaging;
    final ThreeMenMorrisStateBroadcaster broadcaster =
            new ThreeMenMorrisStateBroadcaster(messaging, sessions::seatNames);
    final ThreeMenMorrisGameActions actions = new ThreeMenMorrisGameActions(sessions, broadcaster);
    final LobbyBroadcaster lobbyBroadcaster = new LobbyBroadcaster(messaging);
    final ThreeMenMorrisWebSocketController ws =
            new ThreeMenMorrisWebSocketController(sessions, broadcaster, new SeatCommands(sessions, messaging), actions);
    final ThreeMenMorrisRestController rest =
            new ThreeMenMorrisRestController(sessions, broadcaster, lobbyBroadcaster);

    /** Registers a started game with both seats claimed (one token per seat). */
    ThreeMenMorris install(ThreeMenMorris game) {
        return base.install(game, ThreeMenMorrisFactory.GAME_TYPE);
    }

    ThreeMenMorris newGame() {
        return install(new ThreeMenMorris(GameId.generate()));
    }

    String token(ThreeMenMorris game, int seat) {
        return base.token(game, seat);
    }
}
