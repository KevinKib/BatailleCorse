package org.kevinkib.cardgames.kilodemerde.domain;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

import java.util.List;
import java.util.Map;

/**
 * How a round ended, with every hand revealed as the proof (A17). {@code firstSlapper} is null when nobody slapped
 * ({@code STALLED}, {@code VOIDED}); {@code holders} are the seats whose hand was a four of a kind.
 */
public record RoundOutcome(
        Kind kind,
        PlayerId firstSlapper,
        List<PlayerId> slapOrder,
        List<PlayerId> holders,
        List<KiloDraw> draws,
        Map<PlayerId, List<Card>> hands) {

    public enum Kind { RACE, FALSE_SLAP, STALLED, VOIDED }

    public RoundOutcome {
        slapOrder = List.copyOf(slapOrder);
        holders = List.copyOf(holders);
        draws = List.copyOf(draws);
        hands = Map.copyOf(hands);
    }
}
