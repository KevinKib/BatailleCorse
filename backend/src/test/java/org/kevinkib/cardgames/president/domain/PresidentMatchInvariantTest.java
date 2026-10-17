package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.rules.EqualPlay;
import org.kevinkib.cardgames.president.domain.rules.ExchangeAmount;
import org.kevinkib.cardgames.president.domain.rules.GiveBack;
import org.kevinkib.cardgames.president.domain.rules.MatchLength;
import org.kevinkib.cardgames.president.domain.rules.NextLead;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cardgames.president.domain.rules.Scoring;

import java.util.List;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

/**
 * Invariant over whole seeded matches: hands, trick and set-aside tricks are always the whole deck (52 cards)
 * after every command of every phase, nothing legal is refused, and the match always ends.
 */
class PresidentMatchInvariantTest {

    private static PresidentOptions optionsFor(long seed) {
        ExchangeAmount[] amounts = ExchangeAmount.values();
        Scoring[] scorings = Scoring.values();
        MatchLength[] lengths = {MatchLength.ROUNDS_3, MatchLength.ROUNDS_3, MatchLength.POINTS_11,
                MatchLength.ROUNDS_5};
        return PresidentOptions.defaults()
                .withExchange(amounts[(int) (seed % amounts.length)])
                .withGiveBack(seed % 3 == 0 ? GiveBack.LOWEST : GiveBack.FREE)
                .withNextLead(seed % 2 == 0 ? NextLead.SCUM : NextLead.PRESIDENT)
                .withEqualPlay(seed % 4 < 2 ? EqualPlay.ON : EqualPlay.OFF)
                .withScoring(scorings[(int) (seed % scorings.length)])
                .withLength(lengths[(int) (seed % lengths.length)]);
    }

    @ParameterizedTest(name = "{0} players")
    @ValueSource(ints = {3, 5, 8})
    void seededMatchesKeepTheWholeDeckAndEnd(int players) throws Exception {
        for (long seed = 0; seed < 12; seed++) {
            President game = new President(GameId.generate(), players, seed, new RandomDeckSource(),
                    optionsFor(seed));

            int steps = PresidentDriver.playMatch(game, new Random(seed * 17 + players), 52, s -> { });

            assertThat("seed " + seed, game.isFinished(), is(true));
            assertThat(steps, greaterThan(0));
            assertThat(game.result().winners().isEmpty(), is(false));
            assertThat(game.result().winners(), is(game.standings().leaders()));
        }
    }

    @ParameterizedTest(name = "{0} players")
    @ValueSource(ints = {3, 5, 8})
    void aForfeitAtAnyMomentKeepsTheDeckWholeAndTheMatchEnds(int players) throws Exception {
        for (long seed = 0; seed < 12; seed++) {
            President game = new President(GameId.generate(), players, seed, new RandomDeckSource(),
                    optionsFor(seed));
            Random random = new Random(seed * 31 + players);
            int forfeitAt = random.nextInt(120);

            for (int step = 0; step < 20000 && !game.isFinished(); step++) {
                if (step == forfeitAt || (step > forfeitAt && step % 150 == 0 && game.getPlayerIds().size() > 1)) {
                    List<PlayerId> active = game.getPlayerIds();
                    game.forfeit(active.get(random.nextInt(active.size())));
                } else {
                    PresidentDriver.step(game, random);
                }
                assertThat("seed " + seed + " step " + step, PresidentDriver.wholeDeck(game, 52), is(true));
            }

            assertThat("seed " + seed, game.isFinished(), is(true));
        }
    }
}
