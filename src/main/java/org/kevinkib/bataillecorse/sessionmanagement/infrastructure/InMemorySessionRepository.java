package org.kevinkib.bataillecorse.sessionmanagement.infrastructure;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
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

}
