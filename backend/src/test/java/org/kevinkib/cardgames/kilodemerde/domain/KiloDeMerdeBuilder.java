package org.kevinkib.cardgames.kilodemerde.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.kevinkib.cardgames.kilodemerde.domain.KiloCards.cards;

/**
 * Test builder for a Kilo de merde game in a known state. Seats are 0..n-1 (A, B, C...). Unless set, every hand is
 * {@code JS QS KS 2S} (no set), nobody holds a kilo card, the kilo pile is {@code AH 9C 8C 6C 5C} (top first), the
 * step is 1, no round has been played and rounds default to ten. The deck source keeps every order it is given.
 */
final class KiloDeMerdeBuilder {

    static final PlayerId A = new PlayerId(0);
    static final PlayerId B = new PlayerId(1);
    static final PlayerId C = new PlayerId(2);
    static final PlayerId D = new PlayerId(3);
    static final PlayerId E = new PlayerId(4);

    private final int seats;
    private final Map<PlayerId, List<Card>> hands = new LinkedHashMap<>();
    private final Map<PlayerId, List<Card>> kiloCards = new LinkedHashMap<>();
    private List<Card> pile = new ArrayList<>(cards("AH 9C 8C 6C 5C"));
    private int step = 1;
    private int roundsPlayed;
    private KiloDeMerdeOptions options = KiloDeMerdeOptions.defaults();
    private DeckSource deckSource = new RecordingDeckSource();
    private final GameId id = GameId.generate();

    private KiloDeMerdeBuilder(int seats) {
        this.seats = seats;
    }

    static KiloDeMerdeBuilder game(int seats) {
        return new KiloDeMerdeBuilder(seats);
    }

    static PlayerId seat(int index) {
        return new PlayerId(index);
    }

    GameId id() {
        return id;
    }

    KiloDeMerdeBuilder hand(PlayerId seat, String codes) {
        hands.put(seat, new ArrayList<>(cards(codes)));
        return this;
    }

    KiloDeMerdeBuilder kiloCards(PlayerId seat, String codes) {
        kiloCards.put(seat, new ArrayList<>(cards(codes)));
        return this;
    }

    KiloDeMerdeBuilder pile(String codes) {
        pile = new ArrayList<>(cards(codes));
        return this;
    }

    KiloDeMerdeBuilder step(int step) {
        this.step = step;
        return this;
    }

    KiloDeMerdeBuilder roundsPlayed(int roundsPlayed) {
        this.roundsPlayed = roundsPlayed;
        return this;
    }

    KiloDeMerdeBuilder rounds(KiloDeMerdeOptions.Rounds rounds) {
        this.options = new KiloDeMerdeOptions(rounds);
        return this;
    }

    KiloDeMerdeBuilder deckSource(DeckSource deckSource) {
        this.deckSource = deckSource;
        return this;
    }

    KiloDeMerde build() {
        Map<PlayerId, List<Card>> filledHands = new LinkedHashMap<>();
        Map<PlayerId, List<Card>> filledKilos = new LinkedHashMap<>();
        for (int i = 0; i < seats; i++) {
            PlayerId seat = new PlayerId(i);
            filledHands.put(seat, hands.getOrDefault(seat, new ArrayList<>(cards("JS QS KS 2S"))));
            filledKilos.put(seat, kiloCards.getOrDefault(seat, new ArrayList<>()));
        }
        return new KiloDeMerde(id, options, deckSource, roundsPlayed, step, filledHands, filledKilos, pile);
    }
}
