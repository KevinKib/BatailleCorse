package org.kevinkib.cardgames.kilodemerde.domain.bot;

import org.kevinkib.cards.domain.Card;

/** Port: how a Kilo de merde bot picks the card to pass from what its seat can see. */
public interface KiloDeMerdeBotStrategy {

    /**
     * The card to pass this step.
     *
     * @return a card of {@code view.hand()}
     * @throws IllegalStateException when the seat has nothing to pass
     */
    Card choosePass(KiloDeMerdeBotView view);
}
