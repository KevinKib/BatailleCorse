package org.kevinkib.cardgames.president.domain.round;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.card.HandSort;
import org.kevinkib.cardgames.president.domain.card.RankOrder;
import org.kevinkib.cardgames.president.domain.event.Passed;
import org.kevinkib.cardgames.president.domain.event.PlayerOut;
import org.kevinkib.cardgames.president.domain.event.Played;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.event.RoundOver;
import org.kevinkib.cardgames.president.domain.event.TrickEndReason;
import org.kevinkib.cardgames.president.domain.event.TrickWon;
import org.kevinkib.cardgames.president.domain.play.BeatRule;
import org.kevinkib.cardgames.president.domain.play.IllegalPlayException;
import org.kevinkib.cardgames.president.domain.play.LegalPlays;
import org.kevinkib.cardgames.president.domain.play.Play;
import org.kevinkib.cardgames.president.domain.play.PlayParser;
import org.kevinkib.cardgames.president.domain.rules.AfterPass;
import org.kevinkib.cardgames.president.domain.rules.PresidentException;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cardgames.president.domain.trick.MustLeadException;
import org.kevinkib.cardgames.president.domain.trick.Trick;
import org.kevinkib.cardgames.president.domain.trick.TrickView;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.List;

/**
 * The rules of one round, seats being indexes from 0 in play order. Every command validates first and only
 * then mutates, so a refused command changes nothing. Not thread-safe: the {@code President} aggregate
 * serialises the calls.
 */
public final class Round {

    private final PresidentOptions options;
    private final RankOrder order = RankOrder.standard();
    private final List<List<Card>> hands = new ArrayList<>();
    private final List<Integer> finishing = new ArrayList<>();
    private final List<Card> discard = new ArrayList<>();
    private final PlayerId dealer;
    private Trick trick;
    private int current;
    private Phase phase;

    /**
     * @param hands  one hand per seat, any order (they are sorted)
     * @param leader the seat that leads the first trick
     */
    public Round(PresidentOptions options, List<List<Card>> hands, int dealer, int leader) {
        this(options, hands, dealer, leader, Phase.PLAYING);
    }

    /**
     * @param phase {@code PLAYING}, or {@code EXCHANGE} when cards still change hands before the first trick
     */
    public Round(PresidentOptions options, List<List<Card>> hands, int dealer, int leader, Phase phase) {
        this.phase = phase;
        this.options = options;
        for (List<Card> hand : hands) {
            this.hands.add(new ArrayList<>(HandSort.sorted(hand, order)));
        }
        this.dealer = new PlayerId(dealer);
        this.trick = new Trick(leader);
        this.current = leader;
    }

    // ----- commands -----

    public List<PresidentEvent> play(PlayerId who, List<Card> cards) throws PresidentException {
        checkTurn(who);
        if (cards == null || cards.isEmpty()) {
            throw new IllegalPlayException("A play holds at least one card");
        }
        int seat = current;
        List<Card> remaining = removeFromHand(seat, cards);
        Play play = PlayParser.parse(cards);
        if (trick.top() != null) {
            BeatRule.check(play, trick.top(), order);
        }

        hands.set(seat, remaining);
        trick.play(seat, play);
        List<PresidentEvent> events = new ArrayList<>();
        events.add(new Played(who, play.cards()));
        if (remaining.isEmpty()) {
            finishing.add(seat);
            events.add(new PlayerOut(who, finishing.size()));
            if (seatsWithCards() == 1) {
                endRound(events);
                return events;
            }
        }
        afterAction(events);
        return events;
    }

    public List<PresidentEvent> pass(PlayerId who) throws PresidentException {
        checkTurn(who);
        if (trick.isEmpty()) {
            throw new MustLeadException();
        }
        int seat = current;
        trick.pass(seat, options.afterPass() == AfterPass.OUT);
        List<PresidentEvent> events = new ArrayList<>();
        events.add(new Passed(who));
        afterAction(events);
        return events;
    }

    /** The exchange: moves cards between two hands (the exchange phase only, validated by the caller's phase). */
    public void transfer(PlayerId from, PlayerId to, List<Card> cards) throws PresidentException {
        int source = indexOf(from);
        int target = indexOf(to);
        List<Card> remaining = removeFromHand(source, cards);
        List<Card> received = new ArrayList<>(hands.get(target));
        received.addAll(cards);
        hands.set(source, new ArrayList<>(HandSort.sorted(remaining, order)));
        hands.set(target, new ArrayList<>(HandSort.sorted(received, order)));
    }

    /** The exchange is done: the first trick can start. */
    public void beginPlay() {
        phase = Phase.PLAYING;
    }

