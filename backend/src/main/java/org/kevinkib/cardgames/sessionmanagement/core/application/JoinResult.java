package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;

/** The seat a joiner took, and the lobby views (computed in the same critical section) to send to the room. */
public record JoinResult(PlayerId playerId, String token, List<AddressedLobbyView> views) {

    public JoinResult(PlayerId playerId, String token) {
        this(playerId, token, List.of());
    }
}
