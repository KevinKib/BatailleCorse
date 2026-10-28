package org.kevinkib.cardgames.blackjack.domain.event;

/** What a successful command changed, in order. The hole card appears only in the event that shows it. */
public sealed interface BlackjackEvent
        permits BetPlaced, HandDealt, DealerBlackjack, CardDrawn, Stood, Doubled, SplitHand, Busted, DealerPlayed,
        HandSettled, Eliminated, GameFinished, Forfeited {
}
