package org.kevinkib.cardgames.blackjack.presentation.api;

import java.util.List;

/**
 * Test seam payload: {@code hands} holds one scripted shoe prefix per hand, hand 1 first, each a list of card codes
 * such as {@code AS}, {@code 10H}, {@code KD} (rank A, 2 to 10, J, Q, K then suit H, D, C or S) in the deal order, then
 * the hit, split and dealer cards in the order they are drawn. A null entry leaves that hand random.
 */
public record BlackjackFixedShoePayload(List<List<String>> hands) {
}
