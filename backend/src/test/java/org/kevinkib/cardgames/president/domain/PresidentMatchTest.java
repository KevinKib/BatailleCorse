package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.event.MatchFinished;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.event.PresidentResult;
import org.kevinkib.cardgames.president.domain.event.Ready;
import org.kevinkib.cardgames.president.domain.event.RoundDealt;
import org.kevinkib.cardgames.president.domain.event.RoundScored;
import org.kevinkib.cardgames.president.domain.exchange.Title;
import org.kevinkib.cardgames.president.domain.round.AlreadyReadyException;
import org.kevinkib.cardgames.president.domain.round.FinishedGameException;
import org.kevinkib.cardgames.president.domain.round.NotPlayersTurnException;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.president.domain.round.WrongPhaseException;
import org.kevinkib.cardgames.president.domain.rules.ExchangeAmount;
import org.kevinkib.cardgames.president.domain.rules.MatchLength;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cardgames.president.domain.rules.Scoring;
import org.kevinkib.cardgames.president.domain.scoring.RoundResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/** The match around the rounds: scoring, the ready barrier between rounds, the end of the match. */
class PresidentMatchTest {

    /** Round 1 ends 1, 3, 0, 2: President seat 1, Vice-President seat 3, Vice-Scum seat 0, Scum seat 2. */
    private static final int[] ORDER = {1, 3, 0, 2};

    private static PlayerId p(int seat) {
        return new PlayerId(seat);
    }

    private static President afterRoundOne(PresidentOptions options) throws Exception {
        return OrderedRounds.playedRoundOne(GameId.generate(), new RandomDeckSource(), options, ORDER);
    }

    private static void readyAll(President game) throws Exception {
        for (PlayerId seat : game.getPlayerIds()) {
            game.ready(seat);
        }
    }

    private static void drive(President game, Random random, Predicate<President> until) throws Exception {
        for (int step = 0; step < 5000 && !until.test(game); step++) {
            if (!PresidentDriver.step(game, random)) {
                return;
            }
        }
    }

    @Test
    void theRoundIsScoredWhenItEndsAndTheMatchGoesOn() throws Exception {
        President again = OrderedRounds.start(GameId.generate(), new RandomDeckSource(),
                PresidentOptions.defaults(), ORDER);
        List<PresidentEvent> events = null;
        while (again.phase() == Phase.PLAYING) {
            PlayerId current = again.currentSeat();
            int next = ORDER[again.finishingOrder().size()];
            events = current.id() == next
                    ? again.play(current, List.of(again.hand(current).get(0))) : again.pass(current);
        }

        RoundResult result = again.lastRoundResult();
        assertThat(events, hasItem(new RoundScored(result)));
        assertThat(again.phase(), is(Phase.ROUND_OVER));
        assertThat(again.isFinished(), is(false));
        assertThat(again.result(), is(PresidentResult.ONGOING));
        assertThat(result.round(), is(1));
        assertThat(result.finishingOrder(), contains(p(1), p(3), p(0), p(2)));
        assertThat(result.titles(), is(Map.of(p(1), Title.PRESIDENT, p(3), Title.VICE_PRESIDENT,
                p(0), Title.VICE_SCUM, p(2), Title.SCUM)));
        assertThat(result.points(), is(Map.of(p(1), 3, p(3), 2, p(0), 1, p(2), 0)));
        assertThat(result.totals(), is(result.points()));
        assertThat(again.standings().points(p(1)), is(3));
        assertThat(again.standings().leaders(), is(List.of(p(1))));
        assertThat(again.roundTitles(), is(result.titles()));
    }

