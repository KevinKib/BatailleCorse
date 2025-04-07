package org.kevinkib.bataillecorse.domain.hitrules;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HitRuleFixtures {

    public static HitRule alwaysApplyingRule() {
        HitRule rule = mock(HitRule.class);
        when(rule.applies(any())).thenReturn(true);

        return rule;
    }

    public static HitRule neverApplyingRule() {
        HitRule rule = mock(HitRule.class);
        when(rule.applies(any())).thenReturn(false);

        return rule;
    }
}
