package org.kevinkib.bataillecorse.domain.hitrules;

import java.util.List;

public final class HitRulesBuilder {
    private List<HitRule> rules;

    private HitRulesBuilder() {
    }

    public static HitRulesBuilder aHitRules() {
        return new HitRulesBuilder();
    }

    public HitRulesBuilder withRules(List<HitRule> rules) {
        this.rules = rules;
        return this;
    }

    public HitRules build() {
        return new HitRules(rules);
    }
}
