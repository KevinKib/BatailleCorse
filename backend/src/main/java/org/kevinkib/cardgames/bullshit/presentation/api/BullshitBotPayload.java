package org.kevinkib.cardgames.bullshit.presentation.api;

/** Host request to remove the bot sitting at {@code seat} (the host is identified by {@code token}). */
public record BullshitBotPayload(String gameId, String token, int seat) {
}
