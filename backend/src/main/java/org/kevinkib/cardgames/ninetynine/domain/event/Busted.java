package org.kevinkib.cardgames.ninetynine.domain.event;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

import java.util.List;

/** A seat held no safe card and lost a life; its stuck hand is revealed as the proof. */
public record Busted(PlayerId seat, int totalBefore, List<Card> hand, int livesLeft) implements NinetyNineEvent {

    public Busted {
        hand = List.copyOf(hand);
    }
}
