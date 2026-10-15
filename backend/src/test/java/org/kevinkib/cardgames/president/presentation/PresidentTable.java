package org.kevinkib.cardgames.president.presentation;

import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.PresidentFactory;
import org.kevinkib.cardgames.president.presentation.dto.PresidentDto;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.presentation.support.SessionTable;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

/** The President application layer wired over a real session core and a recording messaging double. */
final class PresidentTable {

    private final SessionTable base = new SessionTable(new PresidentFactory(), new BullshitFactory());

    final InMemorySessionRepository repository = base.repository;
    final SessionService sessions = base.sessions;
    final RecordingMessaging messaging = base.messaging;
    final SeatStateBroadcaster<President, PresidentDto> broadcaster = new SeatStateBroadcaster<>(messaging,
            sessions::botSeats, sessions::seatNames,
            (game, viewer, names, bots) -> PresidentDto.forViewer(game, viewer, names, bots));
    final PresidentGameActions actions = new PresidentGameActions(sessions, broadcaster);
    final LobbyBroadcaster lobbyBroadcaster = new LobbyBroadcaster(messaging);
    final PresidentWebSocketController ws = new PresidentWebSocketController(sessions, broadcaster,
            new SeatCommands(sessions, messaging), actions);
    final PresidentRestController rest = new PresidentRestController(sessions, broadcaster, lobbyBroadcaster);

    /** Registers an already built game with every seat claimed (one token per seat). */
    President install(President game) {
        return base.install(game, PresidentFactory.GAME_TYPE);
    }

    String token(President game, int seat) {
        return base.token(game, seat);
    }
}
