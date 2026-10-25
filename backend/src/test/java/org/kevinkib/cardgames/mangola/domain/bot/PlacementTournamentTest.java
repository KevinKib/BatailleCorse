package org.kevinkib.cardgames.mangola.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.Side;

import java.util.List;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

class PlacementTournamentTest {

    private static final MangolaRules OWNER = MangolaRules.OWNER;
    private static final PlacementTournament.MoveSearch EXPERT_30 =
            seed -> new StudyExpert(new Random(seed), 30, StudyExpert.Weights.PRODUCTION);

    private static List<PlacementTournament.Placement> three() {
        List<int[]> catalogue = MangolaLayouts.catalogue();
        return List.of(
                new PlacementTournament.Placement("c0", catalogue.get(0)),
                new PlacementTournament.Placement("c7", catalogue.get(7)),
                new PlacementTournament.Placement("r1", MangolaLayouts.random(new Random(1))));
    }

    @Test
    void aGameIsPlayedToItsEndBetweenTwoSearches() {
        // The expert's fixture: side A wins by force in three plies by sowing b1.
        PlacementTournament.Outcome outcome = PlacementTournament.play(
                BotFixtures.board("b1:2 c1:1 f1:2 g1:1 h1:2 d2:1 e2:1 g2:1 a3:1 b3:1 e3:2 a4:1 b4:1"),
                new StudyExpert(new Random(1), 100_000, StudyExpert.Weights.PRODUCTION),
                new StudyExpert(new Random(2), 100_000, StudyExpert.Weights.PRODUCTION), OWNER, 600);
        assertThat(outcome.winner(), is(Side.A));
        assertThat(outcome.plies(), is(3));
        assertThat(outcome.capped(), is(false));
    }

    @Test
    void everyOrderedPairIsPlayedTheRequestedNumberOfTimes() {
        PlacementTournament.Results results = PlacementTournament.run(three(),
                new PlacementTournament.Config(OWNER, 2, 7, 1, 600), EXPERT_30);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                PlacementTournament.PairResult pair = results.pair(i, j);
                assertThat(pair.winsA() + pair.draws() + pair.winsB() + pair.stuck(), is(2));
                assertThat(pair.capped(), lessThanOrEqualTo(pair.draws()));
            }
        }
    }

    @Test
    void theSeatSwappedMatrixIsZeroSumWithAnEvenDiagonal() {
        double[][] matrix = PlacementTournament.run(three(),
                new PlacementTournament.Config(OWNER, 1, 7, 1, 600), EXPERT_30).seatSwapped();
        for (int i = 0; i < 3; i++) {
            assertThat(matrix[i][i], closeTo(0.5, 1e-9));
            for (int j = 0; j < 3; j++) {
                assertThat(matrix[i][j] + matrix[j][i], closeTo(1.0, 1e-9));
            }
        }
    }

    @Test
    void theResultsDependOnTheSeedOnlyNotOnTheThreads() {
        PlacementTournament.Results alone = PlacementTournament.run(three(),
                new PlacementTournament.Config(OWNER, 2, 11, 1, 600), EXPERT_30);
        PlacementTournament.Results shared = PlacementTournament.run(three(),
                new PlacementTournament.Config(OWNER, 2, 11, 3, 600), EXPERT_30);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertThat(shared.pair(i, j), is(alone.pair(i, j)));
            }
        }
    }

    @Test
    void extendingAPoolGivesTheSameResultsAsPlayingTheWholePool() {
        PlacementTournament.Config config = new PlacementTournament.Config(OWNER, 1, 13, 2, 600);
        List<PlacementTournament.Placement> pool = three();
        PlacementTournament.Results known = PlacementTournament.run(pool.subList(0, 2), config, EXPERT_30);
        PlacementTournament.Results extended = PlacementTournament.extend(known, pool, config, EXPERT_30);
        PlacementTournament.Results whole = PlacementTournament.run(pool, config, EXPERT_30);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertThat(extended.pair(i, j), is(whole.pair(i, j)));
            }
        }
    }

    @Test
    void aDuelAlternatesTheStarterAndCountsEveryGame() {
        PlacementTournament.LayoutPolicy classic = (side, random) -> MangolaLayouts.catalogue().get(0).clone();
        PlacementTournament.LayoutPolicy anyRandom = (side, random) -> MangolaLayouts.random(random);
        PlacementTournament.Duel duel = PlacementTournament.duel(classic, anyRandom, EXPERT_30, 6, 3, 2, OWNER, 600);
        assertThat(duel.wins() + duel.draws() + duel.losses(), is(6));
        assertThat(duel.winsWhenStarting() <= duel.wins(), is(true));
        assertThat(PlacementTournament.duel(classic, anyRandom, EXPERT_30, 6, 3, 1, OWNER, 600), is(duel));
    }
}
