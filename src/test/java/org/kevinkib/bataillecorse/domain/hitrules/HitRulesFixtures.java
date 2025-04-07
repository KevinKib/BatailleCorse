package org.kevinkib.bataillecorse.domain.hitrules;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HitRulesFixtures {

    public static HitRules alwaysApplyingRules() {
        HitRules rules = mock(HitRules.class);
        when(rules.applies(any())).thenReturn(true);

        return rules;
    }

    public static HitRules neverApplyingRules() {
        HitRules rules = mock(HitRules.class);
        when(rules.applies(any())).thenReturn(false);

        return rules;
    }
}
