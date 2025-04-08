package org.kevinkib.bataillecorse.domain.slaprules;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SlapRulesFixtures {

    public static SlapRules alwaysApplyingRules() {
        SlapRules rules = mock(SlapRules.class);
        when(rules.applies(any())).thenReturn(true);

        return rules;
    }

    public static SlapRules neverApplyingRules() {
        SlapRules rules = mock(SlapRules.class);
        when(rules.applies(any())).thenReturn(false);

        return rules;
    }
}
