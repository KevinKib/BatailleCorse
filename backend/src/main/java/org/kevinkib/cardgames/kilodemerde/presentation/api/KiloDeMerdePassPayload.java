package org.kevinkib.cardgames.kilodemerde.presentation.api;

import org.kevinkib.cardgames.presentation.dto.CardDto;

/** The card a seat passes to its left neighbour this step. */
public record KiloDeMerdePassPayload(String gameId, String token, CardDto card) {
}
