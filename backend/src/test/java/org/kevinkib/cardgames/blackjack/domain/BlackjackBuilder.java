package org.kevinkib.cardgames.blackjack.domain;

import org.kevinkib.cardgames.blackjack.domain.event.BlackjackEvent;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.kevinkib.cardgames.blackjack.domain.BlackjackCards.cards;

/**
 * Test builder for a Blackjack game in the betting phase of a chosen hand. Seats are 0..n-1 (A, B, C...). Unless
 * set: 100 points each, hand 1, the button on seat 0, default options. The shoe of a hand is a scripted prefix (in
 * the deal order of the spec) followed by the rest of the six decks in a fixed order; a hand without a script gets
 * the six decks in that fixed order.
 */
final class BlackjackBuilder {

    static final PlayerId A = new PlayerId(0);
    static final PlayerId B = new PlayerId(1);
    static final PlayerId C = new PlayerId(2);
    static final PlayerId D = new PlayerId(3);

    private final Map<Integer, String> shoes = new LinkedHashMap<>();
    private int[] stacks;
    private int handNumber = 1;
    private int button;
    private BlackjackOptions options = BlackjackOptions.defaults();
    private final GameId id = GameId.generate();

    private BlackjackBuilder(int seats) {
        this.stacks = new int[seats];
        Arrays.fill(stacks, BlackjackOptions.STARTING_STACK);
    }

    static BlackjackBuilder game(int seats) {
        return new BlackjackBuilder(seats);
    }

    static PlayerId seat(int index) {
        return new PlayerId(index);
    }

    BlackjackBuilder stacks(int... stacks) {
        this.stacks = stacks;
        return this;
    }

    BlackjackBuilder handNumber(int handNumber) {
        this.handNumber = handNumber;
        return this;
    }

    BlackjackBuilder button(int button) {
        this.button = button;
        return this;
    }

    BlackjackBuilder options(BlackjackOptions.Hands hands, BlackjackOptions.MaxBet maxBet) {
        this.options = new BlackjackOptions(hands, maxBet);
        return this;
    }

    /** Scripts the shoe of the starting hand. */
    BlackjackBuilder shoe(String codes) {
        return shoeForHand(handNumber, codes);
    }

    BlackjackBuilder shoeForHand(int hand, String codes) {
        shoes.put(hand, codes);
        return this;
    }

    Blackjack build() {
        ScriptedShoeSource source = new ScriptedShoeSource((gameId, hand) -> Shoe.sixDecks());
        shoes.forEach((hand, codes) -> source.fix(id, hand, cards(codes)));
        return new Blackjack(id, options, source, Arrays.stream(stacks).boxed().toList(), handNumber, button);
    }

    /** Every listed seat bets the given amount, in seat order from the first; returns the events of the last bet. */
    static List<BlackjackEvent> bets(Blackjack game, int... amounts) throws BlackjackException {
        List<BlackjackEvent> last = List.of();
        for (int i = 0; i < amounts.length; i++) {
            last = game.bet(seat(i), amounts[i]);
        }
        return last;
    }

    static <T extends BlackjackEvent> T event(List<BlackjackEvent> events, Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).findFirst()
                .orElseThrow(() -> new AssertionError("No " + type.getSimpleName() + " in " + events));
    }

    static boolean has(List<BlackjackEvent> events, Class<? extends BlackjackEvent> type) {
        return events.stream().anyMatch(type::isInstance);
    }
}
