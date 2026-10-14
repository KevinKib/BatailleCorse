package org.kevinkib.cardgames.kobo.presentation.bot;

import org.kevinkib.cardgames.kobo.domain.bot.KoboBotTuning;

import java.time.Duration;

/** Port: how long a Kobo bot takes to react to a matching discard, given its level's tuning. */
@FunctionalInterface
public interface ReactionDelay {

    Duration next(KoboBotTuning tuning);
}
