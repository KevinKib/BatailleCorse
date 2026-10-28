package org.kevinkib.cardgames.ninetynine.domain.bot;

/** Port: how a 99 bot picks its card from what its seat can see. */
public interface NinetyNineBotStrategy {

    /**
     * The decision for the bot's turn; the card is always a safe one and an Ace carries an allowed value.
     *
     * @throws IllegalStateException when the seat has nothing to play
     */
    NinetyNineDecision decide(NinetyNineBotView view);
}
