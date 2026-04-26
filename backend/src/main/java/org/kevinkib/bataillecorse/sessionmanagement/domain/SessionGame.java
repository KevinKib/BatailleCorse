package org.kevinkib.bataillecorse.sessionmanagement.domain;

import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.Player;
import org.kevinkib.bataillecorse.core.domain.PlayerId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record SessionGame(BatailleCorseId id, Map<PlayerId, SessionToken> tokensByPlayer) {

    public static SessionGame create(BatailleCorseId id, List<Player> players) {
        Map<PlayerId, SessionToken> tokensByPlayer = new HashMap<>();

        for (Player player : players) {
            tokensByPlayer.put(player.id(), SessionToken.generate());
        }

        return new SessionGame(id, tokensByPlayer);
    }

    public Optional<SessionToken> findTokenByPlayer(PlayerId playerId) {
        return Optional.ofNullable(tokensByPlayer.get(playerId));
    }

    public Optional<PlayerId> findPlayerByToken(SessionToken token) {
        return tokensByPlayer.entrySet().stream()
                .filter(e -> e.getValue().equals(token))
                .map(Map.Entry::getKey)
                .findFirst();
    }

}
