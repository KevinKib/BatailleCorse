package org.kevinkib.cardgames.blackjack.presentation.dto.event;

import org.kevinkib.cardgames.blackjack.domain.event.BetPlaced;
import org.kevinkib.cardgames.blackjack.domain.event.BlackjackEvent;
import org.kevinkib.cardgames.blackjack.domain.event.Busted;
import org.kevinkib.cardgames.blackjack.domain.event.CardDrawn;
import org.kevinkib.cardgames.blackjack.domain.event.DealerBlackjack;
import org.kevinkib.cardgames.blackjack.domain.event.DealerPlayed;
import org.kevinkib.cardgames.blackjack.domain.event.Doubled;
import org.kevinkib.cardgames.blackjack.domain.event.Eliminated;
import org.kevinkib.cardgames.blackjack.domain.event.GameFinished;
import org.kevinkib.cardgames.blackjack.domain.event.HandDealt;
import org.kevinkib.cardgames.blackjack.domain.event.HandSettled;
import org.kevinkib.cardgames.blackjack.domain.event.SplitHand;
import org.kevinkib.cardgames.blackjack.domain.event.Stood;

/** What the table is told about, one broadcast per domain event of a command, in the order they happened. */
public enum BlackjackEventType {
    START, BET, DEAL, DEALER_BLACKJACK, HIT, STAND, DOUBLE, SPLIT, BUST, DEALER_PLAY, SETTLE, ELIMINATED, GAME_OVER;

    /** The type announcing a domain event; null for a forfeit, which the lifecycle broadcaster announces. */
    public static BlackjackEventType of(BlackjackEvent event) {
        if (event instanceof BetPlaced) {
            return BET;
        } else if (event instanceof HandDealt) {
            return DEAL;
        } else if (event instanceof DealerBlackjack) {
            return DEALER_BLACKJACK;
        } else if (event instanceof CardDrawn) {
            return HIT;
        } else if (event instanceof Stood) {
            return STAND;
        } else if (event instanceof Doubled) {
            return DOUBLE;
        } else if (event instanceof SplitHand) {
            return SPLIT;
        } else if (event instanceof Busted) {
            return BUST;
        } else if (event instanceof DealerPlayed) {
            return DEALER_PLAY;
        } else if (event instanceof HandSettled) {
            return SETTLE;
        } else if (event instanceof Eliminated) {
            return ELIMINATED;
        } else if (event instanceof GameFinished) {
            return GAME_OVER;
        }
        return null;
    }

    @Override
    public String toString() {
        return name();
    }
}
