package org.kevinkib.cardgames.kilodemerde.domain.bot;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeOptions;
import org.kevinkib.cardgames.kilodemerde.domain.Phase;
import org.kevinkib.cards.domain.Card;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a human at one seat sees of a Kilo de merde game, in domain types, plus the seat's own pass history of the
 * round: its hand, the public kilos and the slap race. A strategy receives this view, never the aggregate.
 * {@link #forSeat} builds it from the aggregate; a parity test pins it against the seat's DTO.
 *
 * @param passHistory the seat's own given and received cards this round, oldest step first
 * @param slapOrder   the seats that slapped in the open race, first slapper first; empty outside a race
 */
public record KiloDeMerdeBotView(
        PlayerId seat,
        int version,
        int round,
        int step,
        Phase phase,
        List<Card> hand,
        List<Pass> passHistory,
        Map<PlayerId, Integer> kilos,
        List<PlayerId> activeSeats,
        List<PlayerId> slapOrder,
        int pileSize,
        KiloDeMerdeOptions options) {

    /** One resolved step as the seat lived it: the card it gave and the card it received. */
    public record Pass(int step, Card given, Card received) {
    }

    public KiloDeMerdeBotView {
        hand = List.copyOf(hand);
        passHistory = List.copyOf(passHistory);
        kilos = Map.copyOf(kilos);
        activeSeats = List.copyOf(activeSeats);
        slapOrder = List.copyOf(slapOrder);
    }

    /**
     * What the seat sees of the game right now, read under the game's monitor so it is one consistent snapshot: the
     * seat's own hand and pass history, the public kilos of every seat, the slap order and the size of the kilo pile.
     * A parity test pins it against the seat's DTO.
     */
    public static KiloDeMerdeBotView forSeat(KiloDeMerde game, PlayerId seat) {
        synchronized (game) {
            Map<PlayerId, Integer> kilos = new HashMap<>();
            game.getPlayerIds().forEach(player -> kilos.put(player, game.kilos(player)));
            return new KiloDeMerdeBotView(seat, game.version(), game.round(), game.step(), game.phase(),
                    game.hand(seat), game.passHistory(seat), kilos, game.activeSeats(), game.slapOrder(),
                    game.pileSize(), game.options());
        }
    }
}
