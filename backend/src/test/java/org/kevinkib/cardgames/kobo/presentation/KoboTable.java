package org.kevinkib.cardgames.kobo.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.KoboFactory;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The Kobo application layer wired over a real session core and a recording messaging double. */
final class KoboTable {

    static final class RecordingMessaging extends GameMessagingService {
        final List<PlayerId> seats = Collections.synchronizedList(new ArrayList<>());
        final List<Response> payloads = Collections.synchronizedList(new ArrayList<>());

        RecordingMessaging() {
            super(null, null);
        }

        @Override
        public void sendToSeat(GameId gameId, PlayerId seat, Object payload) {
            seats.add(seat);
            payloads.add((Response) payload);
        }

        void clear() {
            seats.clear();
            payloads.clear();
        }
    }

    final InMemorySessionRepository repository = new InMemorySessionRepository(Clock.systemUTC());
    final SessionService sessions = new SessionService(repository, new GameFactories(List.of(new KoboFactory())));
    final RecordingMessaging messaging = new RecordingMessaging();
    final KoboStateBroadcaster broadcaster = new KoboStateBroadcaster(messaging, sessions::seatNames);
    final KoboGameActions actions = new KoboGameActions(sessions, broadcaster);
    final LobbyBroadcaster lobbyBroadcaster = new LobbyBroadcaster(messaging, sessions);
    final KoboWebSocketController ws = new KoboWebSocketController(sessions, broadcaster, messaging, actions);
    final KoboRestController rest = new KoboRestController(sessions, broadcaster, lobbyBroadcaster);

    /** Registers an already built game with every seat claimed (one token per seat). */
    Kobo install(Kobo game) {
        repository.save(game, solo(game));
        return game;
    }

    private static SessionGame solo(Kobo game) {
        SessionGame session = SessionGame.create(game.getId(), game.getPlayerIds(), KoboFactory.GAME_TYPE);
        session.claimAllSeats();
        return session;
    }

    String token(Kobo game, int seat) {
        return sessions.tokenForSeat(game.getId(), new PlayerId(seat));
    }
}
