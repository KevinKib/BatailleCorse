package org.kevinkib.cardgames.president.domain.bot;

/** Port: how a President bot decides, from what its seat can see and what it has learned so far. */
public interface PresidentBotStrategy {

    /**
     * The decision for one of the bot's own steps (its turn, its return in the exchange, the barrier); the view
     * offers at least one action.
     *
     * @throws IllegalStateException when the seat has nothing to do
     */
    PresidentBotDecision decide(PresidentBotView view, PresidentBotMemory memory);
}
