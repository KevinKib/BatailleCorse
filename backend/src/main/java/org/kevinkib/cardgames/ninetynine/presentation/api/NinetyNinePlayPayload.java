package org.kevinkib.cardgames.ninetynine.presentation.api;

import org.kevinkib.cardgames.presentation.dto.CardDto;

/** The card a seat plays; {@code aceValue} (1 or 11) is read for an Ace only and may be null otherwise. */
public record NinetyNinePlayPayload(String gameId, String token, CardDto card, Integer aceValue) {
}
