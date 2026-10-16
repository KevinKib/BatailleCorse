package org.kevinkib.cardgames.president.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.PresidentFactory;
import org.kevinkib.cardgames.president.domain.event.PresidentResult;
import org.kevinkib.cardgames.president.domain.exchange.PendingReturn;
import org.kevinkib.cardgames.president.domain.exchange.Title;
import org.kevinkib.cardgames.president.domain.play.Constraint;
import org.kevinkib.cardgames.president.domain.play.Play;
import org.kevinkib.cardgames.president.domain.round.Action;
import org.kevinkib.cardgames.president.domain.scoring.RoundResult;
import org.kevinkib.cardgames.president.domain.trick.Chain;
import org.kevinkib.cardgames.president.domain.trick.TrickPlay;
import org.kevinkib.cardgames.president.domain.trick.TrickView;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cards.domain.Card;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One player's view of a President game, the only projection of the aggregate. Public information is the same
 * for every viewer; the hand, the cards of the exchange and the legal plays are filled for the viewer alone.
 * {@code rounds} is the target of a rounds match and null for a points or endless match.
 */
public record PresidentDto(
        boolean started,
        String id,
        String gameType,
        int version,
        int round,
        Integer rounds,
        String phase,
        int currentSeat,
        Map<String, String> options,
        List<PlayerDto> players,
        TrickDto trick,
        List<PairDto> exchange,
        RoundDto lastRound,
        List<StandingDto> standings,
        OutcomeDto outcome,
        List<CardDto> hand,
        List<CardDto> received,
        List<CardDto> given,
        int toGiveBack,
        List<PatternDto> legalPlays,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<CardDto> completion,
        List<String> availableActions) {

    /** {@code bot} is true for a computer seat and omitted from the JSON otherwise. */
    public record PlayerDto(int seat, String name, @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean bot,
                            int handSize, String title, Integer place, boolean out, boolean passed, int points,
                            boolean ready) {
    }

    /** A set of {@code count} cards of one rank. */
    public record PatternDto(String kind, String rank, int count) {
    }

    public record TrickPlayDto(int seat, List<CardDto> cards) {
    }

    public record TrickDto(Integer leader, List<TrickPlayDto> plays, PatternDto top, Integer lastPlayer,
                           List<Integer> passed, String constraint, String requiredRank, Integer requiredCount,
                           ChainDto chain) {
    }

    /**
     * The plays of one rank at the top of the trick, in succession: public, since every card is on the table. With
     * two plays or more the holder of the missing cards may complete the four (that holder alone gets
     * {@code completion}).
     */
    public record ChainDto(String rank, List<CardDto> cards, int plays) {
    }

    /** A title pair of the exchange: seats and a count only, so every player may see it. */
    public record PairDto(int high, int low, int count, boolean done) {
    }

    public record PlayerRoundDto(int seat, int place, String title, int points, int total) {
    }

    public record RoundDto(int round, List<Integer> finishingOrder, List<PlayerRoundDto> players) {
    }

    public record StandingDto(int seat, int points) {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = OutcomeDto.Ongoing.class, name = "ONGOING"),
            @JsonSubTypes.Type(value = OutcomeDto.Finished.class, name = "FINISHED")
    })
    public sealed interface OutcomeDto permits OutcomeDto.Ongoing, OutcomeDto.Finished {

        record Ongoing() implements OutcomeDto {
        }

        record Finished(List<Integer> winners, String reason) implements OutcomeDto {
        }

        static OutcomeDto from(PresidentResult result) {
            if (!result.isFinished()) {
                return new Ongoing();
            }
            return new Finished(result.winners().stream().map(PlayerId::id).toList(), result.reason().name());
        }
    }

    public static PresidentDto forViewer(President game, PlayerId viewer, Map<PlayerId, String> names,
                                         Set<PlayerId> bots) {
        synchronized (game) {
            List<PlayerId> finishing = game.finishingOrder();
            Map<PlayerId, Title> titles = game.titles();
            TrickView trick = game.trick();
            Set<PlayerId> ready = game.readySeats();
            List<PlayerDto> players = game.getPlayerIds().stream().map(p -> {
                int index = finishing.indexOf(p);
                Title title = titles.get(p);
                return new PlayerDto(p.id(), names.get(p), bots.contains(p), game.hand(p).size(),
                        title == null ? null : title.name(), index < 0 ? null : index + 1, index >= 0,
                        trick.passed().contains(p), game.standings().points(p), ready.contains(p));
            }).toList();
            boolean toAct = viewer.equals(game.currentSeat());
            int rounds = game.options().length().rounds();
            return new PresidentDto(
                    true,
                    game.getId().uuid().toString(),
                    PresidentFactory.GAME_TYPE,
                    game.version(),
                    game.round(),
                    rounds > 0 ? rounds : null,
                    game.phase().name(),
                    game.currentSeat().id(),
                    game.options().toGameOptions().values(),
                    players,
                    trickOf(trick),
                    game.exchange().stream()
                            .map(p -> new PairDto(p.high().id(), p.low().id(), p.count(), p.done())).toList(),
                    game.lastRoundResult() == null ? null : roundOf(game.lastRoundResult()),
                    game.standings().ranked().stream()
                            .map(p -> new StandingDto(p.id(), game.standings().points(p))).toList(),
                    OutcomeDto.from(game.result()),
                    cards(game.hand(viewer)),
                    cards(game.received(viewer)),
                    cards(game.given(viewer)),
                    game.pendingReturns().stream().filter(p -> p.high().equals(viewer))
                            .mapToInt(PendingReturn::count).sum(),
                    toAct ? game.legalPlays(viewer).stream().map(PresidentDto::pattern).toList() : List.of(),
                    completionOf(game, viewer),
                    game.availableActions(viewer).stream().map(Action::name).toList());
        }
    }

    private static List<CardDto> cards(List<Card> cards) {
        return cards.stream().map(CardDto::from).toList();
    }

    private static PatternDto pattern(Play play) {
        return new PatternDto("SET", play.rank().name(), play.count());
    }

    /** The cards that complete the four, for the holder alone while the window is open; null for everybody else. */
    private static List<CardDto> completionOf(President game, PlayerId viewer) {
        List<Card> completion = game.completion(viewer);
        return completion.isEmpty() ? null : cards(completion);
    }

    private static TrickDto trickOf(TrickView trick) {
        Play top = trick.top();
        boolean bound = trick.constraint() == Constraint.MATCH_OR_PASS;
        Chain chain = trick.chain();
        return new TrickDto(
                trick.leader().id(),
                trick.plays().stream().map(PresidentDto::playOf).toList(),
                top == null ? null : pattern(top),
                trick.lastPlayer() == null ? null : trick.lastPlayer().id(),
                trick.passed().stream().map(PlayerId::id).toList(),
                trick.constraint().name(),
                bound ? top.rank().name() : null,
                bound ? top.count() : null,
                chain == null ? null : new ChainDto(chain.rank().name(), cards(chain.cards()), chain.plays()));
    }

    private static TrickPlayDto playOf(TrickPlay play) {
        return new TrickPlayDto(play.seat().id(), cards(play.play().cards()));
    }

    private static RoundDto roundOf(RoundResult result) {
        List<PlayerRoundDto> players = new java.util.ArrayList<>();
        for (int i = 0; i < result.finishingOrder().size(); i++) {
            PlayerId p = result.finishingOrder().get(i);
            Title title = result.titles().get(p);
            players.add(new PlayerRoundDto(p.id(), i + 1, title == null ? null : title.name(),
                    result.points().getOrDefault(p, 0), result.totals().getOrDefault(p, 0)));
        }
        return new RoundDto(result.round(), result.finishingOrder().stream().map(PlayerId::id).toList(), players);
    }
}
