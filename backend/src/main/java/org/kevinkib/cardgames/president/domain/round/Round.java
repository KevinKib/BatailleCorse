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
import org.kevinkib.cardgames.president.domain.play.Completion;
import org.kevinkib.cardgames.president.domain.play.IllegalPlayException;
import org.kevinkib.cardgames.president.domain.play.LegalPlays;
import org.kevinkib.cardgames.president.domain.play.Play;
import org.kevinkib.cardgames.president.domain.play.PlayEffects;
import org.kevinkib.cardgames.president.domain.play.PlayParser;
import org.kevinkib.cardgames.president.domain.rules.AfterPass;
import org.kevinkib.cardgames.president.domain.rules.PresidentException;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cardgames.president.domain.trick.Chain;
import org.kevinkib.cardgames.president.domain.trick.MustLeadException;
import org.kevinkib.cardgames.president.domain.trick.MustPlayException;
import org.kevinkib.cardgames.president.domain.trick.Trick;
import org.kevinkib.cardgames.president.domain.trick.TrickView;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The rules of one round, seats being indexes from 0 in play order. Every command validates first and only
 * then mutates, so a refused command changes nothing. Not thread-safe: the {@code President} aggregate
 * serialises the calls.
 */
public final class Round {

    private final PresidentOptions options;
    private final RankOrder order = RankOrder.standard();
    private final List<List<Card>> hands = new ArrayList<>();
    /** The seats out with a normal finish, in the order they went out: places 1, 2, 3 ... */
    private final List<Integer> finishing = new ArrayList<>();
    /** The seats out with a forbidden finish, in the order they went out; they fill the bottom places. */
    private final List<Integer> forbidden = new ArrayList<>();
    private final List<Card> discard = new ArrayList<>();
    private final PlayerId dealer;
    private Trick trick;
    /** The rank of the last chain whose completion window closed (a play came, or the trick ended); null if none. */
    private FrenchRank closedWindowRank;
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

    /**
     * A play in turn, or the completion of the four out of turn (the only command accepted from a seat that is
     * not the one to act): every other out-of-turn play is refused with {@code NotPlayersTurnException}, a
     * completion that arrives too late or with the wrong cards with {@code CompletionClosedException}.
     */
    public List<PresidentEvent> play(PlayerId who, List<Card> cards) throws PresidentException {
        requirePlaying();
        if (who == null || who.id() == null || who.id() < 0 || who.id() >= hands.size()) {
            throw new NotPlayersTurnException();
        }
        if (cards == null || cards.isEmpty()) {
            throw new IllegalPlayException("A play holds at least one card");
        }
        int seat = who.id();
        boolean inTurn = seat == current;
        List<Card> remaining = removeFromHand(seat, cards);
        Play play;
        try {
            play = PlayParser.parse(cards);
        } catch (IllegalPlayException e) {
            if (inTurn) {
                throw e;
            }
            throw new NotPlayersTurnException();
        }
        boolean completion = isCompletion(play);
        if (!inTurn && !completion) {
            throw refusalOutOfTurn(play);
        }
        if (!completion && trick.top() != null) {
            BeatRule.check(play, trick.top(), order, trick.constraint(), options.reducedCut());
        }

        Play top = trick.top();
        boolean equal = top != null && BeatRule.isEqual(play, top);
        Chain before = trick.chain();
        hands.set(seat, remaining);
        trick.play(seat, play, equal);
        noteWindow(before);
        List<PresidentEvent> events = new ArrayList<>();
        events.add(new Played(who, play.cards(), !inTurn));
        if (remaining.isEmpty()) {
            boolean forbiddenFinish = isForbiddenFinish(play);
            if (forbiddenFinish) {
                // placed last when the round ends (spec 3.1, O3)
                forbidden.add(seat);
                events.add(new PlayerOut(who, 0, true));
            } else {
                finishing.add(seat);
                events.add(new PlayerOut(who, finishing.size()));
            }
            if (seatsWithCards() == 1) {
                endRound(events);
                return events;
            }
            // a forbidden finisher takes no place, so never the 1st one: their 2s close the trick as any 2s do
            // (CUT, or QUAD), and the next seat that still holds cards leads
            if (!forbiddenFinish && finishing.size() == 1) {
                // the owner's rule: the player who takes 1st place closes the trick, the next seat with cards leads
                completeTrick(events, TrickEndReason.FIRST_OUT);
                return events;
            }
        }
        Chain chain = trick.chain();
        boolean quadInSuccession = chain.plays() >= 2 && chain.cards().size() == Completion.RANK_SIZE;
        Optional<TrickEndReason> cleared = quadInSuccession ? Optional.of(TrickEndReason.QUAD)
                : PlayEffects.clearReason(play, top, order, options.reducedCut());
        if (cleared.isPresent()) {
            // the four in succession, a quad played at once or a lone 2 close the trick; whoever played
            // the last cards leads (the next seat with cards when they went out)
            completeTrick(events, cleared.get());
            return events;
        }
        afterAction(events);
        return events;
    }

