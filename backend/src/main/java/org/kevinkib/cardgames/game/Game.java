package org.kevinkib.cardgames.game;

import java.util.List;

/** What the session/lifecycle layer needs from any game. Presentation concerns stay per-game. */
public interface Game {

    GameId getId();

    boolean isFinished();

    List<PlayerId> getPlayerIds();

    void forfeit(PlayerId loser);
}
