package org.kevinkib.cardgames.kilodemerde.domain;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Test helper: everything observable about a game, to prove a refused command changed nothing. */
record KiloSnapshot(
        Phase phase, int version, int round, int roundsPlayed, int step, int raceId, int pileSize,
        List<PlayerId> activeSeats, List<PlayerId> slapOrder,
        Map<PlayerId, List<Card>> hands, Map<PlayerId, List<Card>> kiloCards,
        Map<PlayerId, Optional<Card>> chosen, Optional<RoundOutcome> lastOutcome,
        Optional<KiloDeMerdeResult> result) {

    static KiloSnapshot of(KiloDeMerde game) {
        Map<PlayerId, List<Card>> hands = new LinkedHashMap<>();
        Map<PlayerId, List<Card>> kilos = new LinkedHashMap<>();
        Map<PlayerId, Optional<Card>> chosen = new LinkedHashMap<>();
        for (PlayerId seat : game.getPlayerIds()) {
            hands.put(seat, game.hand(seat));
            kilos.put(seat, game.kiloCards(seat));
            chosen.put(seat, game.chosen(seat));
        }
        return new KiloSnapshot(game.phase(), game.version(), game.round(), game.roundsPlayed(), game.step(),
                game.raceId(), game.pileSize(), game.activeSeats(), game.slapOrder(), hands, kilos, chosen,
                game.lastOutcome(), game.result());
    }
}
