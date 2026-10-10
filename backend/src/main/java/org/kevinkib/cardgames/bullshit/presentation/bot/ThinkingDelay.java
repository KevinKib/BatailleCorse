package org.kevinkib.cardgames.bullshit.presentation.bot;

import java.time.Duration;

/** Port: how long a bot "thinks" before its next action. */
@FunctionalInterface
public interface ThinkingDelay {

    Duration next();
}
