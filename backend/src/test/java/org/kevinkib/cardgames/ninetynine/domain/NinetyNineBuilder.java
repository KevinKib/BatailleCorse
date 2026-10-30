package org.kevinkib.cardgames.ninetynine.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test builder for a 99 game in a known state. Seats are 0..n-1 (A, B, C...). Unless set, every hand is three
 * safe filler cards (9, 9, 9), the stock is twenty nines, the pile is empty, the total is 0, the direction is
 * clockwise and seat 0 is to play.
 */
final class NinetyNineBuilder {

    static final PlayerId A = new PlayerId(0);
    static final PlayerId B = new PlayerId(1);
    static final PlayerId C = new PlayerId(2);

    private final int seats;
    private final Map<PlayerId, List<Card>> hands = new LinkedHashMap<>();
    private final Map<PlayerId, Integer> lives = new LinkedHashMap<>();
    private List<Card> stock;
    private List<Card> pile = List.of();
    private int total;
    private Direction direction = Direction.CLOCKWISE;
    private PlayerId current = A;
    private int round = 1;
    private NinetyNineOptions options = NinetyNineOptions.defaults();
    private DeckSource deckSource = new ScriptedDeckSource(new RandomDeckSource());
    private final GameId id = GameId.generate();

    private NinetyNineBuilder(int seats) {
        this.seats = seats;
        for (int i = 0; i < seats; i++) {
            lives.put(new PlayerId(i), 3);
        }
        this.stock = new ArrayList<>(cards("9S 9S 9S 9S 9S 9S 9S 9S 9S 9S 9S 9S 9S 9S 9S 9S 9S 9S 9S 9S"));
    }

    static NinetyNineBuilder game(int seats) {
        return new NinetyNineBuilder(seats);
    }

    static PlayerId seat(int index) {
        return new PlayerId(index);
    }

    /** "KH" is the king of hearts, "10S" the ten of spades, "AD" the ace of diamonds. */
    static Card c(String code) {
        String rank = code.substring(0, code.length() - 1);
        FrenchRank frenchRank = switch (rank) {
            case "A" -> FrenchRank.ACE;
            case "2" -> FrenchRank.TWO;
            case "3" -> FrenchRank.THREE;
            case "4" -> FrenchRank.FOUR;
            case "5" -> FrenchRank.FIVE;
            case "6" -> FrenchRank.SIX;
            case "7" -> FrenchRank.SEVEN;
            case "8" -> FrenchRank.EIGHT;
            case "9" -> FrenchRank.NINE;
            case "10" -> FrenchRank.TEN;
            case "J" -> FrenchRank.JACK;
            case "Q" -> FrenchRank.QUEEN;
            case "K" -> FrenchRank.KING;
            default -> throw new IllegalArgumentException(code);
        };
        FrenchSuit suit = switch (code.charAt(code.length() - 1)) {
            case 'H' -> FrenchSuit.HEART;
            case 'D' -> FrenchSuit.DIAMOND;
            case 'C' -> FrenchSuit.CLUB;
            case 'S' -> FrenchSuit.SPADE;
            default -> throw new IllegalArgumentException(code);
        };
        return new Card(frenchRank, suit);
    }

    static List<Card> cards(String codes) {
        return codes.isBlank() ? List.of()
                : Arrays.stream(codes.trim().split("\\s+")).map(NinetyNineBuilder::c).toList();
    }

    GameId id() {
        return id;
    }

    NinetyNineBuilder hand(PlayerId seat, String codes) {
        hands.put(seat, new ArrayList<>(cards(codes)));
        return this;
    }

    NinetyNineBuilder lives(PlayerId seat, int count) {
        lives.put(seat, count);
        return this;
    }

    NinetyNineBuilder stock(String codes) {
        stock = new ArrayList<>(cards(codes));
        return this;
    }

    NinetyNineBuilder pile(String codes) {
        pile = new ArrayList<>(cards(codes));
        return this;
    }

    NinetyNineBuilder total(int total) {
        this.total = total;
        return this;
    }

    NinetyNineBuilder direction(Direction direction) {
        this.direction = direction;
        return this;
    }

    NinetyNineBuilder current(PlayerId seat) {
        this.current = seat;
        return this;
    }

    NinetyNineBuilder round(int round) {
        this.round = round;
        return this;
    }

    NinetyNineBuilder quick() {
        options = new NinetyNineOptions(options.lives(), NinetyNineOptions.QuickMode.ON, options.freePlay());
        return this;
    }

    /** The restrictive rule: a seat holding a safe card may not play a card that goes over 99 (free play off). */
    NinetyNineBuilder guarded() {
        options = new NinetyNineOptions(options.lives(), options.quick(), NinetyNineOptions.FreePlay.OFF);
        return this;
    }

    NinetyNineBuilder deckSource(DeckSource deckSource) {
        this.deckSource = deckSource;
        return this;
    }

    NinetyNine build() {
        Map<PlayerId, List<Card>> filled = new LinkedHashMap<>();
        for (int i = 0; i < seats; i++) {
            PlayerId seat = new PlayerId(i);
            List<Card> hand = hands.getOrDefault(seat, new ArrayList<>(cards("9H 9D 9C")));
            filled.put(seat, lives.get(seat) == 0 ? new ArrayList<>() : hand);
        }
        return new NinetyNine(id, options, deckSource, round, lives, filled, stock, pile, total, direction, current);
    }
}
