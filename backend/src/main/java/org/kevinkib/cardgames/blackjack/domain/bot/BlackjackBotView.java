package org.kevinkib.cardgames.blackjack.domain.bot;

import org.kevinkib.cardgames.blackjack.domain.Action;
import org.kevinkib.cardgames.blackjack.domain.Blackjack;
import org.kevinkib.cardgames.blackjack.domain.BlackjackOptions;
import org.kevinkib.cardgames.blackjack.domain.HandValue;
import org.kevinkib.cardgames.blackjack.domain.Phase;
import org.kevinkib.cardgames.blackjack.domain.SeatState;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a human at one seat sees of a Blackjack game, in domain types and already counted: the seat's own hands
 * (totals, soft flags, pair flags), the dealer's up card value, the actions and bet limits on offer, every live
 * stack, the button and the hands left. A strategy receives this view, never the aggregate. The aggregate builds it
 * ({@code forSeat}, added with the rules) and a parity test pins it against the seat's DTO.
 *
 * @param seat            the bot's seat
 * @param version         the aggregate version the view was read at
 * @param handNumber      the hand in progress, from 1
 * @param handsLeft       hands left including the one in progress
 * @param phase           the phase of the game
 * @param activeHandIndex the hand being played (0 or 1), 0 while betting
 * @param hands           the seat's own hands, none while betting
 * @param dealerUpValue   the dealer's up card as a play value (2 to 10, 11 for an Ace), 0 while betting
 * @param availableActions what the seat may do now
 * @param minBet          the smallest bet allowed now (during betting)
 * @param maxBet          the largest bet allowed now: the option cap, capped by the stack (during betting)
 * @param stacks          the stack of every live seat, the seat's own included
 * @param button          the seat that plays first this hand
 * @param solo            whether the game has a single seat
 * @param options         the game options in force
 */
public record BlackjackBotView(
        PlayerId seat,
        int version,
        int handNumber,
        int handsLeft,
        Phase phase,
        int activeHandIndex,
        List<Hand> hands,
        int dealerUpValue,
        List<Action> availableActions,
        int minBet,
        int maxBet,
        Map<PlayerId, Integer> stacks,
        PlayerId button,
        boolean solo,
        BlackjackOptions options) {

    /** One of the seat's hands: its worth, whether it is a pair of equal values, its bet and whether it doubled. */
    public record Hand(int total, boolean soft, boolean pair, int bet, boolean doubled) {
    }

    public BlackjackBotView {
        hands = List.copyOf(hands);
        availableActions = List.copyOf(availableActions);
        stacks = Collections.unmodifiableMap(new LinkedHashMap<>(stacks));
    }

    /** What the seat sees of the game right now, read under the game's monitor so it is one consistent snapshot. */
    public static BlackjackBotView forSeat(Blackjack game, PlayerId seat) {
        synchronized (game) {
            Phase phase = game.phase();
            SeatState mine = game.seat(seat);
            List<Hand> hands = mine.hands().stream().map(h -> {
                HandValue value = h.value();
                boolean pair = h.cards().size() == 2
                        && HandValue.of(List.of(h.cards().get(0)), false).total()
                        == HandValue.of(List.of(h.cards().get(1)), false).total();
                return new Hand(value.total(), value.soft(), pair, h.bet(), h.doubled());
            }).toList();
            Card up = game.dealerUpCard();
            List<Action> actions = game.availableActions(seat);
            boolean betting = phase == Phase.BETTING && actions.contains(Action.BET);
            Map<PlayerId, Integer> stacks = new LinkedHashMap<>();
            for (PlayerId other : game.getPlayerIds()) {
                SeatState state = game.seat(other);
                if (!state.eliminated()) {
                    // Another seat's sealed bet stays hidden, as in the human view.
                    stacks.put(other, other.equals(seat) ? state.stack() : game.publicStack(other));
                }
            }
            PlayerId current = game.currentSeat();
            return new BlackjackBotView(seat, game.version(), game.handNumber(),
                    game.handsTotal() - game.handNumber() + 1, phase,
                    current == null ? 0 : game.seat(current).activeHandIndex(), hands,
                    up == null ? 0 : HandValue.of(List.of(up), false).total(), actions,
                    betting ? BlackjackOptions.MIN_BET : 0, betting ? game.maxBetFor(seat) : 0, stacks,
                    game.button(), game.isSolo(), game.options());
        }
    }
}
