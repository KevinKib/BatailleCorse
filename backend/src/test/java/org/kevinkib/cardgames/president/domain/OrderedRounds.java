package org.kevinkib.cardgames.president.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.rules.PresidentException;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.List;

import static org.kevinkib.cardgames.president.domain.PresidentFixtures.card;

/**
 * A round whose finishing order is chosen. Each seat holds a "go-out" card, climbing with its place in
 * {@code order}, so the seat of a place beats the seat of the previous one. The first player out closes the
 * trick, so the seat that leads next is the following seat of the table, not necessarily the next one in
 * {@code order}: a seat that has to lead without going out holds low "filler" cards (the 3s and 4s) for that.
 * The first seat of {@code order} leads and goes out first, and so on. The seat given last never plays and
 * takes the last place.
 */
final class OrderedRounds {

    private static final String[] GO_OUT = {"5", "6", "7", "8", "9", "10", "J", "Q"};
    private static final String[] FILLERS = {"3C", "3D", "3H", "3S", "4C", "4D", "4H", "4S"};

    private OrderedRounds() {
    }

    static President start(GameId id, DeckSource deckSource, PresidentOptions options, int... order) {
        int seats = order.length;
        int[] fillers = fillersNeeded(order);
        List<List<Card>> hands = new ArrayList<>();
        for (int seat = 0; seat < seats; seat++) {
            hands.add(new ArrayList<>());
        }
        int nextFiller = 0;
        for (int place = 0; place < seats; place++) {
            hands.get(order[place]).add(card(GO_OUT[place] + "C"));
        }
        for (int seat = 0; seat < seats; seat++) {
            for (int i = 0; i < fillers[seat]; i++) {
                hands.get(seat).add(card(FILLERS[nextFiller++]));
            }
        }
        return new President(id, options, 1L, deckSource, hands, order[0]);
    }

    /** How many times each seat has to lead a trick without being the one who goes out next. */
    private static int[] fillersNeeded(int[] order) {
        int seats = order.length;
        int[] fillers = new int[seats];
        List<Integer> alive = new ArrayList<>();
        for (int seat = 0; seat < seats; seat++) {
            alive.add(seat);
        }
        int leader = order[0];
        for (int place = 0; place < seats - 1; place++) {
            int target = order[place];
            if (leader != target) {
                fillers[leader]++;
            }
            alive.remove(Integer.valueOf(target));
            leader = nextAlive(alive, target, seats);
        }
        return fillers;
    }

    private static int nextAlive(List<Integer> alive, int from, int seats) {
        for (int step = 1; step <= seats; step++) {
            int seat = (from + step) % seats;
            if (alive.contains(seat)) {
                return seat;
            }
        }
        return from;
    }

    /** Plays the round to its end: the seat of each place plays its go-out card, the others lead a filler or pass. */
    static List<PresidentEvent> finish(President game, int... order) throws PresidentException {
        List<PresidentEvent> last = List.of();
        while (game.finishingOrder().size() < order.length - 1) {
            PlayerId current = game.currentSeat();
            List<Card> hand = game.hand(current);
            if (current.id() == order[game.finishingOrder().size()]) {
                last = game.play(current, List.of(hand.get(hand.size() - 1)));
            } else if (game.trick().plays().isEmpty()) {
                last = game.play(current, List.of(hand.get(0)));
            } else {
                last = game.pass(current);
            }
        }
        return last;
    }

    static President playedRoundOne(GameId id, DeckSource deckSource, PresidentOptions options, int... order)
            throws PresidentException {
        President game = start(id, deckSource, options, order);
        finish(game, order);
        return game;
    }
}
