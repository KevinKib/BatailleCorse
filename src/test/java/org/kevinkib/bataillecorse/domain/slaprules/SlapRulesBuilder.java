package org.kevinkib.bataillecorse.domain.slaprules;

import java.util.List;

public final class SlapRulesBuilder {
    private List<SlapRule> rules;

    private SlapRulesBuilder() {
    }

    public static SlapRulesBuilder aSlapRules() {
        return new SlapRulesBuilder();
    }

    public SlapRulesBuilder withRules(List<SlapRule> rules) {
        this.rules = rules;
        return this;
    }

    public SlapRules build() {
        return new SlapRules(rules);
    }
}
