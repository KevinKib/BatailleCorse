package org.kevinkib.cardgames.sessionmanagement.core.application.port;

/**
 * Outbound port: what players do, counted so the owner can tell whether the site is really played. Events carry the
 * game type key and numbers only, never a name, a token, an address or a game id. The default adapter counts with
 * Micrometer and writes one log line per event; {@link #NONE} is the no-op used when nobody listens.
 */
public interface UsageStatistics {

    /** A room was opened by its host. */
    void gameCreated(String gameType);

    /** The host started the game, with the number of human and computer seats at that moment. */
    void gameStarted(String gameType, int humans, int bots);

    /** A started game reached its end (a win, or a forfeit). Reported once per game. */
    void gameFinished(String gameType);

    /** A finished or running game was reopened for a rematch. Reported once per reopening. */
    void rematch(String gameType);

    /** A human took a seat in an open room (the host's own seat is counted by {@link #gameCreated}). */
    void seatJoined(String gameType);

    /** The host filled a seat with a computer player. */
    void botAdded(String gameType);

    UsageStatistics NONE = new UsageStatistics() {
        @Override public void gameCreated(String gameType) { }
        @Override public void gameStarted(String gameType, int humans, int bots) { }
        @Override public void gameFinished(String gameType) { }
        @Override public void rematch(String gameType) { }
        @Override public void seatJoined(String gameType) { }
        @Override public void botAdded(String gameType) { }
    };
}
