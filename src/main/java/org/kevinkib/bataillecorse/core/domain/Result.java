package org.kevinkib.bataillecorse.core.domain;

import org.kevinkib.bataillecorse.core.domain.slaprules.SlapRules;

import java.util.List;

public record Result(Player winningPlayer) {

    public static Result ONGOING = new Result(null);

    public static Result update(List<Player> players, CentralPile pile, SlapRules slapRules) {
        List<Player> playersWithCards = players.stream().filter(Player::hasAnyCards).toList();

        if (playersWithCards.isEmpty()) {
            throw new IllegalStateException("Cannot have no players with no cards when pile is empty");
        }

        if (playersWithCards.size() > 1) {
            return Result.ONGOING;
        }

        if (!pile.isEmpty() && slapRules.applies(pile)) {
            return Result.ONGOING;
        } else {
            Player winningPlayer = playersWithCards.get(0);
            return new Result(winningPlayer);
        }
    }

    public boolean isFinished() {
        return winningPlayer != null;
    }
}
