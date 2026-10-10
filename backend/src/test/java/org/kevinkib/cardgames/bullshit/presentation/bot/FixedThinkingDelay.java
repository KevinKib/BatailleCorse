package org.kevinkib.cardgames.bullshit.presentation.bot;

import java.time.Duration;

/** A thinking delay that never varies, so tests can assert on it. */
public final class FixedThinkingDelay implements ThinkingDelay {

    private final Duration delay;

    public FixedThinkingDelay(Duration delay) {
        this.delay = delay;
    }

    @Override
    public Duration next() {
        return delay;
    }
}
