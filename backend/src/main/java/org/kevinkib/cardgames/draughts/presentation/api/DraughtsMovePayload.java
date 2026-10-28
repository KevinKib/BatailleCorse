package org.kevinkib.cardgames.draughts.presentation.api;

import java.util.List;

/** {@code captures} is the set of squares the move takes; a missing list stands for none. */
public record DraughtsMovePayload(String gameId, String token, Integer from, Integer to, List<Integer> captures) {
}
