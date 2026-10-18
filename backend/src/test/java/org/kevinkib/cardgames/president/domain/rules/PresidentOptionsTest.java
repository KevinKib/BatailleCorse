package org.kevinkib.cardgames.president.domain.rules;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PresidentOptionsTest {

    @Test
    void theRoundKeysDefaultToTheOwnersRules() {
        PresidentOptions options = PresidentOptions.defaults();

        assertThat(options.afterPass(), is(AfterPass.REENTER));
        assertThat(options.mustPlay(), is(false));
        assertThat(options.reducedCut(), is(false));
    }

    @Test
    void theKeysOfTheVariantPrsNotShippedYetExistAndBehaveAsOff() {
        PresidentOptions options = PresidentOptions.defaults();

        assertThat(options.revolution(), is(Revolution.OFF));
        assertThat(options.ranking(), is(Ranking.TWO_HIGH));
        assertThat(options.runs(), is(Runs.OFF));
        assertThat(options.suitLock(), is(false));
    }

    @Test
    void theMatchKeysKeepTheirSpecDefaults() {
        PresidentOptions options = PresidentOptions.defaults();

        assertThat(options.exchange(), is(ExchangeAmount.TWO));
        assertThat(options.giveBack(), is(GiveBack.FREE));
        assertThat(options.nextLead(), is(NextLead.SCUM));
        assertThat(options.scoring(), is(Scoring.RANK));
        assertThat(options.length(), is(MatchLength.ROUNDS_5));
    }

    @Test
    void withersReplaceOneKeyAndKeepTheOthers() {
        PresidentOptions options = PresidentOptions.defaults().withAfterPass(AfterPass.OUT).withMustPlay(true);

        assertThat(options.afterPass(), is(AfterPass.OUT));
        assertThat(options.mustPlay(), is(true));
        assertThat(options.exchange(), is(ExchangeAmount.TWO));
    }

    /** The owner dropped jokers, two decks, eight stops, the skip, the trick revolution, any-suit runs. */
    @Test
    void theDroppedValuesAreGone() {
        assertThat(names(Revolution.values()), is(List.of("OFF", "ROUND", "PERSISTENT")));
        assertThat(names(Runs.values()), is(List.of("OFF", "SAME_SUIT")));
        assertThat(names(MatchLength.values()),
                is(List.of("ROUNDS_5", "ROUNDS_3", "ROUNDS_10", "POINTS_11", "POINTS_21", "ENDLESS")));
    }

    /** Equal play, the king of hearts lead and the forbidden finish are fixed rules, not record components. */
    @Test
    void theRecordHoldsTheKeysOfTheSpecAndNothingElse() {
        List<String> components = Arrays.stream(PresidentOptions.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();

        assertThat(components, is(List.of("afterPass", "exchange", "giveBack", "nextLead", "scoring",
                "length", "mustPlay", "reducedCut", "revolution", "ranking", "runs", "suitLock")));
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
