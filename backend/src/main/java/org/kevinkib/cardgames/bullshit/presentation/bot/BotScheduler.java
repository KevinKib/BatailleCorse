package org.kevinkib.cardgames.bullshit.presentation.bot;

import java.time.Duration;

/** Port: runs a task once after a delay, and lets the caller cancel it. */
public interface BotScheduler {

    BotTask schedule(Duration delay, Runnable task);

    /** Handle on a scheduled task. Cancelling a task that already ran is harmless. */
    @FunctionalInterface
    interface BotTask {
        void cancel();
    }
}
