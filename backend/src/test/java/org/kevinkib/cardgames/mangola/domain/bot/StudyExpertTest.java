package org.kevinkib.cardgames.mangola.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.mangola.domain.MangolaBoard;
import org.kevinkib.cardgames.mangola.domain.MangolaLayouts;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.Side;

import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/** The study's copy of the expert must be the production expert when it is given the production weights. */
class StudyExpertTest {

    private static final MangolaRules OWNER = MangolaRules.OWNER;

    @Test
    void withTheProductionWeightsItSearchesExactlyLikeTheExpert() {
        int compared = 0;
        for (long seed = 1; compared < 40; seed++) {
            MangolaBoard board = MangolaLayouts.random(new Random(seed));
            Side side = seed % 2 == 0 ? Side.A : Side.B;
            if (MangolaMoves.moves(board, side, OWNER).isEmpty()) {
                continue;
            }
            long cap = 200 + seed * 37;
            ExpertMangolaBot.SearchResult expected = new ExpertMangolaBot(new Random(seed),
                    new ExpertMangolaBot.Budget(StudyExpert.UNLIMITED_MILLIS, cap))
                    .search(BotFixtures.position(board, side), side);
            ExpertMangolaBot.SearchResult actual = new StudyExpert(new Random(seed), cap, StudyExpert.Weights.PRODUCTION)
                    .search(board, side, OWNER);
            assertThat("seed " + seed, actual, is(expected));
            compared++;
        }
    }

    @Test
    void otherWeightsChangeTheEvaluation() {
        MangolaBoard board = MangolaLayouts.random(new Random(5));
        StudyExpert.Weights seedsOnly = new StudyExpert.Weights(10, 0, 0, 0);
        int production = new StudyExpert(new Random(1), 1, StudyExpert.Weights.PRODUCTION).evaluate(board, Side.A, OWNER);
        int other = new StudyExpert(new Random(1), 1, seedsOnly).evaluate(board, Side.A, OWNER);
        assertThat(other, is(not(production)));
    }
}
