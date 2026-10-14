package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisVariant;
import org.kevinkib.cardgames.threemenmorris.domain.Move;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.instanceOf;

/**
 * The bots and the solver read the rules through {@link MorrisTransitions}, a second statement of the
 * rules of the aggregate: random games prove the two never disagree, in every variant.
 */
class MorrisTransitionsTest {

    @Test
    void theOpeningOffersEveryFreePointExceptPicariasCentre() {
        MorrisPosition tapatan = new ThreeMenMorris(GameId.generate()).position();
        MorrisPosition picaria = new ThreeMenMorris(GameId.generate(), MorrisVariant.PICARIA.rules(),
                new PlayerId(0)).position();

        assertThat(MorrisTransitions.legal(tapatan).size(), is(9));
        assertThat(MorrisTransitions.legal(picaria).size(), is(8));
        assertThat(MorrisTransitions.legal(picaria).contains(new MorrisDecision.Place(4)), is(false));
    }

    @Test
    void aLineEndsTheGameWithAWinForTheMover() {
        // White 0 and 1, black 3 and 4, white to place: 2 completes the top row.
        MorrisPosition position = new MorrisPosition(MorrisVariant.TAPATAN.rules(),
                java.util.Arrays.asList(0, 0, null, 1, 1, null, null, null, null), List.of(1, 1), 0);

        assertThat(MorrisTransitions.apply(position, new MorrisDecision.Place(2)),
                is(new MorrisTransitions.Outcome.Win(0)));
        assertThat(MorrisTransitions.apply(position, new MorrisDecision.Place(8)),
                instanceOf(MorrisTransitions.Outcome.Next.class));
    }

    @ParameterizedTest
    @EnumSource(MorrisVariant.class)
    void randomGamesAgreeWithTheAggregateStepByStep(MorrisVariant variant) throws Exception {
        Random random = new Random(42);
        for (int game = 0; game < 300; game++) {
            PlayerId whiteSeat = new PlayerId(game % 2);
            ThreeMenMorris aggregate = new ThreeMenMorris(GameId.generate(), variant.rules(), whiteSeat);
            int steps = 0;
            while (!aggregate.isFinished() && steps++ < 200) {
                MorrisPosition before = aggregate.position();
                PlayerId seat = aggregate.currentSeat();
                List<MorrisDecision> legal = MorrisTransitions.legal(before);

                assertThat(legal.size(), is(expectedLegal(aggregate, seat).size()));
                assertThat(new HashSet<>(legal), is(new HashSet<>(expectedLegal(aggregate, seat))));

                MorrisDecision decision = legal.get(random.nextInt(legal.size()));
                MorrisTransitions.Outcome outcome = MorrisTransitions.apply(before, decision);
                play(aggregate, seat, decision);

                if (aggregate.isFinished() && aggregate.result().winner() != null) {
                    assertThat(outcome, is(new MorrisTransitions.Outcome.Win(seat.id())));
                } else {
                    assertThat(outcome, is(new MorrisTransitions.Outcome.Next(aggregate.position())));
                }
            }
        }
    }

    private static Set<MorrisDecision> expectedLegal(ThreeMenMorris game, PlayerId seat) {
        Set<MorrisDecision> expected = new HashSet<>();
        game.legalPlacements(seat).forEach(point -> expected.add(new MorrisDecision.Place(point)));
        for (Move move : game.legalMoves(seat)) {
            expected.add(new MorrisDecision.Step(move.from(), move.to()));
        }
        return expected;
    }

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
    void onlyThePiecesNextToTheHoleCanMove() {
        // Achi, black to move, hemmed in: black 0, 1, 3 ... built to be blocked is hard on a connected
        // board, so use a full board but one hole next to no black piece.
        MorrisPosition position = new MorrisPosition(MorrisVariant.ACHI.rules(),
                java.util.Arrays.asList(0, 1, 0, 1, 0, 1, 0, 1, null), List.of(0, 0), 1);
        List<MorrisDecision> legal = MorrisTransitions.legal(position);

        List<MorrisDecision> expected = new ArrayList<>();
        // Point 8 is adjacent to 4, 5, 7 (centre, side middle 5, side middle 7): black owns 5 and 7.
        expected.add(new MorrisDecision.Step(5, 8));
        expected.add(new MorrisDecision.Step(7, 8));
        assertThat(new HashSet<>(legal), is(new HashSet<>(expected)));
    }
}
