package org.kevinkib.cardgames.blackjack.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.kevinkib.cardgames.blackjack.domain.Action;
import org.kevinkib.cardgames.blackjack.domain.Blackjack;
import org.kevinkib.cardgames.blackjack.domain.BlackjackFactory;
import org.kevinkib.cardgames.blackjack.domain.BlackjackOptions;
import org.kevinkib.cardgames.blackjack.domain.HandValue;
import org.kevinkib.cardgames.blackjack.domain.LastHand;
import org.kevinkib.cardgames.blackjack.domain.Phase;
import org.kevinkib.cardgames.blackjack.domain.PlayerHand;
import org.kevinkib.cardgames.blackjack.domain.SeatState;
import org.kevinkib.cardgames.blackjack.domain.bot.BlackjackBotLevel;
import org.kevinkib.cardgames.blackjack.domain.event.BlackjackResult;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cards.domain.Card;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One player's view of a Blackjack game, the only projection of the aggregate. Public information is the same for
 * every viewer; {@code availableActions}, {@code betLimits} and {@code lastBet} are the viewer's own. Never in it: the
 * dealer's hole card before it is shown, another seat's bet during {@code BETTING} (the amount is null, only the fact
 * that it was placed is public) and the order of the shoe. Totals, soft flags and statuses come from the domain: the
 * client counts nothing.
 */
