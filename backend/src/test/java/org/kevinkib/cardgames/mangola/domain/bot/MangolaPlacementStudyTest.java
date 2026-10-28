package org.kevinkib.cardgames.mangola.domain.bot;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;

/** The study's own helpers (the study itself only runs on demand). */
class MangolaPlacementStudyTest {

    @Test
    void spearmanIsOneForTheSameOrderMinusOneForTheReverseAndHandlesTies() {
        assertThat(MangolaPlacementStudy.spearman(new double[]{1, 2, 3, 4}, new double[]{10, 20, 30, 40}), closeTo(1, 1e-9));
        assertThat(MangolaPlacementStudy.spearman(new double[]{1, 2, 3, 4}, new double[]{4, 3, 2, 1}), closeTo(-1, 1e-9));
        // Ranks with ties: x = (1, 2.5, 2.5, 4) against y = (1, 2, 3, 4) gives 0.9487 (Pearson on the ranks).
        assertThat(MangolaPlacementStudy.spearman(new double[]{1, 2, 2, 3}, new double[]{1, 2, 3, 4}), closeTo(0.9487, 1e-4));
    }

    @Test
    void aLayoutIsShownInnerRowThenOuterRowFromColumnAToColumnH() {
        // The catalogue's first layout is the classic checkerboard: loop index 0 (a, inner) holds 4.
        assertThat(MangolaPlacementStudy.show(MangolaLayouts.catalogue().get(0)), is("inner 40404040 / outer 04040404"));
    }

    @Test
    void theOracleStopsAfterTheFirstRoundThatAddsNothing() {
        List<Integer> asked = new java.util.ArrayList<>();
        int played = OracleRounds.run(3, 8, round -> {
            asked.add(round);
            return false; // a stub oracle that finds nothing
        });
        assertThat(played, is(1));
        assertThat(asked, is(List.of(3)));
    }

    @Test
    void theOracleKeepsGoingWhileRoundsAddAndStopsAtTheLimit() {
        assertThat(OracleRounds.run(3, 5, round -> true), is(3));
        assertThat(OracleRounds.run(3, 8, round -> round < 5), is(3));
        assertThat(OracleRounds.run(6, 5, round -> true), is(0));
    }

    @Test
    void theLastPlayedRoundIsReadFromTheOracleLayoutNames() {
        int[] layout = MangolaLayouts.catalogue().get(0);
        List<PlacementTournament.Placement> pool = List.of(new PlacementTournament.Placement("C00", layout),
                new PlacementTournament.Placement("O1A1", layout), new PlacementTournament.Placement("O12B0", layout));
        assertThat(MangolaPlacementStudy.lastRound(pool), is(12));
        assertThat(MangolaPlacementStudy.lastRound(pool.subList(0, 1)), is(0));
    }

    @Test
    void thePoolHoldsTheCatalogueFifteenRandomAndTheExpertsLayoutsWithoutDuplicates() {
        List<PlacementTournament.Placement> pool = MangolaPlacementStudy.basePool();
        Set<List<Integer>> distinct = new HashSet<>();
        pool.forEach(p -> distinct.add(MangolaLayouts.boxed(p.layout())));
        assertThat(distinct.size(), is(pool.size()));
        assertThat(pool.stream().filter(p -> p.name().startsWith("C")).count(), is((long) MangolaLayouts.catalogue().size()));
        assertThat(pool.stream().filter(p -> p.name().startsWith("R")).count(), is(15L));
        assertThat(pool.stream().allMatch(p -> BotFixtures.isLegalLayout(p.layout())), is(true));
    }
}
