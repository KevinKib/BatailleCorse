package org.kevinkib.cardgames.kilodemerde.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kilodemerde.domain.Action;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeFactory;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeResult;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDraw;
import org.kevinkib.cardgames.kilodemerde.domain.Phase;
import org.kevinkib.cardgames.kilodemerde.domain.RoundOutcome;
import org.kevinkib.cardgames.kilodemerde.domain.bot.KiloDeMerdeBotLevel;
import org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeDeadlines;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cards.domain.Card;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One seat's view of a Kilo de merde game, the only projection of the aggregate. Public information is the same for
 * every viewer: the kilo cards of every seat, who has chosen this step (a mark, not the card), the slap order, and,
 * once a round is over, every hand. {@code hand}, {@code myChoice}, {@code lastReceived}, {@code lastGiven} and
 * {@code availableActions} are the viewer's own. The other hands during a round, their choices, the cards they
 * received and the order of the kilo pile are never in it.
 *
 * <p>{@code passTimerEndsAt} and {@code raceEndsAt} are server epoch milliseconds for the countdown, null when the
 * timer is off or not running.
 */
public record KiloDeMerdeDto(
        boolean started,
        String id,
        String gameType,
        int version,
        int round,
        int roundsTotal,
        int step,
        String phase,
        int pileSize,
        Map<String, String> options,
        Long passTimerEndsAt,
        Long raceEndsAt,
        List<PlayerDto> players,
        RaceDto race,
        OutcomeDto lastOutcome,
        ResultDto result,
        List<CardDto> hand,
        CardDto myChoice,
        ReceivedDto lastReceived,
        GivenDto lastGiven,
        List<String> availableActions) {

    /**
     * {@code bot} is true for a computer seat and omitted from the JSON otherwise; {@code level} only for a bot.
     * {@code slapRank} is the place in the slap order (1 is the first slapper), null when the seat has not slapped.
     * {@code place} is the final place once the game is finished.
     */
    public record PlayerDto(int seat, String name, @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean bot,
                            @JsonInclude(JsonInclude.Include.NON_NULL) String level, int kilos,
                            List<CardDto> kiloCards, boolean chosen, Integer slapRank, boolean active,
                            Integer place) {
    }

    /** The race in progress, or the one that just ended: the first slapper and the slaps in order of arrival. */
    public record RaceDto(Integer firstSlapper, List<Integer> order) {
    }

    /** How the last round ended, with every hand revealed as the proof. */
    public record OutcomeDto(String kind, Integer firstSlapper, List<Integer> slapOrder, List<Integer> holders,
                             List<DrawDto> draws, List<HandDto> hands) {
    }

    /** One draw from the kilo pile; {@code card} is null when the pile was empty. */
    public record DrawDto(int seat, CardDto card, boolean flushed, int kilosAfter) {
    }

    public record HandDto(int seat, List<CardDto> cards) {
    }

    /** The end of the game: why, and the place and kilos of every seat (a place may be shared). */
    public record ResultDto(String reason, List<StandingDto> standings) {
    }

    public record StandingDto(int seat, int place, int kilos) {
    }

    /** The card the viewer received at the last resolved step, and the seat it came from. */
    public record ReceivedDto(CardDto card, int from) {
    }

    /** The card the viewer gave at the last resolved step, and the seat it went to. */
    public record GivenDto(CardDto card, int to) {
    }

    /** The view without any computer seat level or deadline: bots show the default level. */
    public static KiloDeMerdeDto forViewer(KiloDeMerde game, PlayerId viewer, Map<PlayerId, String> names,
                                           Set<PlayerId> bots) {
        return forViewer(game, viewer, names, bots, BotSeatOptions.none(), KiloDeMerdeDeadlines.off());
    }

    public static KiloDeMerdeDto forViewer(KiloDeMerde game, PlayerId viewer, Map<PlayerId, String> names,
                                           Set<PlayerId> bots, BotSeatOptions seatOptions,
                                           KiloDeMerdeDeadlines deadlines) {
        synchronized (game) {
            List<PlayerId> slapOrder = game.slapOrder();
            Map<PlayerId, Integer> finalPlaces = game.result().map(KiloDeMerdeResult::places).orElse(Map.of());
            List<PlayerDto> players = game.getPlayerIds().stream().map(seat -> {
                boolean bot = bots.contains(seat);
                int rank = slapOrder.indexOf(seat);
                return new PlayerDto(seat.id(), names.get(seat), bot, bot ? levelOf(game, seat, seatOptions) : null,
                        game.kilos(seat), game.kiloCards(seat).stream().map(CardDto::from).toList(),
                        game.hasChosen(seat), rank < 0 ? null : rank + 1, game.activeSeats().contains(seat),
                        finalPlaces.get(seat));
            }).toList();
            boolean passingOrRace = game.phase() == Phase.PASSING || game.phase() == Phase.RACE;
            boolean viewerPlays = game.activeSeats().contains(viewer);
            return new KiloDeMerdeDto(
                    true,
                    game.getId().uuid().toString(),
                    KiloDeMerdeFactory.GAME_TYPE,
                    game.version(),
                    game.round(),
                    game.roundsTotal(),
                    game.step(),
                    game.phase().name(),
                    game.pileSize(),
                    game.options().toGameOptions().values(),
                    deadlines.passEndsAt(game),
                    deadlines.raceEndsAt(game),
                    players,
                    slapOrder.isEmpty() ? null
                            : new RaceDto(slapOrder.get(0).id(), slapOrder.stream().map(PlayerId::id).toList()),
                    game.lastOutcome().map(KiloDeMerdeDto::outcomeOf).orElse(null),
                    game.result().map(KiloDeMerdeDto::resultOf).orElse(null),
                    game.hand(viewer).stream().map(CardDto::from).toList(),
                    game.chosen(viewer).map(CardDto::from).orElse(null),
                    passingOrRace && viewerPlays
                            ? game.lastReceived(viewer).map(c -> new ReceivedDto(CardDto.from(c),
                                    game.rightOf(viewer).id())).orElse(null)
                            : null,
                    passingOrRace && viewerPlays
                            ? game.lastGiven(viewer).map(c -> new GivenDto(CardDto.from(c),
                                    game.leftOf(viewer).id())).orElse(null)
                            : null,
                    game.availableActions(viewer).stream().map(Action::name).toList());
        }
    }

    private static String levelOf(KiloDeMerde game, PlayerId seat, BotSeatOptions seatOptions) {
        return KiloDeMerdeBotLevel.fromOption(
                seatOptions.seatOptions(game.getId(), seat).get(KiloDeMerdeFactory.LEVEL_KEY)).key();
    }

    /** The outcome of a round, public once it is over: every hand is revealed (A17). */
    public static OutcomeDto outcomeOf(RoundOutcome outcome) {
        return new OutcomeDto(
                outcome.kind().name(),
                outcome.firstSlapper() == null ? null : outcome.firstSlapper().id(),
                outcome.slapOrder().stream().map(PlayerId::id).toList(),
                outcome.holders().stream().map(PlayerId::id).toList(),
                outcome.draws().stream().map(KiloDeMerdeDto::drawOf).toList(),
                outcome.hands().entrySet().stream()
                        .sorted(Comparator.comparingInt(e -> e.getKey().id()))
                        .map(e -> new HandDto(e.getKey().id(), e.getValue().stream().map(CardDto::from).toList()))
                        .toList());
    }

    private static DrawDto drawOf(KiloDraw draw) {
        Card card = draw.card();
        return new DrawDto(draw.seat().id(), CardDto.from(card), draw.flushed(), draw.kilosAfter());
    }

    /** The final result, public: places and kilos by seat order. */
    public static ResultDto resultOf(KiloDeMerdeResult result) {
        List<StandingDto> standings = result.places().entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getKey().id()))
                .map(e -> new StandingDto(e.getKey().id(), e.getValue(), result.kilos().getOrDefault(e.getKey(), 0)))
                .toList();
        return new ResultDto(result.reason().name(), standings);
    }
}
