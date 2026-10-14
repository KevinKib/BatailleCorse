package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;

/**
 * Port: what a computer player does at a position. A strategy is pure (it reads a snapshot and returns a
 * decision); the coordinator applies the decision through the same use cases as a human.
 */
public interface MorrisBot {

    /**
     * @param position         the snapshot of the game
     * @param seat             the seat the bot plays
     * @param drawOfferPending the opponent has offered a draw and waits for the bot's answer: the result is
     *                         then {@link MorrisDecision.AcceptDraw} or {@link MorrisDecision.DeclineDraw};
     *                         otherwise a {@link MorrisDecision.Place} or {@link MorrisDecision.Step}
     */
    MorrisDecision decide(MorrisPosition position, PlayerId seat, boolean drawOfferPending);
}
