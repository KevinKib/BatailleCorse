package org.kevinkib.bataillecorse.domain.hitrules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.domain.CentralPile;
import org.kevinkib.bataillecorse.domain.CentralPileFixtures;

import java.util.Arrays;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class HitRulesTest {

    private CentralPile pile;

    @BeforeEach
    public void init() {
        pile = CentralPileFixtures.createEmptyCentralPile();
    }

    @Test
    public void givenZeroApplyingRule_thenDoNotApply() {

        HitRules rules = HitRulesBuilder.aHitRules()
                .withRules(Arrays.asList(
                        HitRuleFixtures.neverApplyingRule(),
                        HitRuleFixtures.neverApplyingRule()
                ))
                .build();

        assertThat(rules.applies(pile), is(false));
    }

    @Test
    public void givenAtLeastOneApplyingRule_thenApplies() {

        HitRules rules = HitRulesBuilder.aHitRules()
                .withRules(Arrays.asList(
                        HitRuleFixtures.neverApplyingRule(),
                        HitRuleFixtures.alwaysApplyingRule()
                ))
                .build();

        assertThat(rules.applies(pile), is(true));
    }

}