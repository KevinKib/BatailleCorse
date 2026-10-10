package org.kevinkib.cardgames.kobo.presentation.api;

/**
 * Matching discard. {@code giveSlot} is the actor card handed over when the target belongs to
 * another player; {@code expectedTopRank} the rank the client saw on top of the discard pile
 * (required: a moved pile rejects the attempt without penalty); {@code expectedRevision} the
 * revision of the target slot as the client knew it (optional, same protection for the slot).
 */
public record KoboMatchPayload(String gameId, String token, int seat, int slot, Integer giveSlot,
                               String expectedTopRank, Integer expectedRevision) {
}
