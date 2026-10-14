package org.kevinkib.cardgames.kobo.domain.bot;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KoboBotLevelTest {

    @Test
    void easyTuning() {
        KoboBotTuning easy = KoboBotLevel.EASY.tuning();

        assertThat(easy.reactionMinMs(), is(2500));
        assertThat(easy.reactionMaxMs(), is(4000));
        assertThat(easy.forgetProbability(), is(0.25));
        assertThat(easy.followsMoves(), is(false));
        assertThat(easy.matchesOpponents(), is(false));
        assertThat(easy.announceAt(), is(9));
        assertThat(easy.maxUnknownAtAnnounce(), is(2));
        assertThat(easy.comparesOpponents(), is(false));
    }

    @Test
    void mediumTuning() {
        KoboBotTuning medium = KoboBotLevel.MEDIUM.tuning();

        assertThat(medium.reactionMinMs(), is(1300));
        assertThat(medium.reactionMaxMs(), is(2500));
        assertThat(medium.forgetProbability(), is(0.08));
        assertThat(medium.followsMoves(), is(true));
        assertThat(medium.matchesOpponents(), is(true));
        assertThat(medium.announceAt(), is(6));
        assertThat(medium.maxUnknownAtAnnounce(), is(1));
        assertThat(medium.comparesOpponents(), is(true));
        assertThat(medium.announceMargin(), is(0));
    }

    @Test
    void hardTuning() {
        KoboBotTuning hard = KoboBotLevel.HARD.tuning();

        assertThat(hard.reactionMinMs(), is(700));
        assertThat(hard.reactionMaxMs(), is(1300));
        assertThat(hard.forgetProbability(), is(0.0));
        assertThat(hard.followsMoves(), is(true));
        assertThat(hard.matchesOpponents(), is(true));
        assertThat(hard.announceAt(), is(5));
        assertThat(hard.maxUnknownAtAnnounce(), is(1));
        assertThat(hard.comparesOpponents(), is(true));
        assertThat(hard.announceMargin(), is(2));
    }

    @Test
    void keysAndDefault() {
        assertThat(KoboBotLevel.EASY.key(), is("easy"));
        assertThat(KoboBotLevel.MEDIUM.key(), is("medium"));
        assertThat(KoboBotLevel.HARD.key(), is("hard"));
        assertThat(KoboBotLevel.DEFAULT, is(KoboBotLevel.MEDIUM));
    }

    @Test
    void fromOptionMapsKeysAndDefaultsOtherwise() {
        assertThat(KoboBotLevel.fromOption("easy"), is(KoboBotLevel.EASY));
        assertThat(KoboBotLevel.fromOption("hard"), is(KoboBotLevel.HARD));
        assertThat(KoboBotLevel.fromOption(null), is(KoboBotLevel.MEDIUM));
        assertThat(KoboBotLevel.fromOption("x"), is(KoboBotLevel.MEDIUM));
    }

    @Test
    void aTuningIsValidated() {
        assertThrows(IllegalArgumentException.class,
                () -> new KoboBotTuning(2000, 1000, 0, true, true, 5, 1, true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new KoboBotTuning(0, 10, 1.5, true, true, 5, 1, true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new KoboBotTuning(0, 10, 0, true, true, 5, -1, true, 0));
    }
}
