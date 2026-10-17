package org.kevinkib.cardgames.president.domain.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PresidentBotLevelTest {

    static Stream<Arguments> tuningTable() {
        // level, random play, random pass, counted ranks, control ranks, short hand, plans the end game,
        // chance of taking a completion, keeps a completion that would be a forbidden finish
        return Stream.of(
                Arguments.of(PresidentBotLevel.EASY, 0.30, 0.20, 0, 2, 6, false, 0.5, false),
                Arguments.of(PresidentBotLevel.MEDIUM, 0.0, 0.0, 3, 2, 52, false, 1.0, false),
                Arguments.of(PresidentBotLevel.HARD, 0.0, 0.0, 13, 2, 52, true, 1.0, true));
    }

    @ParameterizedTest
    @MethodSource("tuningTable")
    void eachLevelHasItsTuning(PresidentBotLevel level, double randomPlay, double randomPass, int counted,
                               int control, int shortHand, boolean plans, double completes, boolean keeps) {
        PresidentBotTuning tuning = level.tuning();

        assertThat(tuning.randomPlayProbability(), is(randomPlay));
        assertThat(tuning.randomPassProbability(), is(randomPass));
        assertThat(tuning.countedRanks(), is(counted));
        assertThat(tuning.controlRanks(), is(control));
        assertThat(tuning.shortHand(), is(shortHand));
        assertThat(tuning.plansEndGame(), is(plans));
        assertThat(tuning.completionProbability(), is(completes));
        assertThat(tuning.keepsDangerousCompletion(), is(keeps));
    }

    @Test
    void theDefaultLevelIsMedium() {
        assertThat(PresidentBotLevel.DEFAULT, is(PresidentBotLevel.MEDIUM));
    }

    @ParameterizedTest
    @ValueSource(strings = {"easy", "medium", "hard"})
    void aLevelIsFoundByItsKey(String key) {
        assertThat(PresidentBotLevel.fromOption(key).key(), is(key));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "expert", "HARD"})
    void anUnknownKeyGivesTheDefault(String key) {
        assertThat(PresidentBotLevel.fromOption(key), is(PresidentBotLevel.DEFAULT));
    }

    @Test
    void anAbsentKeyGivesTheDefault() {
        assertThat(PresidentBotLevel.fromOption(null), is(PresidentBotLevel.DEFAULT));
    }

    @Test
    void aTuningRefusesAProbabilityOutsideZeroToOne() {
        assertThrows(IllegalArgumentException.class, () -> new PresidentBotTuning(1.5, 0, 0, 2, 3, false));
        assertThrows(IllegalArgumentException.class, () -> new PresidentBotTuning(0, -0.1, 0, 2, 3, false));
        assertThrows(IllegalArgumentException.class,
                () -> new PresidentBotTuning(0, 0, 0, 2, 3, false, 1.2, false));
    }

    @Test
    void aTuningRefusesCountsOutsideTheirRange() {
        assertThrows(IllegalArgumentException.class, () -> new PresidentBotTuning(0, 0, 14, 2, 3, false));
        assertThrows(IllegalArgumentException.class, () -> new PresidentBotTuning(0, 0, 0, -1, 3, false));
        assertThrows(IllegalArgumentException.class, () -> new PresidentBotTuning(0, 0, 0, 2, -1, false));
    }
}
