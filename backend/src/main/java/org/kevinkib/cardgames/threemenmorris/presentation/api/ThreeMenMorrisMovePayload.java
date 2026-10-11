package org.kevinkib.cardgames.threemenmorris.presentation.api;

public record ThreeMenMorrisMovePayload(String gameId, String token, Integer from, Integer to) {
}
