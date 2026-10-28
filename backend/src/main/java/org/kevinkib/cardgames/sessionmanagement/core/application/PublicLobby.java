package org.kevinkib.cardgames.sessionmanagement.core.application;

import java.time.Instant;

/**
 * One entry of the public game list. {@code hostName} is the name seat 0 typed (null when none: the client labels it), {@code joined} counts the
 * taken seats (bots included). {@code openedAt} only orders the list: it is never published.
 */
public record PublicLobby(String gameId, String gameType, String hostName, int joined, int bots, int maxPlayers,
                          PublicLobbyState state, Instant openedAt) {
}