    @Test
    void nothingIsScoredBeforeTheFirstRoundEnds() {
        President game = OrderedRounds.start(GameId.generate(), new RandomDeckSource(),
                PresidentOptions.defaults(), ORDER);

        assertThat(game.lastRoundResult(), is(nullValue()));
        assertThat(game.standings().points(p(0)), is(0));
        assertThat(game.result(), is(PresidentResult.ONGOING));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "RANK     | 3 2 1 0",
            "TITLES   | 2 1 0 0",
            "ZERO_SUM | 2 1 -1 -2",
    })
    void theScoringOptionDecidesThePointsOfTheRound(Scoring scoring, String expected) throws Exception {
        President game = afterRoundOne(PresidentOptions.defaults().withScoring(scoring));

        List<Integer> byPlace = game.lastRoundResult().finishingOrder().stream()
                .map(seat -> game.lastRoundResult().points().get(seat)).toList();
        assertThat(byPlace, is(java.util.Arrays.stream(expected.split(" ")).map(Integer::parseInt).toList()));
    }

    @Test
    void theBarrierWaitsForEveryPlayerThenDealsTheNextRound() throws Exception {
        President game = afterRoundOne(PresidentOptions.defaults().withExchange(ExchangeAmount.NONE));
        int version = game.version();

        List<PresidentEvent> first = game.ready(p(0));

        assertThat(first, contains(new Ready(p(0), false)));
        assertThat(game.version(), is(version + 1));
        assertThat(game.readySeats(), is(java.util.Set.of(p(0))));
        assertThat(game.phase(), is(Phase.ROUND_OVER));
        assertThat(game.round(), is(1));
        game.ready(p(1));
        game.ready(p(2));

        List<PresidentEvent> last = game.ready(p(3));

        assertThat(last.get(0), is(new Ready(p(3), true)));
        assertThat(last.stream().anyMatch(e -> e instanceof RoundDealt), is(true));
        assertThat(game.round(), is(2));
        assertThat(game.phase(), is(Phase.PLAYING));
        assertThat(game.readySeats().isEmpty(), is(true));
        assertThat(game.dealer(), is(p(2)));
        assertThat(game.handSizes(), is(List.of(13, 13, 13, 13)));
        assertThat(game.standings().points(p(1)), is(3));
    }

    @Test
    void readyTwiceOrFromAnUnknownSeatIsRefusedWithoutEffect() throws Exception {
        President game = afterRoundOne(PresidentOptions.defaults());
        game.ready(p(0));
        int version = game.version();

        assertThrows(AlreadyReadyException.class, () -> game.ready(p(0)));
        assertThrows(NotPlayersTurnException.class, () -> game.ready(p(9)));
        assertThrows(NotPlayersTurnException.class, () -> game.ready(null));

        assertThat(game.version(), is(version));
        assertThat(game.readySeats(), is(java.util.Set.of(p(0))));
    }

    @Test
    void readyOutsideTheBarrierAndPlayingInsideItAreRefused() throws Exception {
        President game = afterRoundOne(PresidentOptions.defaults());
        int version = game.version();

        assertThrows(WrongPhaseException.class, () -> game.play(p(2), cards("3C")));
        assertThrows(WrongPhaseException.class, () -> game.pass(p(2)));
        assertThat(game.version(), is(version));

        President playing = OrderedRounds.start(GameId.generate(), new RandomDeckSource(),
                PresidentOptions.defaults(), ORDER);
        assertThrows(WrongPhaseException.class, () -> playing.ready(p(0)));
    }

    @Test
    void pointsAddUpOverTheRoundsAndTitlesCarryToTheNextDeal() throws Exception {
        President game = afterRoundOne(PresidentOptions.defaults().withLength(MatchLength.ROUNDS_10));
        Map<PlayerId, Integer> firstTotals = new LinkedHashMap<>(game.lastRoundResult().totals());
        Map<PlayerId, Title> earned = game.roundTitles();
        readyAll(game);

        assertThat(game.titles(), is(earned));
        drive(game, new Random(3), g -> g.phase() == Phase.ROUND_OVER);

        RoundResult second = game.lastRoundResult();
        assertThat(second.round(), is(2));
        for (PlayerId seat : game.getPlayerIds()) {
            assertThat(second.totals().get(seat), is(firstTotals.get(seat) + second.points().get(seat)));
            assertThat(game.standings().points(seat), is(second.totals().get(seat)));
        }
        readyAll(game);
        assertThat(game.dealer(), is(second.finishingOrder().get(3)));
        assertThat(game.round(), is(3));
    }

    @ParameterizedTest(name = "{0} rounds")
    @CsvSource({"ROUNDS_3, 3", "ROUNDS_5, 5"})
    void aMatchByRoundsEndsAfterTheLastRoundWithTheLeadersAsWinners(MatchLength length, int rounds)
            throws Exception {
        President game = new President(GameId.generate(), 4, 11L, new RandomDeckSource(),
                PresidentOptions.defaults().withLength(length));

        drive(game, new Random(5), g -> g.phase() == Phase.FINISHED);

        assertThat(game.phase(), is(Phase.FINISHED));
        assertThat(game.isFinished(), is(true));
        assertThat(game.round(), is(rounds));
        assertThat(game.result().reason(), is(PresidentResult.Reason.POINTS));
        assertThat(game.result().winners(), is(game.standings().leaders()));
        assertThat(game.result().winners().isEmpty(), is(false));
        assertThat(game.lastRoundResult().round(), is(rounds));
    }

    @Test
    void aMatchByPointsEndsOnceAPlayerReachesTheTarget() throws Exception {
        President game = new President(GameId.generate(), 3, 21L, new RandomDeckSource(),
                PresidentOptions.defaults().withLength(MatchLength.POINTS_11));

        drive(game, new Random(8), g -> g.phase() == Phase.FINISHED);

        assertThat(game.isFinished(), is(true));
        assertThat(game.standings().points(game.standings().leaders().get(0)) >= 11, is(true));
        assertThat(game.result().winners(), is(game.standings().leaders()));
    }

    @Test
    void theEventsOfTheLastRoundCarryTheMatchResult() throws Exception {
        President game = OrderedRounds.start(GameId.generate(), new RandomDeckSource(),
                PresidentOptions.defaults().withLength(MatchLength.ROUNDS_3), ORDER);
        List<PresidentEvent> events = null;
        for (int round = 1; round <= 3; round++) {
            if (round > 1) {
                readyAll(game);
            }
            while (game.phase() != Phase.ROUND_OVER && game.phase() != Phase.FINISHED) {
                events = driveOne(game);
            }
        }

        assertThat(events.stream().anyMatch(e -> e instanceof MatchFinished), is(true));
        assertThat(events.stream().filter(e -> e instanceof MatchFinished).findFirst().get(),
                is(new MatchFinished(game.result())));
    }

    private static List<PresidentEvent> driveOne(President game) throws Exception {
        if (game.phase() == Phase.EXCHANGE) {
            var pending = game.pendingReturns().get(0);
            return game.giveBack(pending.high(), game.hand(pending.high()).subList(0, pending.count()));
        }
        PlayerId seat = game.currentSeat();
        boolean leading = game.trick().plays().isEmpty();
        var options = game.legalPlays(seat);
        return leading || !options.isEmpty() ? game.play(seat, options.get(0).cards()) : game.pass(seat);
    }

    @Test
    void onceTheMatchIsFinishedEveryCommandIsRefusedWithoutEffect() throws Exception {
        President game = new President(GameId.generate(), 3, 4L, new RandomDeckSource(),
                PresidentOptions.defaults().withLength(MatchLength.ROUNDS_3));
        drive(game, new Random(2), g -> g.phase() == Phase.FINISHED);
        int version = game.version();

        assertThrows(FinishedGameException.class, () -> game.play(p(0), cards("3C")));
        assertThrows(FinishedGameException.class, () -> game.pass(p(0)));
        assertThrows(FinishedGameException.class, () -> game.giveBack(p(0), cards("3C")));
        assertThrows(FinishedGameException.class, () -> game.ready(p(0)));

        assertThat(game.version(), is(version));
        assertThat(game.phase(), is(Phase.FINISHED));
    }
}
