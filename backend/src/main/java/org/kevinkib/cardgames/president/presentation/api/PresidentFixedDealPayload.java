package org.kevinkib.cardgames.president.presentation.api;

import java.util.List;

/**
 * The leading cards of one round, in deal order, as codes such as {@code 7H}, {@code 10S}, {@code KD}
 * (rank A, 2 to 10, J, Q, K then suit H, D, C or S). {@code round} defaults to 1.
 */
public record PresidentFixedDealPayload(Integer round, List<String> cards) {
}
