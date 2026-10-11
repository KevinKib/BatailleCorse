package org.kevinkib.cardgames.sessionmanagement.core.application;

import java.util.List;

/**
 * Outcome of leaving a lobby: either the lobby is gone (no human left, nobody to notify) or the remaining humans'
 * views, computed in the same critical section.
 */
public record LeaveResult(boolean lobbyRemoved, List<AddressedLobbyView> views) {
}
