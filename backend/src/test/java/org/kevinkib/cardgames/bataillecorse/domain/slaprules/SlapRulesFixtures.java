package org.kevinkib.cardgames.bataillecorse.domain.slaprules;

import org.kevinkib.cardgames.bataillecorse.domain.slaprules.SlapRules;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SlapRulesFixtures {

    public static SlapRules anySlapRules() {
        return alwaysApplyingSlapRules();
    }

    public static SlapRules alwaysApplyingSlapRules() {
        SlapRules rules = mock(SlapRules.class);
        when(rules.applies(any())).thenReturn(true);

        return rules;
    }

    public static SlapRules neverApplyingSlapRules() {
        SlapRules rules = mock(SlapRules.class);
        when(rules.applies(any())).thenReturn(false);

        return rules;
    }
}
