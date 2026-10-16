package org.kevinkib.cardgames.president.domain.trick;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.play.Constraint;
import org.kevinkib.cardgames.president.domain.play.Play;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * The mutable state of one trick, driven by {@code Round}: the plays so far, the seats that answered since
 * the last play, and the seats that are out of the trick (a pass under {@code afterPass = out}).
 */
public final class Trick {

    private final int leader;
    private final List<TrickPlay> plays = new ArrayList<>();
    private final Set<Integer> responded = new HashSet<>();
    private final Set<Integer> out = new HashSet<>();
    private boolean bound;

    public Trick(int leader) {
        this.leader = leader;
    }

    public boolean isEmpty() {
        return plays.isEmpty();
    }

    /** The play to beat, or {@code null} for a leader who has not played. */
    public Play top() {
        return plays.isEmpty() ? null : plays.get(plays.size() - 1).play();
    }

    /** The seat of the last play, or -1 when nothing was played. */
    public int lastPlayer() {
        return plays.isEmpty() ? -1 : plays.get(plays.size() - 1).seat().id();
    }

    /** A play: it answers everyone, so the passes since the previous play no longer count. */
    public void play(int seat, Play play) {
        play(seat, play, false);
    }

    /**
     * A play; {@code equal} when it repeats the rank and count of the previous top under equal play, which binds
     * the next player to match it or pass.
     */
    public void play(int seat, Play play, boolean equal) {
        plays.add(new TrickPlay(new PlayerId(seat), play));
        responded.clear();
        bound = equal;
    }

    /** A pass; {@code final} when the passer is out of the trick until it ends. The first pass ends the binding. */
    public void pass(int seat, boolean finalPass) {
        responded.add(seat);
        bound = false;
        if (finalPass) {
            out.add(seat);
        }
    }

    /** What the next play must answer. */
    public Constraint constraint() {
        if (plays.isEmpty()) {
            return Constraint.FREE;
        }
        return bound ? Constraint.MATCH_OR_PASS : Constraint.BEAT;
    }

    /** The plays of the top rank in succession, or {@code null} on an empty trick. */
    public Chain chain() {
        if (plays.isEmpty()) {
            return null;
        }
        FrenchRank rank = plays.get(plays.size() - 1).play().rank();
        List<Card> cards = new ArrayList<>();
        int count = 0;
        for (int index = plays.size() - 1; index >= 0 && plays.get(index).play().rank() == rank; index--) {
            cards.addAll(0, plays.get(index).play().cards());
            count++;
        }
        return new Chain(rank, cards, count);
    }

    public boolean isOut(int seat) {
        return out.contains(seat);
    }

    /**
     * Over when somebody played and every eligible seat but the last player has answered since.
     *
     * @param eligible whether a seat can still act in this trick (holds cards and is not out of it)
     */
    public boolean isComplete(IntPredicate eligible, int seats) {
        int last = lastPlayer();
        if (last < 0) {
            return false;
        }
        for (int seat = 0; seat < seats; seat++) {
            if (seat != last && eligible.test(seat) && !responded.contains(seat)) {
                return false;
            }
        }
        return true;
    }

    public List<Card> cards() {
        List<Card> cards = new ArrayList<>();
        plays.forEach(p -> cards.addAll(p.play().cards()));
        return cards;
    }

    public TrickView view() {
        List<PlayerId> passed = responded.stream().sorted().map(PlayerId::new).toList();
        return new TrickView(new PlayerId(leader), plays, passed, constraint(), chain());
    }
}
