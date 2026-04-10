package org.kevinkib.bataillecorse.core.domain.slaprules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.core.domain.CentralPile;
import org.kevinkib.bataillecorse.core.domain.CentralPileFixtures;

import java.util.Arrays;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class SlapRulesTest {

    private CentralPile pile;

    @BeforeEach
    public void init() {
        pile = CentralPileFixtures.createEmptyCentralPile();
    }

    @Test
    public void givenZeroApplyingRule_thenDoNotApply() {

        SlapRules rules = SlapRulesBuilder.aSlapRules()
                .withRules(Arrays.asList(
                        SlapRuleFixtures.neverApplyingRule(),
                        SlapRuleFixtures.neverApplyingRule()
                ))
                .build();

        assertThat(rules.applies(pile), is(false));
    }

    @Test
    public void givenAtLeastOneApplyingRule_thenApplies() {

        SlapRules rules = SlapRulesBuilder.aSlapRules()
                .withRules(Arrays.asList(
                        SlapRuleFixtures.neverApplyingRule(),
                        SlapRuleFixtures.alwaysApplyingRule()
                ))
                .build();

        assertThat(rules.applies(pile), is(true));
    }

}