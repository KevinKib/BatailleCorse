package org.kevinkib.cardgames.presentation.support;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.List;

/**
 * A real session core (in-memory repository and {@link SessionService} over the given factories) with a
 * {@link RecordingMessaging} that resolves tokens through it. Per-game tables build their broadcasters,
 * actions and controllers on top of it.
 */
public final class SessionTable {

    public final InMemorySessionRepository repository = new InMemorySessionRepository(Clock.systemUTC());
    public final SessionService sessions;
    public final RecordingMessaging messaging;

    public SessionTable(GameFactory... factories) {
        sessions = new SessionService(repository, new GameFactories(List.of(factories)));
        messaging = new RecordingMessaging(sessions);
    }

    /** Registers an already built game with every seat claimed (one token per seat). */
    public <G extends Game> G install(G game, String gameType) {
        SessionGame session = SessionGame.create(game.getId(), game.getPlayerIds(), gameType);
        game.getPlayerIds().forEach(seat -> session.claimNextFreeSeat(null));
        repository.save(game, session);
        return game;
    }

    public String token(Game game, int seat) {
        return sessions.tokenForSeat(game.getId(), new PlayerId(seat));
    }
}
