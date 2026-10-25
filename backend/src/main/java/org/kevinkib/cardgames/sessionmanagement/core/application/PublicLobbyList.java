package org.kevinkib.cardgames.sessionmanagement.core.application;

import java.util.List;

/**
 * The public game list of one game type: at most the first entries in list order, the {@code total} of public
 * entries, {@code truncated} when more exist, and a {@code version} that grows so a client can drop an older list.
 */
public record PublicLobbyList(String gameType, List<PublicLobby> lobbies, int total, boolean truncated, long version) {
}
