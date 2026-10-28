package org.kevinkib.cardgames.ninetynine.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.Action;
import org.kevinkib.cardgames.ninetynine.domain.CardEffect;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNine;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNineFactory;
import org.kevinkib.cardgames.ninetynine.domain.Phase;
import org.kevinkib.cardgames.ninetynine.domain.bot.NinetyNineBotLevel;
import org.kevinkib.cardgames.ninetynine.domain.event.Busted;
import org.kevinkib.cardgames.ninetynine.domain.event.NinetyNineResult;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One player's view of a 99 game, the only projection of the aggregate. Public information is the same for every
 * viewer; {@code hand}, {@code playable} and {@code availableActions} are the viewer's own. The other hands and the
 * stock order are never in it. {@code stuck} is public: the seat on turn holds no safe card, so it must play a card
 * that goes over 99 and lose a life; the {@code lastBust.hand} is what it still held.
 */
public record NinetyNineDto(
        boolean started,
        String id,
        String gameType,
        int version,
        int round,
        String phase,
        int total,
        String direction,
        int currentSeat,
        CardDto pileTop,
        int pileCount,
        int stockSize,
        Map<String, String> options,
        List<PlayerDto> players,
        LastPlayDto lastPlay,
        LastBustDto lastBust,
        OutcomeDto outcome,
        boolean stuck,
        List<CardDto> hand,
        List<PlayableDto> playable,
        List<String> availableActions) {

    /** {@code bot} is true for a computer seat and omitted from the JSON otherwise; {@code level} only for a bot. */
    public record PlayerDto(int seat, String name, @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean bot,
                            @JsonInclude(JsonInclude.Include.NON_NULL) String level, int lives, int handSize,
                            boolean eliminated, Integer place) {
    }

    public record LastPlayDto(int seat, CardDto card, Integer aceValue, int totalBefore, int totalAfter,
                              boolean reversed) {
    }

    public record LastBustDto(int seat, int totalBefore, List<CardDto> hand, int livesLeft, boolean eliminated) {
    }

    /**
     * A card of the viewer's hand: whether it may be played now and the Ace values allowed (empty for other cards).
     * A stuck seat on turn may play any card ({@code safe} true: the play costs a life, see {@code stuck}).
     */
    public record PlayableDto(CardDto card, boolean safe, List<Integer> aceValues) {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = OutcomeDto.Ongoing.class, name = "ONGOING"),
            @JsonSubTypes.Type(value = OutcomeDto.Finished.class, name = "FINISHED")
    })
    public sealed interface OutcomeDto permits OutcomeDto.Ongoing, OutcomeDto.Finished {

        record Ongoing() implements OutcomeDto {
        }

        /** {@code winners}: every seat in first place (several in quick mode). */
        record Finished(List<Integer> winners, String reason) implements OutcomeDto {
        }
    }

    /** The view without any computer seat level: bots show the default level. */
    public static NinetyNineDto forViewer(NinetyNine game, PlayerId viewer, Map<PlayerId, String> names,
                                          Set<PlayerId> bots) {
        return forViewer(game, viewer, names, bots, BotSeatOptions.none());
    }

    public static NinetyNineDto forViewer(NinetyNine game, PlayerId viewer, Map<PlayerId, String> names,
                                          Set<PlayerId> bots, BotSeatOptions seatOptions) {
        synchronized (game) {
            Map<PlayerId, Integer> lives = game.lives();
            Map<PlayerId, Integer> handSizes = game.handSizes();
            Map<PlayerId, Integer> places = game.places();
            List<PlayerDto> players = game.getPlayerIds().stream().map(seat -> {
                boolean bot = bots.contains(seat);
                return new PlayerDto(seat.id(), names.get(seat), bot, bot ? levelOf(game, seat, seatOptions) : null,
                        lives.get(seat), handSizes.get(seat), lives.get(seat) == 0, places.get(seat));
            }).toList();
            List<Card> pile = game.pileSinceReshuffle();
            List<Card> hand = game.hand(viewer);
            return new NinetyNineDto(
                    true,
                    game.getId().uuid().toString(),
                    NinetyNineFactory.GAME_TYPE,
                    game.version(),
                    game.round(),
                    game.phase().name(),
                    game.total(),
                    game.direction().name(),
                    game.currentSeat().id(),
                    pile.isEmpty() ? null : CardDto.from(pile.get(pile.size() - 1)),
                    pile.size(),
                    game.stockSize(),
                    game.options().toGameOptions().values(),
                    players,
                    game.lastPlay().map(p -> new LastPlayDto(p.seat().id(), CardDto.from(p.card()), p.aceValue(),
                            p.totalBefore(), p.totalAfter(), p.reversed())).orElse(null),
                    game.lastBust().map(b -> bustOf(b)).orElse(null),
                    outcomeOf(game),
                    game.isStuck(),
                    hand.stream().map(CardDto::from).toList(),
                    game.phase() == Phase.PLAYING
                            ? hand.stream().map(card -> playableOf(game, viewer, card)).toList() : List.of(),
                    game.availableActions(viewer).stream().map(Action::name).toList());
        }
    }

    private static String levelOf(NinetyNine game, PlayerId seat, BotSeatOptions seatOptions) {
        return NinetyNineBotLevel.fromOption(
                seatOptions.seatOptions(game.getId(), seat).get(NinetyNineFactory.LEVEL_KEY)).key();
    }

    private static PlayableDto playableOf(NinetyNine game, PlayerId viewer, Card card) {
        boolean ace = card.getRank() == FrenchRank.ACE;
        if (game.isStuck() && viewer.equals(game.currentSeat())) {
            // stuck: every card may be played (each goes over 99 and costs a life); an Ace is sent as 1
            return new PlayableDto(CardDto.from(card), true, ace ? List.of(1) : List.of());
        }
        return new PlayableDto(CardDto.from(card), CardEffect.isSafe(game.total(), card),
                ace ? CardEffect.aceValues(game.total()) : List.of());
    }

    private static LastBustDto bustOf(Busted bust) {
        return new LastBustDto(bust.seat().id(), bust.totalBefore(), bust.hand().stream().map(CardDto::from).toList(),
                bust.livesLeft(), bust.livesLeft() == 0);
    }

    private static OutcomeDto outcomeOf(NinetyNine game) {
        NinetyNineResult result = game.result().orElse(null);
        if (result == null) {
            return new OutcomeDto.Ongoing();
        }
        List<Integer> winners = result.places().entrySet().stream().filter(e -> e.getValue() == 1)
                .map(e -> e.getKey().id()).sorted().toList();
        return new OutcomeDto.Finished(winners, result.reason().name());
    }
}
