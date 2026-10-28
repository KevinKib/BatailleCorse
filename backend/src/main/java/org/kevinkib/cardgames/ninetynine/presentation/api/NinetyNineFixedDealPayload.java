package org.kevinkib.cardgames.ninetynine.presentation.api;

import java.util.List;

/**
 * Test seam payload. {@code cards}: the leading cards of round {@code round} (default 1) in deal order, as codes such
 * as {@code 7H}, {@code 10S}, {@code KD} (rank A, 2 to 10, J, Q, K then suit H, D, C or S). {@code firstSeat}: the
 * seat (0-based) that starts round 1. {@code reshuffle}: the leading cards of the next reshuffle. Each part is
 * optional, but at least one is required.
 */
public record NinetyNineFixedDealPayload(Integer round, List<String> cards, Integer firstSeat, List<String> reshuffle) {
}
