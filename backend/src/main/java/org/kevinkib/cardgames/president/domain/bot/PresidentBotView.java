package org.kevinkib.cardgames.president.domain.bot;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.exchange.PendingReturn;
import org.kevinkib.cardgames.president.domain.exchange.Title;
import org.kevinkib.cardgames.president.domain.play.Play;
import org.kevinkib.cardgames.president.domain.round.Action;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cardgames.president.domain.trick.TrickView;
import org.kevinkib.cards.domain.Card;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a human sitting at one seat sees of a President game, in domain types: the seat's own hand, the other
 * hands as sizes only, the public trick, the titles, the exchange pairs (seats and counts) and, private to the
 * seat, the cards it received and gave. A bot strategy receives this view, never the {@link President}
 * aggregate, so it cannot read another hand. {@code completion} holds the cards that complete the four when the
 * seat holds them and the window is open, as in its DTO.
 */
public record PresidentBotView(
        PlayerId seat,
        int version,
        int round,
        Phase phase,
        PlayerId currentSeat,
        List<Action> actions,
        List<Card> hand,
        Map<PlayerId, Integer> handSizes,
        TrickView trick,
        List<Play> legalPlays,
        List<Card> completion,
        List<PlayerId> finishingOrder,
        Map<PlayerId, Title> titles,
        List<PendingReturn> pendingReturns,
        List<Card> received,
        List<Card> given,
        Set<PlayerId> readySeats,
        Map<PlayerId, Integer> totals,
        PresidentOptions options) {

    /** A view without a completion on offer, for the steps of the game that never have one. */
    public PresidentBotView(PlayerId seat, int version, int round, Phase phase, PlayerId currentSeat,
                            List<Action> actions, List<Card> hand, Map<PlayerId, Integer> handSizes, TrickView trick,
                            List<Play> legalPlays, List<PlayerId> finishingOrder, Map<PlayerId, Title> titles,
                            List<PendingReturn> pendingReturns, List<Card> received, List<Card> given,
                            Set<PlayerId> readySeats, Map<PlayerId, Integer> totals, PresidentOptions options) {
        this(seat, version, round, phase, currentSeat, actions, hand, handSizes, trick, legalPlays, List.of(),
                finishingOrder, titles, pendingReturns, received, given, readySeats, totals, options);
    }

    /** Built under the game's monitor, so the fields belong to one version. */
    public static PresidentBotView forSeat(President game, PlayerId seat) {
        synchronized (game) {
            Map<PlayerId, Integer> sizes = new LinkedHashMap<>();
            List<Integer> bySeat = game.handSizes();
            for (PlayerId player : game.getPlayerIds()) {
                sizes.put(player, bySeat.get(player.id()));
            }
            return new PresidentBotView(
                    seat,
                    game.version(),
                    game.round(),
                    game.phase(),
                    game.currentSeat(),
                    List.copyOf(game.availableActions(seat)),
                    game.hand(seat),
                    sizes,
                    game.trick(),
                    game.legalPlays(seat),
                    game.completion(seat),
                    game.finishingOrder(),
                    game.titles(),
                    game.pendingReturns(),
                    game.received(seat),
                    game.given(seat),
                    game.readySeats(),
                    game.standings().totals(),
                    game.options());
        }
    }

    public boolean can(Action action) {
        return actions.contains(action);
    }

    public boolean finished() {
        return phase == Phase.FINISHED;
    }
}
