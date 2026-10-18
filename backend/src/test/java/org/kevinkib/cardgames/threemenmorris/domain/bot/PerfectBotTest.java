package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisResult;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisVariant;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;

import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

class PerfectBotTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    private static void play(ThreeMenMorris game, PlayerId seat, MorrisDecision decision) throws Exception {
        if (decision instanceof MorrisDecision.Place place) {
            game.place(seat, place.point());
        } else if (decision instanceof MorrisDecision.Step step) {
            game.move(seat, step.from(), step.to());
        } else {
            throw new AssertionError(decision);
        }
    }

    @Test
    void withTheFirstGeneratorItsOpeningIsFixed() {
        MorrisBot bot = new PerfectBot(Positions.FIRST);

        // Tapatan and Achi: the centre is the only first move that does not leave White's win intact for
        // the opponent.
        assertThat(bot.decide(open(MorrisRules.TAPATAN), WHITE, false), is(new MorrisDecision.Place(4)));
        assertThat(bot.decide(open(MorrisRules.ACHI), WHITE, false), is(new MorrisDecision.Place(4)));
    }

    private static MorrisPosition open(MorrisRules rules) {
        return new ThreeMenMorris(GameId.generate(), rules, WHITE).position();
    }

    @ParameterizedTest
    @EnumSource(MorrisVariant.class)
    void whiteWinsEveryGameAgainstARandomBlackWithinTheSolvedDepth(MorrisVariant variant) throws Exception {
        MorrisBot perfect = new PerfectBot(new Random(3));
        MorrisBot random = new RandomBot(new Random(5));
        int depth = MorrisSolver.forRules(variant.rules()).solve(open(variant.rules())).depth();
        for (int game = 0; game < 200; game++) {
            ThreeMenMorris aggregate = new ThreeMenMorris(GameId.generate(), variant.rules(), WHITE);
            int plies = 0;
            while (!aggregate.isFinished()) {
                PlayerId seat = aggregate.currentSeat();
                MorrisBot bot = seat.equals(WHITE) ? perfect : random;
                play(aggregate, seat, bot.decide(aggregate.position(), seat, false));
                plies++;
            }
            assertThat(aggregate.result().winner(), is(WHITE));
            assertThat(plies, lessThanOrEqualTo(depth));
        }
    }

    @ParameterizedTest
    @EnumSource(MorrisVariant.class)
    void itNeverThrowsAwayTheValueOfAPositionWhoeverPlaysTheOtherSide(MorrisVariant variant) throws Exception {
        MorrisSolver solver = MorrisSolver.forRules(variant.rules());
        MorrisBot perfect = new PerfectBot(new Random(11));
        MorrisBot random = new RandomBot(new Random(13));
        for (int game = 0; game < 200; game++) {
            PlayerId perfectSeat = new PlayerId(game % 2);
            ThreeMenMorris aggregate = new ThreeMenMorris(GameId.generate(), variant.rules(), WHITE);
            while (!aggregate.isFinished()) {
                PlayerId seat = aggregate.currentSeat();
                MorrisPosition position = aggregate.position();
                if (seat.equals(perfectSeat)) {
                    MorrisSolver.Solution before = solver.solve(position);
                    MorrisDecision decision = perfect.decide(position, seat, false);
                    MorrisTransitions.Outcome outcome = MorrisTransitions.apply(position, decision);
                    switch (before.outcome()) {
                        case WIN -> {
                            if (outcome instanceof MorrisTransitions.Outcome.Next next) {
                                assertThat(solver.solve(next.position()),
                                        is(new MorrisSolver.Solution(MorrisSolver.Outcome.LOSS, before.depth() - 1)));
                            } else {
                                assertThat(before.depth(), is(1));
                            }
                        }
                        case DRAW -> assertThat(solver.solve(((MorrisTransitions.Outcome.Next) outcome).position())
                                .outcome(), is(MorrisSolver.Outcome.DRAW));
                        case LOSS -> assertThat(solver.solve(((MorrisTransitions.Outcome.Next) outcome).position()),
                                is(new MorrisSolver.Solution(MorrisSolver.Outcome.WIN, before.depth() - 1)));
                    }
                    play(aggregate, seat, decision);
                } else {
                    play(aggregate, seat, random.decide(position, seat, false));
                }
            }
        }
    }

    @Test
    void aSolvedDrawIsNeverLostAgainstAnyone() throws Exception {
        // Tapatan after a corner opening is a draw for Black: perfect Black never loses to a random White
        // that opens in the corner.
        MorrisBot perfect = new PerfectBot(new Random(17));
        MorrisBot random = new RandomBot(new Random(19));
        for (int game = 0; game < 300; game++) {
            ThreeMenMorris aggregate = new ThreeMenMorris(GameId.generate(), MorrisRules.TAPATAN, WHITE);
            aggregate.place(WHITE, 0);
            while (!aggregate.isFinished()) {
                PlayerId seat = aggregate.currentSeat();
                MorrisBot bot = seat.equals(BLACK) ? perfect : random;
                play(aggregate, seat, bot.decide(aggregate.position(), seat, false));
            }
            MorrisResult result = aggregate.result();
            assertThat("perfect Black does not lose", BLACK.equals(result.winner()) || result.winner() == null, is(true));
        }
    }

    @Test
    void itAnswersADrawOfferWithTheSolverRule() {
        MorrisPosition winning = Positions.tapatan("WW.BBW..B", 0, 0, 0);
        MorrisBot bot = new PerfectBot(Positions.FIRST);

        assertThat(bot.decide(winning, WHITE, true), is(new MorrisDecision.DeclineDraw()));
        assertThat(bot.decide(winning, BLACK, true), is(new MorrisDecision.AcceptDraw()));
    }

    @Test
    void aPositionTheSolverDoesNotKnowIsStillPlayedLegally() {
        // Black to move although White has a line already on the board: unreachable, never solved.
        MorrisPosition odd = Positions.tapatan("WWWBB....", 0, 0, 1);
        MorrisBot bot = new PerfectBot(Positions.FIRST);

        assertThat(MorrisTransitions.legal(odd).contains(bot.decide(odd, BLACK, false)), is(true));
    }
}