public record BlackjackDto(
        boolean started,
        String id,
        String gameType,
        int version,
        String phase,
        int handNumber,
        int handsTotal,
        boolean solo,
        int button,
        int currentSeat,
        int activeHandIndex,
        OptionsDto options,
        DealerDto dealer,
        List<PlayerDto> players,
        LastHandDto lastHand,
        OutcomeDto outcome,
        List<String> availableActions,
        BetLimitsDto betLimits,
        Integer lastBet) {

    public record OptionsDto(String hands, String maxBet, int startingStack, int minBet) {
    }

    public record BetLimitsDto(int min, int max) {
    }

    /** {@code cards} holds the hole card only once shown; {@code total} counts the shown cards. */
    public record DealerDto(CardDto upCard, List<CardDto> cards, boolean holeHidden, int total, boolean soft,
                            boolean blackjack, boolean bust) {
    }

    /** {@code net} is the gain of a settled hand, null until it is settled. */
    public record HandDto(List<CardDto> cards, int total, boolean soft, String status, boolean doubled,
                          boolean fromSplit, @JsonInclude(JsonInclude.Include.NON_NULL) Integer net) {
    }

    /** {@code bot} is omitted from the JSON when false, {@code level} when not a bot; {@code bet} is null when sealed. */
    public record PlayerDto(int seat, String name, @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean bot,
                            @JsonInclude(JsonInclude.Include.NON_NULL) String level, int stack, boolean betPlaced,
                            Integer bet, List<HandDto> hands, boolean eliminated, boolean forfeited, Integer place,
                            int standing) {
    }

    public record LastHandDto(int handNumber, LastDealerDto dealer, List<LastSeatDto> seats, List<Integer> bestSeats) {
    }

    public record LastDealerDto(List<CardDto> cards, int total, boolean bust, boolean blackjack) {
    }

    public record LastSeatDto(int seat, List<LastHandHandDto> hands, int net) {
    }

    public record LastHandHandDto(List<CardDto> cards, int total, String status, int net) {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = OutcomeDto.Ongoing.class, name = "ONGOING"),
            @JsonSubTypes.Type(value = OutcomeDto.Finished.class, name = "FINISHED")
    })
    public sealed interface OutcomeDto permits OutcomeDto.Ongoing, OutcomeDto.Finished {

        record Ongoing() implements OutcomeDto {
        }

        /** {@code places} and {@code stacks} by seat; no places in solo, where {@code net} is the final result. */
        record Finished(Map<String, Integer> places, Map<String, Integer> stacks, String reason,
                        @JsonInclude(JsonInclude.Include.NON_NULL) Integer net) implements OutcomeDto {
        }
    }

    /** The view without any computer seat level: bots show the default level. */
    public static BlackjackDto forViewer(Blackjack game, PlayerId viewer, Map<PlayerId, String> names,
                                         Set<PlayerId> bots) {
        return forViewer(game, viewer, names, bots, BotSeatOptions.none());
    }

    public static BlackjackDto forViewer(Blackjack game, PlayerId viewer, Map<PlayerId, String> names,
                                         Set<PlayerId> bots, BotSeatOptions seatOptions) {
        synchronized (game) {
            Phase phase = game.phase();
            LastHand last = game.lastHand();
            boolean lastIsThisHand = last != null && last.handNumber() == game.handNumber();
            List<PlayerDto> players = game.getPlayerIds().stream().map(seat -> {
                boolean bot = bots.contains(seat);
                return playerOf(game, seat, viewer, names.get(seat), bot,
                        bot ? levelOf(game, seat, seatOptions) : null, lastIsThisHand ? last : null);
            }).toList();
            List<Action> actions = game.availableActions(viewer);
            boolean betting = phase == Phase.BETTING && actions.contains(Action.BET);
            PlayerId current = game.currentSeat();
            BlackjackOptions options = game.options();
            return new BlackjackDto(
                    true,
                    game.getId().uuid().toString(),
                    BlackjackFactory.GAME_TYPE,
                    game.version(),
                    phase.name(),
                    game.handNumber(),
                    game.handsTotal(),
                    game.isSolo(),
                    game.button().id(),
                    current == null ? -1 : current.id(),
                    current == null ? 0 : game.seat(current).activeHandIndex(),
                    new OptionsDto(options.hands().key(), options.maxBet().key(), BlackjackOptions.STARTING_STACK,
                            BlackjackOptions.MIN_BET),
                    dealerOf(game),
                    players,
                    last == null ? null : lastHandOf(last),
                    outcomeOf(game),
                    actions.stream().map(Action::name).toList(),
                    betting ? new BetLimitsDto(BlackjackOptions.MIN_BET, game.maxBetFor(viewer)) : null,
                    game.lastBet(viewer).isPresent() ? game.lastBet(viewer).getAsInt() : null);
        }
    }

    private static String levelOf(Blackjack game, PlayerId seat, BotSeatOptions seatOptions) {
        return BlackjackBotLevel.fromOption(
                seatOptions.seatOptions(game.getId(), seat).get(BlackjackFactory.LEVEL_KEY)).key();
    }

    private static DealerDto dealerOf(Blackjack game) {
        Card up = game.dealerUpCard();
        List<Card> shown = game.dealerCards();
        HandValue value = HandValue.of(shown, false);
        return new DealerDto(CardDto.from(up), shown.stream().map(CardDto::from).toList(),
                up != null && !game.holeCardShown(), value.total(), value.soft(), value.blackjack(), value.bust());
    }

    private static PlayerDto playerOf(Blackjack game, PlayerId seat, PlayerId viewer, String name, boolean bot,
                                      String level, LastHand last) {
        SeatState state = game.seat(seat);
        boolean sealed = game.phase() == Phase.BETTING && !seat.equals(viewer);
        Integer bet = state.betPlaced() && !sealed ? state.bet() : null;
        List<LastHand.SettledHand> settled = last == null ? null : last.seats().get(seat);
        List<HandDto> hands = new java.util.ArrayList<>();
        for (int i = 0; i < state.hands().size(); i++) {
            PlayerHand hand = state.hands().get(i);
            HandValue value = hand.value();
            Integer net = settled != null && i < settled.size() ? settled.get(i).net() : null;
            hands.add(new HandDto(hand.cards().stream().map(CardDto::from).toList(), value.total(), value.soft(),
                    hand.status().name(), hand.doubled(), hand.fromSplit(), net));
        }
        return new PlayerDto(seat.id(), name, bot, level, state.stack(), state.betPlaced(), bet, hands,
                state.eliminated(), state.forfeited(), state.place(), game.standing(seat));
    }

    private static LastHandDto lastHandOf(LastHand last) {
        HandValue dealer = HandValue.of(last.dealerCards(), false);
        List<LastSeatDto> seats = last.seats().entrySet().stream().map(e -> new LastSeatDto(e.getKey().id(),
                e.getValue().stream().map(s -> {
                    HandValue v = s.hand().value();
                    return new LastHandHandDto(s.hand().cards().stream().map(CardDto::from).toList(), v.total(),
                            s.hand().status().name(), s.net());
                }).toList(), last.nets().get(e.getKey()))).toList();
        return new LastHandDto(last.handNumber(),
                new LastDealerDto(last.dealerCards().stream().map(CardDto::from).toList(), dealer.total(),
                        dealer.bust(), dealer.blackjack()),
                seats, last.bestSeats().stream().map(PlayerId::id).toList());
    }

    private static OutcomeDto outcomeOf(Blackjack game) {
        BlackjackResult result = game.result().orElse(null);
        if (result == null) {
            return new OutcomeDto.Ongoing();
        }
        Map<String, Integer> places = new LinkedHashMap<>();
        result.places().forEach((seat, place) -> places.put(String.valueOf(seat.id()), place));
        Map<String, Integer> stacks = new LinkedHashMap<>();
        result.stacks().forEach((seat, stack) -> stacks.put(String.valueOf(seat.id()), stack));
        Integer net = game.isSolo()
                ? result.stacks().values().iterator().next() - BlackjackOptions.STARTING_STACK : null;
        return new OutcomeDto.Finished(places, stacks, result.reason().name(), net);
    }
}
