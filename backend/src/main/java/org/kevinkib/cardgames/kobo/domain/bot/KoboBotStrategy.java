package org.kevinkib.cardgames.kobo.domain.bot;

import java.util.Optional;

/** Port: how a Kobo bot decides, from what its seat can see and what it has learned so far. */
public interface KoboBotStrategy {

    /** The decision for one of the bot's own steps (barrier, turn phase or gift); the view offers an action. */
    KoboBotDecision decide(KoboBotView view, KoboBotMemory memory);

    /** A matching discard the bot can make right now with a card it remembers; empty when it should not try. */
    Optional<KoboBotDecision.Match> matchFor(KoboBotView view, KoboBotMemory memory);
}
