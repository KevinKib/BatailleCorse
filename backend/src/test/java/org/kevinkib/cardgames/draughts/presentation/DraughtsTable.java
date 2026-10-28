package org.kevinkib.cardgames.draughts.presentation;

import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.draughts.domain.Draughts;
import org.kevinkib.cardgames.draughts.domain.DraughtsFactory;
import org.kevinkib.cardgames.draughts.presentation.dto.DraughtsDto;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.bot.BotSeats;
import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.presentation.support.SessionTable;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

/** The draughts application layer wired over a real session core and a recording messaging double. */
final class DraughtsTable {

    private final SessionTable base = new SessionTable(new DraughtsFactory(), new BullshitFactory());

    final InMemorySessionRepository repository = base.repository;
    final SessionService sessions = base.sessions;
    final RecordingMessaging messaging = base.messaging;
    final SeatStateBroadcaster<Draughts, DraughtsDto> broadcaster =
            new SeatStateBroadcaster<>(messaging, BotSeats.none(), sessions::seatNames,
                    (game, viewer, names, bots) -> DraughtsDto.forViewer(game, viewer, names));
    final DraughtsGameActions actions = new DraughtsGameActions(sessions, broadcaster);
    final LobbyBroadcaster lobbyBroadcaster = new LobbyBroadcaster(messaging);
    final DraughtsWebSocketController ws =
            new DraughtsWebSocketController(sessions, broadcaster, new SeatCommands(sessions, messaging), actions);
    final DraughtsRestController rest = new DraughtsRestController(sessions, broadcaster, lobbyBroadcaster);

    /** Registers a started game with both seats claimed (one token per seat). */
    Draughts install(Draughts game) {
        return base.install(game, DraughtsFactory.GAME_TYPE);
    }

    Draughts newGame() {
        return install(new Draughts(GameId.generate(), new org.kevinkib.cardgames.game.PlayerId(0)));
    }

    String token(Draughts game, int seat) {
        return base.token(game, seat);
    }
}
