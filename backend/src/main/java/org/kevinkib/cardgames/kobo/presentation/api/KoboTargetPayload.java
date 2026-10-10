package org.kevinkib.cardgames.kobo.presentation.api;

/** A card on the table: the owner seat and the slot in their tableau. */
public record KoboTargetPayload(String gameId, String token, int seat, int slot) {
}
