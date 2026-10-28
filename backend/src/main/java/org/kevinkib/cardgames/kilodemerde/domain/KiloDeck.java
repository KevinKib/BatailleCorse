package org.kevinkib.cardgames.kilodemerde.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.ArrayList;
import java.util.List;

/**
 * The two decks of a Kilo de merde game, as pure tables (spec section 3.1): the hand deck (four suits of the dealt
 * ranks, exactly four cards per player) and the kilo pile (the other cards of one 52-card deck, jokers excluded).
 * The 7s, the Aces and the 10s are never dealt.
 */
public final class KiloDeck {

    public static final int MIN_PLAYERS = 3;
    public static final int MAX_PLAYERS = 10;

    /** Ranks added one by one from the third player on: Jack, Queen and King, then 2, 3, 4, 5, 6, 8 and 9. */
    private static final List<FrenchRank> ADDED_RANKS = List.of(
            FrenchRank.TWO, FrenchRank.THREE, FrenchRank.FOUR, FrenchRank.FIVE, FrenchRank.SIX,
            FrenchRank.EIGHT, FrenchRank.NINE);

    private static final List<FrenchRank> FACE_RANKS = List.of(FrenchRank.JACK, FrenchRank.QUEEN, FrenchRank.KING);

    private static final List<FrenchSuit> SUITS = List.of(
            FrenchSuit.CLUB, FrenchSuit.DIAMOND, FrenchSuit.HEART, FrenchSuit.SPADE);

    private KiloDeck() {
    }

    /** The ranks dealt to the given number of players, lowest rank first (the face cards last). */
    public static List<FrenchRank> dealtRanks(int players) {
        if (players < MIN_PLAYERS || players > MAX_PLAYERS) {
            throw new IllegalArgumentException("Kilo de merde is played by 3 to 10 players, not " + players);
        }
        List<FrenchRank> ranks = new ArrayList<>(ADDED_RANKS.subList(0, players - MIN_PLAYERS));
        ranks.addAll(FACE_RANKS);
        return List.copyOf(ranks);
    }

    /** The cards dealt: four suits of each dealt rank, in rank then suit order (the caller shuffles). */
    public static List<Card> handDeck(int players) {
        List<Card> cards = new ArrayList<>();
        for (FrenchRank rank : dealtRanks(players)) {
            for (FrenchSuit suit : SUITS) {
                cards.add(new Card(rank, suit));
            }
        }
        return List.copyOf(cards);
    }

    /** The other cards of the 52-card deck, in rank then suit order (the caller shuffles). */
    public static List<Card> kiloPile(int players) {
        List<FrenchRank> dealt = dealtRanks(players);
        List<Card> cards = new ArrayList<>();
        for (FrenchRank rank : FrenchRank.values()) {
            if (rank == FrenchRank.JOKER || dealt.contains(rank)) {
                continue;
            }
            for (FrenchSuit suit : SUITS) {
                cards.add(new Card(rank, suit));
            }
        }
        return List.copyOf(cards);
    }
}
