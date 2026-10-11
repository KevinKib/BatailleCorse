package org.kevinkib.cardgames.threemenmorris.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The Three Men's Morris application layer wired over a real session core and a recording messaging double. */
final class ThreeMenMorrisTable {

    static final class RecordingMessaging extends org.kevinkib.cardgames.presentation.TokenResolvingMessaging {
        final List<PlayerId> seats = Collections.synchronizedList(new ArrayList<>());
        final List<Response> payloads = Collections.synchronizedList(new ArrayList<>());

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
    final SessionService sessions =
            new SessionService(repository, new GameFactories(List.of(new ThreeMenMorrisFactory())));
    final RecordingMessaging messaging = new RecordingMessaging();
    { messaging.resolver = sessions; }
    final ThreeMenMorrisStateBroadcaster broadcaster =
            new ThreeMenMorrisStateBroadcaster(messaging, sessions::seatNames);
    final ThreeMenMorrisGameActions actions = new ThreeMenMorrisGameActions(sessions, broadcaster);
    final LobbyBroadcaster lobbyBroadcaster = new LobbyBroadcaster(messaging);
    final ThreeMenMorrisWebSocketController ws =
            new ThreeMenMorrisWebSocketController(sessions, broadcaster, messaging, actions);
    final ThreeMenMorrisRestController rest =
            new ThreeMenMorrisRestController(sessions, broadcaster, lobbyBroadcaster);

    /** Registers a started game with both seats claimed (one token per seat). */
    ThreeMenMorris install(ThreeMenMorris game) {
        SessionGame session = SessionGame.create(game.getId(), game.getPlayerIds(), ThreeMenMorrisFactory.GAME_TYPE);
        session.claimAllSeats();
        repository.save(game, session);
        return game;
    }

    ThreeMenMorris newGame() {
        return install(new ThreeMenMorris(GameId.generate()));
    }

    String token(ThreeMenMorris game, int seat) {
        return sessions.tokenForSeat(game.getId(), new PlayerId(seat));
    }
}
