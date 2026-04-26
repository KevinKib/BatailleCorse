package org.kevinkib.bataillecorse.sessionmanagement.domain;

import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.Player;
import org.kevinkib.bataillecorse.core.domain.PlayerId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record SessionGame(BatailleCorseId id, Map<SessionToken, PlayerId> playersByToken) {

    public static SessionGame create(BatailleCorseId id, List<Player> players) {
        Map<SessionToken, PlayerId> playersByToken = new HashMap<>();

        for (Player player : players) {
            playersByToken.put(SessionToken.generate(), player.id());
        }

        return new SessionGame(id, playersByToken);
    }

    public Optional<PlayerId> findPlayerByToken(SessionToken token) {
        return Optional.ofNullable(playersByToken.get(token));
    }

}
