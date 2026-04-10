package org.kevinkib.bataillecorse.core.domain.slaprules;

import org.kevinkib.bataillecorse.core.domain.CentralPile;

import java.util.Arrays;
import java.util.List;

public class SlapRules {

    private final List<SlapRule> rules;

    public SlapRules(List<SlapRule> rules) {
        this.rules = rules;
    }

    public boolean applies(CentralPile pile) {
        for (SlapRule rule : rules) {
            if (rule.applies(pile)) {
                return true;
            }
        }

        return false;
    }

    public static SlapRules DEFAULT = new SlapRules(Arrays.asList(
            new CanSlapTens(),
            new CanSlapSameCardAsBelow(),
            new CanSlapSandwich(),
            new CanSlapSumOfTen())
    );
}
