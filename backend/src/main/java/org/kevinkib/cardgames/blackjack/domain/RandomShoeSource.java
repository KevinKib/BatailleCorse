package org.kevinkib.cardgames.blackjack.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cards.domain.Card;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

/** The default source: six French decks shuffled afresh every time. */
public class RandomShoeSource implements ShoeSource {

    @Override
    public List<Card> shuffledShoe(GameId id, int handNumber) {
        List<Card> shoe = new ArrayList<>(Shoe.sixDecks());
        Collections.shuffle(shoe, ThreadLocalRandom.current());
        return shoe;
    }
}
