package org.kevinkib.bataillecorse.domain.hitrules;

import org.kevinkib.cards.domain.Pile;

import java.util.Arrays;
import java.util.List;

public class HitRules {

    private final List<HitRule> rules;

    public HitRules(List<HitRule> rules) {
        this.rules = rules;
    }

    public boolean applies(Pile pile) {
        for (HitRule rule : rules) {
            if (rule.applies(pile)) {
                return true;
            }
        }

        return false;
    }

    public static HitRules DEFAULT = new HitRules(Arrays.asList(
            new CanHitTens(),
            new CanHitSameCardAsBelow(),
            new CanHitSandwich(),
            new CanHitSumOfTen())
    );
}
