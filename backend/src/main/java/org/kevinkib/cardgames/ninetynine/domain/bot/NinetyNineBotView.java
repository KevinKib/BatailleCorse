package org.kevinkib.cardgames.ninetynine.domain.bot;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.Direction;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNineOptions;
import org.kevinkib.cardgames.ninetynine.domain.Phase;
import org.kevinkib.cards.domain.Card;

import java.util.List;
import java.util.Map;

/**
 * What a human at one seat sees of a 99 game, in domain types: the seat's own hand, the other hands as sizes only,
 * the lives, and the public pile since the last reshuffle. A strategy receives this view, never the aggregate.
 * The factory {@code forSeat(game, seat)} and its DTO parity test arrive with the presentation chunk (C3).
 */
public record NinetyNineBotView(
        PlayerId seat,
        int version,
        int round,
        Phase phase,
        PlayerId currentSeat,
        int total,
        Direction direction,
        List<Card> hand,
        List<Card> pileSinceReshuffle,
        Map<PlayerId, Integer> handSizes,
        Map<PlayerId, Integer> lives,
        List<PlayerId> liveSeats,
        int stockSize,
        NinetyNineOptions options) {

    public NinetyNineBotView {
        hand = List.copyOf(hand);
        pileSinceReshuffle = List.copyOf(pileSinceReshuffle);
        handSizes = Map.copyOf(handSizes);
        lives = Map.copyOf(lives);
        liveSeats = List.copyOf(liveSeats);
    }
}
