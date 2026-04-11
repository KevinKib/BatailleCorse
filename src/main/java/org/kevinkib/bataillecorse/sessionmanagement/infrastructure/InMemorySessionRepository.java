package org.kevinkib.bataillecorse.sessionmanagement.infrastructure;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.sessionmanagement.application.port.SessionRepository;

import java.util.ArrayList;
import java.util.List;

public class InMemorySessionRepository implements SessionRepository {

    private final List<BatailleCorse> games;

    public InMemorySessionRepository() {
        this.games = new ArrayList<>();
    }

    @Override
    public void save(BatailleCorse batailleCorse) {
        games.add(batailleCorse);
    }

    @Override
    public BatailleCorse load(BatailleCorseId id) {
        return games.stream()
                .filter(game -> game.getId().equals(id))
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }

}