    public List<PresidentEvent> pass(PlayerId who) throws PresidentException {
        checkTurn(who);
        if (trick.isEmpty()) {
            throw new MustLeadException();
        }
        if (options.mustPlay() && !legalPlays(who).isEmpty()) {
            // compulsory play counts the plays of legalPlays (cuts and equal plays included); a completion is not one
            throw new MustPlayException();
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

    /** Every card has changed hands: the table is shown the exchange before the first trick. */
    public void showExchange() {
        phase = Phase.EXCHANGE_VIEW;
    }

    /** The exchange is done and seen (or there was none): the first trick can start. */
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
        if (phase == Phase.EXCHANGE || phase == Phase.EXCHANGE_VIEW) {
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
        List<PlayerId> order = new ArrayList<>(finishing.stream().map(PlayerId::new).toList());
        if (phase == Phase.ROUND_OVER) {
            forbidden.forEach(seat -> order.add(new PlayerId(seat)));
        }
        return order;
    }

    /** The seats that went out on a forbidden finish, the earliest first; they take the last places. */
    public List<PlayerId> forbiddenFinishers() {
        return forbidden.stream().map(PlayerId::new).toList();
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
        return LegalPlays.of(hands.get(current), trick.top(), order, trick.constraint(), options.reducedCut());
    }

    /**
     * The cards that complete the four when the seat holds all of them and the window is open (the chain at the
     * top of the trick holds two plays or more, or is a lone card or a lone pair), in turn or out of turn; nothing
     * otherwise.
     */
    public List<Card> completion(PlayerId seat) {
        if (phase != Phase.PLAYING || seat == null || seat.id() == null
                || seat.id() < 0 || seat.id() >= hands.size()) {
            return List.of();
        }
        Chain chain = trick.chain();
        if (chain == null || !windowOpen(chain)) {
            return List.of();
        }
        return HandSort.sorted(Completion.heldBy(hands.get(seat.id()), chain.rank(), chain.cards()), order);
    }

    // ----- internals -----

    /** Going out on a top-rank card is forbidden (a fixed rule): the player is placed last. */
    private boolean isForbiddenFinish(Play play) {
        return play.cards().stream()
                .anyMatch(card -> card.getRank() == order.topRank());
    }

    private void requirePlaying() throws WrongPhaseException {
        if (phase != Phase.PLAYING) {
            throw new WrongPhaseException(phase, "PLAYING");
        }
    }

    private void checkTurn(PlayerId who) throws PresidentException {
        requirePlaying();
        if (who == null || who.id() == null || who.id() != current) {
            throw new NotPlayersTurnException();
        }
    }

    /** Whether the cards are exactly the ones that complete the four, with the window open. */
    private boolean isCompletion(Play play) {
        Chain chain = trick.chain();
        return chain != null && windowOpen(chain)
                && play.rank() == chain.rank()
                && Completion.isCompletion(play.cards(), chain.rank(), chain.cards());
    }

    /**
     * The completion window is open: the chain holds two plays or more, or a single play of one card or a pair that
     * as many cards or more of its rank can complete (the cumulative count reaches four with a play of exactly the
     * cards missing). It never works the other way round (a lone card on a triple), and a 2 is never left on top of
     * a trick.
     */
    private boolean windowOpen(Chain chain) {
        return chain.plays() >= 2 || (chain.cards().size() <= 2 && chain.rank() != order.topRank());
    }

    /**
     * Why an out-of-turn play that is not a completion is refused: a set of the rank whose window is open (the
     * wrong cards) or was open and closed (too late) is a failed completion; anything else is just not the seat's
     * turn.
     */
    private PresidentException refusalOutOfTurn(Play play) {
        Chain chain = trick.chain();
        FrenchRank windowRank = chain != null && windowOpen(chain) ? chain.rank() : closedWindowRank;
        return play.rank() == windowRank || play.rank() == closedWindowRank ? new CompletionClosedException() : new NotPlayersTurnException();
    }

    /** Remembers the window a play closed, and forgets the old one when a new window opens. */
    private void noteWindow(Chain before) {
        Chain after = trick.chain();
        if (before != null && windowOpen(before) && after.rank() != before.rank()) {
            closedWindowRank = before.rank();
        }
        if (after.plays() == 2) {
            closedWindowRank = null;
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
        completeTrick(events, TrickEndReason.ALL_PASSED);
    }

    private void completeTrick(List<PresidentEvent> events, TrickEndReason reason) {
        int last = trick.lastPlayer();
        int winner = hands.get(last).isEmpty() ? nextSeatWithCards(last) : last;
        Chain chain = trick.chain();
        if (chain != null && windowOpen(chain)) {
            closedWindowRank = chain.rank();
        }
        discard.addAll(trick.cards());
        trick = new Trick(winner);
        current = winner;
        events.add(new TrickWon(new PlayerId(winner), reason));
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
