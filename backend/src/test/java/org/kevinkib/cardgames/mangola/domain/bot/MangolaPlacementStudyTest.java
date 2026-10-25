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
