package org.kevinkib.cardgames.blackjack.domain.bot;

import org.kevinkib.cardgames.blackjack.domain.Action;

/** Port: how a Blackjack bot picks its bet and its play from what its seat can see. */
public interface BlackjackBotStrategy {

    /**
     * The bet of a hand, from {@code view.minBet()} to {@code view.maxBet()}.
     *
     * @throws IllegalStateException when the seat cannot bet
     */
    int decideBet(BlackjackBotView view);

    /**
     * The action for the bot's turn; always one of {@code view.availableActions()}.
     *
     * @throws IllegalStateException when the seat has nothing to play
     */
    Action decidePlay(BlackjackBotView view);
}
