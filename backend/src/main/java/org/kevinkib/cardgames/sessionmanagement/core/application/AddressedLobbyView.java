package org.kevinkib.cardgames.sessionmanagement.core.application;

/**
 * A lobby view with the token of the seat it is for, computed under the lobby lock. The sender addresses
 * the message by this token, so a seat that changes number between the computation and the send still
 * reaches its occupant, and nobody else.
 */
public record AddressedLobbyView(String token, LobbyView view) {
}
