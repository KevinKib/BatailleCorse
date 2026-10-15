package org.kevinkib.cardgames.mangola.presentation.api;

/** The pit to sow, an index 0..31 ({@code row * 8 + column}). */
public record MangolaSowPayload(String gameId, String token, Integer pit) {
}
