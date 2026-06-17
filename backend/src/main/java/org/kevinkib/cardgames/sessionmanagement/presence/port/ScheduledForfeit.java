package org.kevinkib.cardgames.sessionmanagement.presence.port;

/** Handle to a scheduled forfeit; cancelling it prevents the forfeit from running. */
public interface ScheduledForfeit {
    void cancel();
}
