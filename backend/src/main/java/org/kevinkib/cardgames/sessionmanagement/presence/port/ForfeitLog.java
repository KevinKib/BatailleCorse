package org.kevinkib.cardgames.sessionmanagement.presence.port;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;

import java.util.Map;

/** Records which seat forfeited a game and why, so the reason can be merged into game state. */
public interface ForfeitLog {
    void record(Seat seat, ForfeitReason reason);
    Map<Integer, ForfeitReason> reasonsBySeat(GameId gameId);
    void removeGame(GameId gameId);
}
