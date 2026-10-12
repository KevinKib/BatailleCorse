package org.kevinkib.cardgames.kobo.presentation;

import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.KoboFactory;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.presentation.support.SessionTable;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

/** The Kobo application layer wired over a real session core and a recording messaging double. */
final class KoboTable {

    private final SessionTable base = new SessionTable(new KoboFactory(), new org.kevinkib.cardgames.bullshit.domain.BullshitFactory());

    final InMemorySessionRepository repository = base.repository;
    final SessionService sessions = base.sessions;
    final RecordingMessaging messaging = base.messaging;
    final KoboStateBroadcaster broadcaster = new KoboStateBroadcaster(messaging, sessions::seatNames);
    final KoboGameActions actions = new KoboGameActions(sessions, broadcaster);
    final LobbyBroadcaster lobbyBroadcaster = new LobbyBroadcaster(messaging);
    final KoboWebSocketController ws = new KoboWebSocketController(sessions, broadcaster,
            new SeatCommands(sessions, messaging), actions);
    final KoboRestController rest = new KoboRestController(sessions, broadcaster, lobbyBroadcaster);

    /** Registers an already built game with every seat claimed (one token per seat). */
    Kobo install(Kobo game) {
        return base.install(game, KoboFactory.GAME_TYPE);
    }

    String token(Kobo game, int seat) {
        return base.token(game, seat);
    }
}
