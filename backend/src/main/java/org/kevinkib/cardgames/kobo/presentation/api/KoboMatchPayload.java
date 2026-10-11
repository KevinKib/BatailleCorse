package org.kevinkib.cardgames.kobo.presentation.api;

/**
 * Matching discard. {@code giveSlot} is the actor card handed over when the target belongs to
 * another player; {@code expectedTopRank} the rank the client saw on top of the discard pile
 * (required: a moved pile rejects the attempt without penalty); {@code expectedRevision} the
 * revision of the target slot as the client knew it (required, same protection for the slot: a changed slot rejects the attempt without penalty).
 */
public record KoboMatchPayload(String gameId, String token, Integer seat, Integer slot, Integer giveSlot,
                               String expectedTopRank, Integer expectedRevision) {
}
