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
        assertThat(options.firstLead(), is(FirstLead.KING_OF_HEARTS));
    }

    @Test
    void theClassicDefaultsOfTheTrickVariants() {
        PresidentOptions options = PresidentOptions.defaults();

        assertThat(options.equalPlay(), is(EqualPlay.ON));
        assertThat(options.twoCuts(), is(TwoCuts.OFF));
        assertThat(options.quadClears(), is(true));
        assertThat(options.forbiddenFinish(), is(ForbiddenFinish.LAST_PLACE));
        assertThat(options.mustPlay(), is(false));
    }

    @Test
    void theNewWithersReplaceOneKeyAndKeepTheOthers() {
        PresidentOptions options = PresidentOptions.defaults().withTwoCuts(TwoCuts.LADDER).withQuadClears(false)
                .withForbiddenFinish(ForbiddenFinish.OFF).withMustPlay(true);

        assertThat(options.twoCuts(), is(TwoCuts.LADDER));
        assertThat(options.quadClears(), is(false));
        assertThat(options.forbiddenFinish(), is(ForbiddenFinish.OFF));
        assertThat(options.mustPlay(), is(true));
        assertThat(options.equalPlay(), is(EqualPlay.ON));
        assertThat(options.afterPass(), is(AfterPass.REENTER));
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
        PresidentOptions options = PresidentOptions.defaults().withAfterPass(AfterPass.OUT)
                .withFirstLead(FirstLead.RANDOM);

        assertThat(options.afterPass(), is(AfterPass.OUT));
        assertThat(options.firstLead(), is(FirstLead.RANDOM));
        assertThat(options.exchange(), is(ExchangeAmount.TWO));
    }

    /** The owner dropped jokers, two decks, eight stops, the skip, the trick revolution, any-suit runs. */
    @Test
    void theDroppedValuesAreGone() {
        assertThat(names(EqualPlay.values()), is(List.of("ON", "OFF")));
        assertThat(names(TwoCuts.values()), is(List.of("OFF", "SINGLE", "LADDER")));
        assertThat(names(ForbiddenFinish.values()), is(List.of("LAST_PLACE", "OFF")));
        assertThat(names(Revolution.values()), is(List.of("OFF", "ROUND", "PERSISTENT")));
        assertThat(names(Runs.values()), is(List.of("OFF", "SAME_SUIT")));
        assertThat(names(FirstLead.values()),
                is(List.of("KING_OF_HEARTS", "LOWEST_CLUB", "QUEEN_OF_HEARTS", "RANDOM")));
        assertThat(names(MatchLength.values()),
                is(List.of("ROUNDS_5", "ROUNDS_3", "ROUNDS_10", "POINTS_11", "POINTS_21", "ENDLESS")));
    }

    @Test
    void theRecordHoldsTheSixteenKeysOfTheSpecAndNothingElse() {
        List<String> components = Arrays.stream(PresidentOptions.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();

        assertThat(components, is(List.of("afterPass", "firstLead", "exchange", "giveBack", "nextLead", "scoring",
                "length", "equalPlay", "twoCuts", "quadClears", "forbiddenFinish", "mustPlay", "revolution",
                "ranking", "runs", "suitLock")));
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
