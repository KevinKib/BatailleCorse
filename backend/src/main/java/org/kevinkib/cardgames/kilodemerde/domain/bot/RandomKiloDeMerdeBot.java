package org.kevinkib.cardgames.kilodemerde.domain.bot;

import org.kevinkib.cards.domain.Card;

import java.util.List;
import java.util.random.RandomGenerator;

/** The {@code easy} level: passes a uniformly random card of its hand. */
public class RandomKiloDeMerdeBot implements KiloDeMerdeBotStrategy {

    private final RandomGenerator random;

    public RandomKiloDeMerdeBot(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public Card choosePass(KiloDeMerdeBotView view) {
        List<Card> hand = view.hand();
        if (hand.isEmpty()) {
            throw new IllegalStateException("Nothing to pass");
        }
        return hand.get(random.nextInt(hand.size()));
    }
}
