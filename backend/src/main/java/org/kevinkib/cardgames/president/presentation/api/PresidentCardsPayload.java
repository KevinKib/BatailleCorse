package org.kevinkib.cardgames.president.presentation.api;

import org.kevinkib.cardgames.presentation.dto.CardDto;

import java.util.List;

/** Cards named by a seat, to play or to give back. */
public record PresidentCardsPayload(String gameId, String token, List<CardDto> cards) {
}
