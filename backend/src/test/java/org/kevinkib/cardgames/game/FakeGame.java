package org.kevinkib.cardgames.game;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/** Minimal Game test double for exercising the session layer without a real game. */
public class FakeGame implements Game {

    private final GameId id;
    private final List<PlayerId> playerIds;
    private boolean finished = false;

    public FakeGame(GameId id, int nbPlayers) {
        this.id = id;
        this.playerIds = new ArrayList<>(
                IntStream.range(0, nbPlayers).mapToObj(PlayerId::new).toList());
    }

    public void finish() {
        this.finished = true;
    }

    @Override
    public GameId getId() {
        return id;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public List<PlayerId> getPlayerIds() {
        return new ArrayList<>(playerIds);
    }

    @Override
    public void forfeit(PlayerId loser) {
        this.finished = true;
    }
}
