package org.kevinkib.bataillecorse.sessionmanagement.infrastructure;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.application.port.SessionRepository;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionGame;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken;

import java.util.ArrayList;
import java.util.List;

public class InMemorySessionRepository implements SessionRepository {

    private final List<BatailleCorse> games;
    private final List<SessionGame> sessionGames;

    public InMemorySessionRepository() {
        this.games = new ArrayList<>();
        this.sessionGames = new ArrayList<>();
    }

    @Override
    public void save(BatailleCorse batailleCorse, SessionGame sessionGame) {
        games.add(batailleCorse);
        sessionGames.add(sessionGame);
    }

    @Override
    public BatailleCorse load(BatailleCorseId id) {
        return games.stream()
                .filter(game -> game.getId().equals(id))
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }

    @Override
    public SessionToken loadSessionToken(BatailleCorseId batailleCorseId, PlayerId playerId) {
        return loadSessionGame(batailleCorseId)
                .findTokenByPlayer(playerId)
                .orElseThrow(IllegalArgumentException::new);
    }

    @Override
    public SessionGame loadSessionGame(BatailleCorseId id) {
        return sessionGames.stream()
                .filter(session -> session.id().equals(id))
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }

}
