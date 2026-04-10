package org.kevinkib.bataillecorse.core.domain.slaprules;

import org.kevinkib.bataillecorse.core.domain.slaprules.SlapRule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SlapRuleFixtures {

    public static SlapRule alwaysApplyingRule() {
        SlapRule rule = mock(SlapRule.class);
        when(rule.applies(any())).thenReturn(true);

        return rule;
    }

    public static SlapRule neverApplyingRule() {
        SlapRule rule = mock(SlapRule.class);
        when(rule.applies(any())).thenReturn(false);

        return rule;
    }
}
