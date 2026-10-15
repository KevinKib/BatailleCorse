package org.kevinkib.cardgames.president.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.rules.PresidentException;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.List;

import static org.kevinkib.cardgames.president.domain.PresidentFixtures.card;

/**
 * A round whose finishing order is chosen: every seat holds one card, the cards climb in the order of
 * {@code order}, so the first seat of {@code order} leads and goes out first, and so on. The seat given
 * last never plays and takes the last place.
 */
final class OrderedRounds {

    private static final String[] RANKS = {"3", "4", "5", "6", "7", "8", "9", "10"};

    private OrderedRounds() {
    }

    static President start(GameId id, DeckSource deckSource, PresidentOptions options, int... order) {
        List<List<Card>> hands = new ArrayList<>();
        for (int seat = 0; seat < order.length; seat++) {
            hands.add(List.of());
        }
        for (int place = 0; place < order.length; place++) {
            hands.set(order[place], List.of(card(RANKS[place] + "C")));
        }
        return new President(id, options, 1L, deckSource, hands, order[0]);
    }

    /** Plays the round to its end; the rank of the card of each seat makes it go out in the given order. */
    static void finish(President game, int... order) throws PresidentException {
        while (game.finishingOrder().size() < order.length - 1) {
            PlayerId current = game.currentSeat();
            if (current.id() == order[game.finishingOrder().size()]) {
                game.play(current, List.of(game.hand(current).get(0)));
            } else {
                game.pass(current);
            }
        }
    }

    static President playedRoundOne(GameId id, DeckSource deckSource, PresidentOptions options, int... order)
            throws PresidentException {
        President game = start(id, deckSource, options, order);
        finish(game, order);
        return game;
    }
}
