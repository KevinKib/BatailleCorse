package org.kevinkib.cardgames.president.domain.rules;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PresidentOptionsTest {

    @Test
    void theP1KeysDefaultToTheClassicPreset() {
        PresidentOptions options = PresidentOptions.defaults();

        assertThat(options.afterPass(), is(AfterPass.REENTER));
        assertThat(options.firstLead(), is(FirstLead.LOWEST_CLUB));
    }

    @Test
    void theKeysOfTheVariantPrsExistAndBehaveAsOff() {
        PresidentOptions options = PresidentOptions.defaults();

        assertThat(options.equalPlay(), is(EqualPlay.OFF));
        assertThat(options.twoCuts(), is(TwoCuts.OFF));
        assertThat(options.quadClears(), is(false));
        assertThat(options.eightClears(), is(false));
        assertThat(options.forbiddenFinish(), is(ForbiddenFinish.OFF));
        assertThat(options.mustPlay(), is(false));
        assertThat(options.jokers(), is(Jokers.NONE));
        assertThat(options.revolution(), is(Revolution.OFF));
        assertThat(options.ranking(), is(Ranking.TWO_HIGH));
        assertThat(options.runs(), is(Runs.OFF));
        assertThat(options.suitLock(), is(false));
        assertThat(options.decks(), is(Decks.AUTO));
        assertThat(options.putsch(), is(false));
        assertThat(options.presidentFall(), is(false));
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
}
