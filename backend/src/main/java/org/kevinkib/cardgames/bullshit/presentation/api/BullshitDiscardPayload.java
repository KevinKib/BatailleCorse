package org.kevinkib.cardgames.bullshit.presentation.api;

import org.kevinkib.cardgames.bullshit.presentation.dto.CardDto;

import java.util.List;

public record BullshitDiscardPayload(String gameId, String token, List<CardDto> cards) {
}
