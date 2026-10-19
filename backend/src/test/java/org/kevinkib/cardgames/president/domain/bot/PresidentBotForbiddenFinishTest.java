package org.kevinkib.cardgames.president.domain.bot;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.kevinkib.cardgames.president.domain.bot.PresidentBotSelfPlayTest.Match;
import org.kevinkib.cardgames.president.domain.rules.PresidentException;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

/**
 * Seeded bot-only matches that count the rounds a seat ended by going out on a 2 (a forbidden finish: no place,
 * ranked last). Every level plays tables of its own level, 3 to 8 seats. Without compulsory play a bot can always
 * pass or lead its twos first, so it must never do it. Under compulsory play the pass is refused and the only legal
 * play can be the last non-2 set (leaving the twos), which is out of the bot's hands: the count only has to stay
 * marginal (the heuristic spends its twos early to keep it there).
 */
class PresidentBotForbiddenFinishTest {

    private static final int[] SEATS = {3, 4, 5, 6, 7, 8};
    private static final int MATCHES_PER_TABLE = 12;
    /** Before the fix: easy 652, medium 912, hard 449 in 7920 seat-rounds (all variants together). */
    private static final double COMPULSORY_PLAY_CEILING = 0.005;

    private static int[] tally(PresidentBotLevel level, PresidentOptions options, long variant)
            throws PresidentException {
        int forbidden = 0;
        int seatRounds = 0;
        for (int seats : SEATS) {
            for (int index = 0; index < MATCHES_PER_TABLE; index++) {
                long seed = 100_000L * variant + 1000L * seats + index;
                Match match = new Match(seats, seed, Collections.nCopies(seats, level), options);
                match.run();
                forbidden += match.forbiddenFinishes.getOrDefault(level, 0);
                seatRounds += match.seatRounds.getOrDefault(level, 0);
            }
        }
        return new int[] {forbidden, seatRounds};
    }

    @ParameterizedTest
    @EnumSource(PresidentBotLevel.class)
    void withoutCompulsoryPlayABotNeverGoesOutOnATwo(PresidentBotLevel level) throws PresidentException {
        PresidentOptions d = PresidentOptions.defaults();
        List<PresidentOptions> variants = List.of(d, d.withReducedCut(true));
        int forbidden = 0;
        long variant = 0;
        for (PresidentOptions options : variants) {
            forbidden += tally(level, options, ++variant)[0];
        }

        assertThat(level + " forbidden finishes", forbidden, is(0));
    }

    @ParameterizedTest
    @EnumSource(PresidentBotLevel.class)
    void underCompulsoryPlayForbiddenFinishesStayMarginal(PresidentBotLevel level) throws PresidentException {
        PresidentOptions d = PresidentOptions.defaults();
        int forbidden = 0;
        int seatRounds = 0;
        long variant = 2;
        for (PresidentOptions options : List.of(d.withMustPlay(true), d.withMustPlay(true).withReducedCut(true))) {
            int[] counts = tally(level, options, ++variant);
            forbidden += counts[0];
            seatRounds += counts[1];
        }

        assertThat(level + " forbidden finishes: " + forbidden + " in " + seatRounds + " seat-rounds",
                (double) forbidden, lessThanOrEqualTo(COMPULSORY_PLAY_CEILING * seatRounds));
    }
}