    /**
     * The player leaves the match: their hand is set aside. Whoever was to act is replaced, a trick that
     * only waited for them ends, and the round ends when a single hand is left. A player who is already out
     * keeps the place they took.
     */
    public List<PresidentEvent> forfeit(PlayerId who) {
        List<PresidentEvent> events = new ArrayList<>();
        int seat = indexOf(who);
        if (hands.get(seat).isEmpty()) {
            return events;
        }
        discard.addAll(hands.get(seat));
        hands.set(seat, new ArrayList<>());
        if (phase == Phase.EXCHANGE) {
            if (seat == current) {
                passLeadFrom(seat);
            }
        } else if (phase == Phase.PLAYING) {
            if (seatsWithCards() == 1) {
                endRound(events);
            } else if (seat == current) {
                if (trick.isEmpty()) {
                    passLeadFrom(seat);
                } else {
                    afterAction(events);
                }
            } else if (trick.isComplete(this::eligible, hands.size())) {
                completeTrick(events);
            }
        }
        return events;
    }

    // ----- queries -----

    public Phase phase() {
        return phase;
    }

    public PlayerId currentSeat() {
        return new PlayerId(current);
    }

    public PlayerId dealer() {
        return dealer;
    }

    public int seats() {
        return hands.size();
    }

    public List<Card> hand(PlayerId seat) {
        return List.copyOf(hands.get(indexOf(seat)));
    }

    public List<Integer> handSizes() {
        return hands.stream().map(List::size).toList();
    }

    public List<PlayerId> finishingOrder() {
        return finishing.stream().map(PlayerId::new).toList();
    }

    public TrickView trick() {
        return trick.view();
    }

    /** The cards of the tricks already won, face down, in the order they were played. */
    public List<Card> discard() {
        return List.copyOf(discard);
    }

    /** Every legal play of the seat whose turn it is; nothing for the other seats or once the round is over. */
    public List<Play> legalPlays(PlayerId seat) {
        if (phase != Phase.PLAYING || seat == null || seat.id() == null || seat.id() != current) {
            return List.of();
        }
        return LegalPlays.of(hands.get(current), trick.top(), order);
    }

    // ----- internals -----

    private void checkTurn(PlayerId who) throws PresidentException {
        if (phase != Phase.PLAYING) {
            throw new WrongPhaseException(phase, "PLAYING");
        }
        if (who == null || who.id() == null || who.id() != current) {
            throw new NotPlayersTurnException();
        }
    }

    private int indexOf(PlayerId seat) {
        if (seat == null || seat.id() == null || seat.id() < 0 || seat.id() >= hands.size()) {
            throw new IllegalArgumentException("Unknown seat " + seat);
        }
        return seat.id();
    }

    /** The hand without the named cards (one copy per name), or a refusal when it does not hold them all. */
    private List<Card> removeFromHand(int seat, List<Card> cards) throws CardsNotInHandException {
        List<Card> remaining = new ArrayList<>(hands.get(seat));
        for (Card card : cards) {
            if (card == null || !remaining.remove(card)) {
                throw new CardsNotInHandException();
            }
        }
        return remaining;
    }

    private int seatsWithCards() {
        return (int) hands.stream().filter(h -> !h.isEmpty()).count();
    }

    private boolean eligible(int seat) {
        return !hands.get(seat).isEmpty() && !trick.isOut(seat);
    }

    private void endRound(List<PresidentEvent> events) {
        for (int seat = 0; seat < hands.size(); seat++) {
            if (!hands.get(seat).isEmpty()) {
                finishing.add(seat);
            }
        }
        phase = Phase.ROUND_OVER;
        events.add(new RoundOver(finishingOrder()));
    }

    /** Ends the trick when everyone answered, else hands the turn to the next seat that can act. */
    private void afterAction(List<PresidentEvent> events) {
        if (trick.isComplete(this::eligible, hands.size())) {
            completeTrick(events);
            return;
        }
        for (int step = 1; step <= hands.size(); step++) {
            int seat = (current + step) % hands.size();
            if (eligible(seat)) {
                current = seat;
                return;
            }
        }
    }

    /** The trick goes to its last player, or to the next seat with cards when that player has gone out or left. */
    private void completeTrick(List<PresidentEvent> events) {
        int last = trick.lastPlayer();
        int winner = hands.get(last).isEmpty() ? nextSeatWithCards(last) : last;
        discard.addAll(trick.cards());
        trick = new Trick(winner);
        current = winner;
        events.add(new TrickWon(new PlayerId(winner), TrickEndReason.ALL_PASSED));
    }

    /** The seat that was to lead has left: the next seat with cards leads an empty trick. */
    private void passLeadFrom(int seat) {
        int next = nextSeatWithCards(seat);
        trick = new Trick(next);
        current = next;
    }

    private int nextSeatWithCards(int from) {
        for (int step = 1; step <= hands.size(); step++) {
            int seat = (from + step) % hands.size();
            if (!hands.get(seat).isEmpty()) {
                return seat;
            }
        }
        throw new IllegalStateException("No seat holds cards");
    }
}
