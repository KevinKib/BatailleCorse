package org.kevinkib.cardgames.sessionmanagement.presence.port;

import java.time.Instant;

/** Schedules a forfeit to run at a deadline, returning a cancellable handle. */
public interface ForfeitScheduler {
    ScheduledForfeit schedule(Instant deadline, Runnable task);
}
