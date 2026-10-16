package org.kevinkib.cardgames.president.domain.bot;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.card.RankOrder;
import org.kevinkib.cardgames.president.domain.play.Play;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What one bot seat has learned in the current round: the cards it saw played, and the cards it received and
 * gave in the exchange. Never another hand. It is fed by the seat's view on every broadcast (observing the same
 * view twice changes nothing) and starts afresh when a new round is dealt. Not thread-safe: the owner calls it
 * under the game's monitor.
 */
public final class PresidentBotMemory {

    private static final int RANKS = 13;
    private static final int CARDS_PER_RANK = 4;

    private final PlayerId seat;
    private final Set<Card> played = new LinkedHashSet<>();
    private final List<Card> received = new ArrayList<>();
    private final List<Card> given = new ArrayList<>();
    private int round = 1;

    public PresidentBotMemory(PlayerId seat) {
        this.seat = seat;
    }

    public PlayerId seat() {
        return seat;
    }

    /** The round the memory belongs to. */
    public int round() {
        return round;
    }

    /** Brings the memory in line with the seat's current view; a new round empties it first. */
    public void observe(PresidentBotView view) {
        if (view.round() != round) {
            played.clear();
            received.clear();
            given.clear();
            round = view.round();
        }
        view.trick().plays().forEach(trickPlay -> played.addAll(trickPlay.play().cards()));
        received.clear();
        received.addAll(view.received());
        given.clear();
        given.addAll(view.given());
    }

    /** Records cards seen played that the seat view no longer shows (the last play of a closed trick, announced by the broadcast). */
    public void learnPlayed(Collection<Card> cards) {
        played.addAll(cards);
    }

    // ----- queries -----

    /** The cards seen played this round, in the order they were seen. */
    public List<Card> played() {
        return List.copyOf(played);
    }

    /** The cards this seat received in this round's exchange. */
    public List<Card> received() {
        return List.copyOf(received);
    }

    /** The cards this seat gave in this round's exchange. */
    public List<Card> given() {
        return List.copyOf(given);
    }

    /** How many cards of the rank are neither in the given hand nor seen played: in the other hands or the tricks unseen. */
    public int unseen(FrenchRank rank, List<Card> hand) {
        long inHand = hand.stream().filter(card -> card.getRank() == rank).count();
        long gone = played.stream().filter(card -> card.getRank() == rank).count();
        return (int) Math.max(0, CARDS_PER_RANK - inHand - gone);
    }

    /**
     * Whether no set of the play's size can still beat the play, as far as the level counts: every rank above
     * must be one of the {@code countedRanks} strongest and hold fewer unseen cards than the play has.
     */
    public boolean isUnbeatable(Play play, List<Card> hand, int countedRanks, RankOrder order) {
        int firstCounted = RANKS - countedRanks;
        for (FrenchRank rank : order.ranksAscending()) {
            if (order.compare(rank, play.rank()) <= 0) {
                continue;
            }
            if (order.strength(rank) < firstCounted || unseen(rank, hand) >= play.count()) {
                return false;
            }
        }
        return true;
    }
}
