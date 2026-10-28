package org.kevinkib.cardgames.kilodemerde.domain.bot;

import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeRules;
import org.kevinkib.cards.domain.Card;

/** The {@code medium} level: keeps the rank it holds most of and passes from the rank it holds fewest of (A15). */
public class GreedyKiloDeMerdeBot implements KiloDeMerdeBotStrategy {

    @Override
    public Card choosePass(KiloDeMerdeBotView view) {
        if (view.hand().isEmpty()) {
            throw new IllegalStateException("Nothing to pass");
        }
        return KiloDeMerdeRules.defaultPass(view.hand());
    }
}
