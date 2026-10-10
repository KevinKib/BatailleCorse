package org.kevinkib.cardgames.bullshit.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.claim.AscendingRankClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.CyclingSuitClaimMode;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Games between bots only must end: seeded deals and seeded randomness, no wall clock. A bot that
 * challenged every doubtful claim used to keep the pile moving forever in rank mode (it finished
 * about 1 game in 40 with two players), so the whole fixed seed range must now reach a winner.
 */
class BotEndgameSimulationTest {

    private static final int GAMES = 50;
    private static final int ACTION_CAP = 4_000;

    private static void assertEveryGameFinishes(ClaimMode mode, String name) {
        for (int seats = 2; seats <= 4; seats++) {
            for (long seed = 1; seed <= GAMES; seed++) {
                BotSimulation.Outcome outcome = BotSimulation.play(mode, seats, seed, ACTION_CAP);
                assertThat(name + " mode, " + seats + " players, seed " + seed
                        + " did not finish within " + ACTION_CAP + " actions", outcome.finished(), is(true));
            }
        }
    }

    @Test
    void givenBotsOnly_whenPlayedInRankMode_thenEveryGameReachesAWinner() {
        assertEveryGameFinishes(new AscendingRankClaimMode(), "rank");
    }

    @Test
    void givenBotsOnly_whenPlayedInSuitMode_thenEveryGameReachesAWinner() {
        assertEveryGameFinishes(new CyclingSuitClaimMode(), "suit");
    }

    @Test
    void givenTheSameSeed_thenTheSimulationIsReproducible() {
        BotSimulation.Outcome first = BotSimulation.play(new AscendingRankClaimMode(), 3, 7, ACTION_CAP);
        BotSimulation.Outcome second = BotSimulation.play(new AscendingRankClaimMode(), 3, 7, ACTION_CAP);

        assertThat(second, is(first));
    }
}
