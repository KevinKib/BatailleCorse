package org.kevinkib.cardgames.ninetynine.domain.bot;

import org.kevinkib.cardgames.ninetynine.domain.CardEffect;
import org.kevinkib.cards.domain.Card;

import java.util.List;
import java.util.Random;

/** The {@code easy} level: a uniformly random safe card and, for an Ace, a random allowed value. */
public class RandomNinetyNineBot implements NinetyNineBotStrategy {

    private final Random random;

    public RandomNinetyNineBot(Random random) {
        this.random = random;
    }

    @Override
    public NinetyNineDecision decide(NinetyNineBotView view) {
        List<Card> safe = CardEffect.safeCards(view.total(), view.hand());
        if (safe.isEmpty()) {
            throw new IllegalStateException("No safe card to play at " + view.total());
        }
        Card card = safe.get(random.nextInt(safe.size()));
        if (NinetyNineMoves.isAce(card)) {
            List<Integer> values = CardEffect.aceValues(view.total());
            return new NinetyNineDecision.Play(card, values.get(random.nextInt(values.size())));
        }
        return new NinetyNineDecision.Play(card, null);
    }
}
