package org.kevinkib.cardgames.sessionmanagement.core.application;

import java.util.List;

/**
 * Outcome of the host removing a human seat: the token the removed player held (it no longer resolves, but their
 * client may still listen on its channel and must be told) and the remaining humans' views, computed in the same
 * critical section.
 */
public record SeatRemoval(String removedToken, List<AddressedLobbyView> views) {
}
