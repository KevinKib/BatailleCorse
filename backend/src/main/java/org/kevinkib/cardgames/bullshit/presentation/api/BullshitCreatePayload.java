package org.kevinkib.cardgames.bullshit.presentation.api;

/** {@code claimMode} and {@code deckSize} ("32" or "52") are optional; absent means the defaults (rank, 32). */
public record BullshitCreatePayload(String name, String claimMode, String deckSize) {
}
