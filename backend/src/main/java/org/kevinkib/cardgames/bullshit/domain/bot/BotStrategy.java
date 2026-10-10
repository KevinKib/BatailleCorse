package org.kevinkib.cardgames.bullshit.domain.bot;

/** Port: how a bot decides, from what its seat can see and what it has learned so far. */
public interface BotStrategy {

    BotDecision decide(BotObservation observation, BotMemory memory);
}
