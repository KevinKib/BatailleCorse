package org.kevinkib.cardgames.kilodemerde.presentation.api;

import org.kevinkib.cardgames.presentation.dto.CardDto;

import java.util.List;

/**
 * Test seam payload. {@code hands}: the leading hands of the next deal, in seat order (the remaining seats are dealt
 * at random). {@code pile}: the kilo pile, top card first, used exactly as given (a one-card pile makes a one-round
 * game). Each part is optional, but at least one is required.
 */
public record KiloDeMerdeFixedDealPayload(List<List<CardDto>> hands, List<CardDto> pile) {
}
