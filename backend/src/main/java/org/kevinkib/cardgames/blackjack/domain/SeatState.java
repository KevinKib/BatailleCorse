package org.kevinkib.cardgames.blackjack.domain;

import java.util.List;

/**
 * A snapshot of one seat: its stack, its bet (placed while betting, the first hand's bet afterwards), its hands
 * (none while betting, one or two while playing), the hand being played, why it left the game if it did and its final
 * place once the game is over. Immutable.
 *
 * @param eliminatedAtHand the hand whose settlement took the seat to 0 points, 0 when it did not
 * @param forfeited        whether the seat forfeited (it is then out of the game too)
 * @param place            the final place (1 is the best), null until the game is over and always null in solo
 */
public record SeatState(int stack, int bet, boolean betPlaced, List<PlayerHand> hands, int activeHandIndex,
                        int eliminatedAtHand, boolean forfeited, Integer place) {

    public SeatState {
        hands = List.copyOf(hands);
    }

    /** Whether the seat is out of the game, by reaching 0 points or by forfeiting. */
    public boolean eliminated() {
        return forfeited || eliminatedAtHand > 0;
    }
}
