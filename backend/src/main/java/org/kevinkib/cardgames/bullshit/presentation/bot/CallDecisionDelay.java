package org.kevinkib.cardgames.bullshit.presentation.bot;

import java.time.Duration;

/**
 * Port: how long a bot hesitates between deciding to call "Bullshit" and actually calling, on top of its
 * thinking delay. It is the window a human has to call first; a zero delay means "call at once".
 */
@FunctionalInterface
public interface CallDecisionDelay {

    Duration next();
}
